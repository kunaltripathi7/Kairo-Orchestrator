package main

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"math"
	"net/http"
	"os"
	"sort"
	"sync"
	"time"
)

// ANSI color codes
const (
	colorReset  = "\033[0m"
	colorRed    = "\033[31m"
	colorGreen  = "\033[32m"
	colorYellow = "\033[33m"
	colorCyan   = "\033[36m"
)

type Task struct {
	Name      string                 `json:"name"`
	Handler   string                 `json:"handler"`
	DependsOn []string               `json:"dependsOn"`
	Payload   map[string]interface{} `json:"payload"`
}

type WorkflowRequest struct {
	Name               string `json:"name"`
	MaxRetries         int    `json:"maxRetries"`
	TaskTimeoutSeconds int    `json:"taskTimeoutSeconds"`
	Tasks              []Task `json:"tasks"`
}

type WorkflowResponse struct {
	ID     string `json:"id"`
	Status string `json:"status"`
}

type SubmissionResult struct {
	Index       int
	ID          string
	Latency     time.Duration
	SubmitStart time.Time
	Err         error
}

type PollResult struct {
	ID          string
	Status      string
	EndToEnd    time.Duration
	Err         error
}

type Metrics struct {
	TotalSubmitted int
	TotalSuccess   int
	TotalFailed    int
	TotalTimedOut  int

	SubmissionLatencies []time.Duration
	EndToEndLatencies   []time.Duration

	SubmissionP50 time.Duration
	SubmissionP95 time.Duration
	SubmissionP99 time.Duration
	SubmissionMin time.Duration
	SubmissionMax time.Duration
	SubmissionAvg time.Duration

	EndToEndP50 time.Duration
	EndToEndP95 time.Duration
	EndToEndP99 time.Duration
	EndToEndMin time.Duration
	EndToEndMax time.Duration
	EndToEndAvg time.Duration

	Throughput  float64
	Concurrency int
	Durability  float64
}

func main() {
	var (
		n            = flag.Int("n", 100, "Total workflows to submit")
		c            = flag.Int("c", 10, "Concurrency")
		baseURL      = flag.String("url", "http://localhost:8080", "Base URL")
		timeoutSec   = flag.Int("timeout", 120, "Timeout in seconds")
		pollIntMs    = flag.Int("poll-interval", 500, "Poll interval in ms")
		rampSec      = flag.Int("ramp", 0, "Ramp up duration in seconds")
		dagType      = flag.String("dag", "linear", "DAG type: linear, fanout, diamond")
	)
	flag.Parse()

	fmt.Printf("%s--- Kairo Orchestrator Load Test ---%s\n", colorCyan, colorReset)
	fmt.Printf("Workflows: %d | Concurrency: %d | DAG: %s | URL: %s\n", *n, *c, *dagType, *baseURL)

	client := &http.Client{
		Timeout: 10 * time.Second,
		Transport: &http.Transport{
			MaxIdleConns:        *c * 2,
			MaxIdleConnsPerHost: *c * 2,
		},
	}

	submissionResults := make(chan SubmissionResult, *n)
	var wg sync.WaitGroup

	jobs := make(chan int, *n)
	for i := 0; i < *c; i++ {
		wg.Add(1)
		go func(workerID int) {
			defer wg.Done()
			for idx := range jobs {
				submitWorkflow(client, *baseURL, *dagType, idx, submissionResults)
			}
		}(i)
	}

	fmt.Println("Phase 1: Blasting workflows...")
	startTime := time.Now()

	// Ramp up logic
	var delay time.Duration
	if *rampSec > 0 {
		delay = time.Duration(float64(*rampSec*int(time.Second)) / float64(*n))
	}

	for i := 0; i < *n; i++ {
		jobs <- i
		if delay > 0 {
			time.Sleep(delay)
		}
	}
	close(jobs)

	wg.Wait()
	close(submissionResults)
	blastDuration := time.Since(startTime)
	throughput := float64(*n) / blastDuration.Seconds()

	var successfulSubs []SubmissionResult
	var latencies []time.Duration
	subErrors := 0

	for res := range submissionResults {
		if res.Err != nil {
			subErrors++
		} else {
			successfulSubs = append(successfulSubs, res)
			latencies = append(latencies, res.Latency)
		}
	}

	fmt.Printf("Phase 1 Complete. Submitted: %d, Success: %d, Errors: %d. Time: %v\n", *n, len(successfulSubs), subErrors, blastDuration)

	fmt.Println("Phase 2: Polling for completion...")
	pollResults := make(chan PollResult, len(successfulSubs))
	var pollWg sync.WaitGroup

	pollJobs := make(chan SubmissionResult, len(successfulSubs))
	for i := 0; i < *c; i++ {
		pollWg.Add(1)
		go func() {
			defer pollWg.Done()
			for sub := range pollJobs {
				pollWorkflow(client, *baseURL, sub, time.Duration(*timeoutSec)*time.Second, time.Duration(*pollIntMs)*time.Millisecond, pollResults)
			}
		}()
	}

	for _, sub := range successfulSubs {
		pollJobs <- sub
	}
	close(pollJobs)

	pollWg.Wait()
	close(pollResults)

	fmt.Println("Phase 2 Complete. Generating report...")

	metrics := Metrics{
		TotalSubmitted:      *n,
		Concurrency:         *c,
		Throughput:          throughput,
		SubmissionLatencies: latencies,
	}

	for res := range pollResults {
		if res.Err != nil {
			metrics.TotalTimedOut++
		} else if res.Status == "FAILED" {
			metrics.TotalFailed++
			metrics.EndToEndLatencies = append(metrics.EndToEndLatencies, res.EndToEnd)
		} else if res.Status == "COMPLETED" {
			metrics.TotalSuccess++
			metrics.EndToEndLatencies = append(metrics.EndToEndLatencies, res.EndToEnd)
		}
	}

	metrics.Durability = (float64(metrics.TotalSuccess) / float64(*n)) * 100.0

	calculatePercentiles(&metrics.SubmissionLatencies, &metrics.SubmissionP50, &metrics.SubmissionP95, &metrics.SubmissionP99, &metrics.SubmissionMin, &metrics.SubmissionMax, &metrics.SubmissionAvg)
	calculatePercentiles(&metrics.EndToEndLatencies, &metrics.EndToEndP50, &metrics.EndToEndP95, &metrics.EndToEndP99, &metrics.EndToEndMin, &metrics.EndToEndMax, &metrics.EndToEndAvg)

	printReport(metrics)

	if metrics.Durability < 99.0 {
		fmt.Printf("\n%sFAILED: Durability (%.2f%%) is below 99.0%%%s\n", colorRed, metrics.Durability, colorReset)
		os.Exit(1)
	}
}

func submitWorkflow(client *http.Client, baseURL, dagType string, index int, results chan<- SubmissionResult) {
	reqData := buildWorkflow(dagType, index)
	body, _ := json.Marshal(reqData)

	start := time.Now()
	req, _ := http.NewRequest(http.MethodPost, baseURL+"/api/v1/workflows", bytes.NewBuffer(body))
	req.Header.Set("Content-Type", "application/json")

	resp, err := client.Do(req)
	latency := time.Since(start)

	res := SubmissionResult{
		Index:       index,
		Latency:     latency,
		SubmitStart: start,
	}

	if err != nil {
		res.Err = err
		results <- res
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		res.Err = fmt.Errorf("status code: %d", resp.StatusCode)
		results <- res
		return
	}

	var wResp WorkflowResponse
	if err := json.NewDecoder(resp.Body).Decode(&wResp); err != nil {
		res.Err = err
	} else {
		res.ID = wResp.ID
	}
	results <- res
}

func pollWorkflow(client *http.Client, baseURL string, sub SubmissionResult, timeout, interval time.Duration, results chan<- PollResult) {
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		req, _ := http.NewRequest(http.MethodGet, fmt.Sprintf("%s/api/v1/workflows/%s", baseURL, sub.ID), nil)
		resp, err := client.Do(req)
		
		if err == nil {
			if resp.StatusCode == 200 {
				var wResp WorkflowResponse
				if err := json.NewDecoder(resp.Body).Decode(&wResp); err == nil {
					resp.Body.Close()
					if wResp.Status == "COMPLETED" || wResp.Status == "FAILED" {
						results <- PollResult{
							ID:       sub.ID,
							Status:   wResp.Status,
							EndToEnd: time.Since(sub.SubmitStart),
						}
						return
					}
				}
			}
			resp.Body.Close()
		}
		time.Sleep(interval)
	}
	results <- PollResult{
		ID:  sub.ID,
		Err: fmt.Errorf("timeout"),
	}
}

func buildWorkflow(dagType string, index int) WorkflowRequest {
	req := WorkflowRequest{
		Name:               fmt.Sprintf("LoadTest-%s-%d", dagType, index),
		MaxRetries:         3,
		TaskTimeoutSeconds: 30,
	}

	switch dagType {
	case "fanout":
		req.Tasks = []Task{
			{Name: "A", Handler: "validate-order", DependsOn: []string{}, Payload: map[string]interface{}{"id": index}},
			{Name: "B", Handler: "charge-payment", DependsOn: []string{"A"}, Payload: map[string]interface{}{"amt": 10}},
			{Name: "C", Handler: "charge-payment", DependsOn: []string{"A"}, Payload: map[string]interface{}{"amt": 20}},
			{Name: "D", Handler: "charge-payment", DependsOn: []string{"A"}, Payload: map[string]interface{}{"amt": 30}},
			{Name: "E", Handler: "send-notification", DependsOn: []string{"B", "C", "D"}, Payload: map[string]interface{}{"done": true}},
		}
	case "diamond":
		req.Tasks = []Task{
			{Name: "A", Handler: "validate-order", DependsOn: []string{}, Payload: map[string]interface{}{"id": index}},
			{Name: "B", Handler: "charge-payment", DependsOn: []string{"A"}, Payload: map[string]interface{}{"amt": 10}},
			{Name: "C", Handler: "charge-payment", DependsOn: []string{"A"}, Payload: map[string]interface{}{"amt": 20}},
			{Name: "D", Handler: "send-notification", DependsOn: []string{"B", "C"}, Payload: map[string]interface{}{"done": true}},
		}
	default:
		// linear
		req.Tasks = []Task{
			{Name: "A", Handler: "validate-order", DependsOn: []string{}, Payload: map[string]interface{}{"id": index}},
			{Name: "B", Handler: "charge-payment", DependsOn: []string{"A"}, Payload: map[string]interface{}{"amt": 10}},
			{Name: "C", Handler: "send-notification", DependsOn: []string{"B"}, Payload: map[string]interface{}{"done": true}},
		}
	}
	return req
}

func calculatePercentiles(latencies *[]time.Duration, p50, p95, p99, min, max, avg *time.Duration) {
	if len(*latencies) == 0 {
		return
	}
	sort.Slice(*latencies, func(i, j int) bool {
		return (*latencies)[i] < (*latencies)[j]
	})

	l := *latencies
	n := len(l)

	*min = l[0]
	*max = l[n-1]

	*p50 = l[int(math.Max(0, float64(n)*0.50-1))]
	*p95 = l[int(math.Max(0, float64(n)*0.95-1))]
	*p99 = l[int(math.Max(0, float64(n)*0.99-1))]

	var sum time.Duration
	for _, val := range l {
		sum += val
	}
	*avg = time.Duration(int64(sum) / int64(n))
}

func printReport(m Metrics) {
	fmt.Printf("\n%s========================================================%s\n", colorCyan, colorReset)
	fmt.Printf("%s                  LOAD TEST RESULTS                     %s\n", colorCyan, colorReset)
	fmt.Printf("%s========================================================%s\n", colorCyan, colorReset)
	
	fmt.Printf("Total Workflows:    %d\n", m.TotalSubmitted)
	fmt.Printf("Concurrency Peak:   %d\n", m.Concurrency)
	fmt.Printf("Throughput:         %.2f req/sec\n", m.Throughput)
	
	color := colorGreen
	if m.Durability < 99.0 {
		color = colorRed
	} else if m.Durability < 99.99 {
		color = colorYellow
	}
	fmt.Printf("Durability:         %s%.2f%%%s (Completed: %d, Failed: %d, Timeout: %d)\n", 
		color, m.Durability, colorReset, m.TotalSuccess, m.TotalFailed, m.TotalTimedOut)
	
	fmt.Println("\n--- Submission Latency ---")
	fmt.Printf("Min: %v | Max: %v | Avg: %v\n", m.SubmissionMin, m.SubmissionMax, m.SubmissionAvg)
	fmt.Printf("p50: %v | p95: %v | p99: %v\n", m.SubmissionP50, m.SubmissionP95, m.SubmissionP99)
	
	fmt.Println("\n--- End-To-End Latency ---")
	fmt.Printf("Min: %v | Max: %v | Avg: %v\n", m.EndToEndMin, m.EndToEndMax, m.EndToEndAvg)
	fmt.Printf("p50: %v | p95: %v | p99: %v\n", m.EndToEndP50, m.EndToEndP95, m.EndToEndP99)
	
	fmt.Printf("%s========================================================%s\n", colorCyan, colorReset)
}
