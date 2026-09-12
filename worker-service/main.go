package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/kunal/kairo-worker/handler"
	"github.com/segmentio/kafka-go"
)

const (
	brokerAddress   = "localhost:9092"
	taskQueueTopic  = "task-queue"
	taskResultTopic = "task-results"
	consumerGroup   = "worker-group"
	dedupTTL        = 5 * time.Minute
)

type dedupEntry struct {
	result    handler.TaskResult
	timestamp time.Time
}

var processedTasks sync.Map // concurrent map in go

func main() {
	fmt.Println("Starting Kairo Worker Service (Go)...")

	registry := handler.NewRegistry()

	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:  []string{brokerAddress},
		Topic:    taskQueueTopic,
		GroupID:  consumerGroup,
		MinBytes: 1,
		MaxBytes: 10e6,
	})

	writer := &kafka.Writer{
		Addr:     kafka.TCP(brokerAddress),
		Topic:    taskResultTopic,
		Balancer: &kafka.LeastBytes{},
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Graceful shutdown on SIGINT/SIGTERM
	signals := make(chan os.Signal, 1)
	signal.Notify(signals, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		<-signals
		fmt.Println("\nShutting down worker...")
		cancel()
	}()

	// Periodically clean up old dedup entries
	go cleanupDedupEntries(ctx)

	fmt.Printf("Listening on topic: %s (group: %s)\n", taskQueueTopic, consumerGroup)

	for {
		msg, err := reader.ReadMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				break // context cancelled, shutting down
			}
			log.Printf("Error reading message: %v\n", err)
			continue
		}

		var task handler.TaskPayload
		if err := json.Unmarshal(msg.Value, &task); err != nil {
			log.Printf("Failed to unmarshal task: %v\n", err)
			continue
		}

		log.Printf("Received task: id=%s, handler=%s\n", task.TaskID, task.HandlerName)

		var result handler.TaskResult

		if entry, exists := processedTasks.Load(task.TaskID); exists {
			log.Printf("Task %s already processed, skipping duplicate execution\n", task.TaskID)
			result = entry.(dedupEntry).result
		} else {
			result = processTask(registry, task)
			processedTasks.Store(task.TaskID, dedupEntry{
				result:    result,
				timestamp: time.Now(),
			})
		}

		resultBytes, err := json.Marshal(result)
		if err != nil {
			log.Printf("Failed to marshal result for task %s: %v\n", task.TaskID, err)
			continue
		}
		err = writer.WriteMessages(ctx, kafka.Message{
			Key:   []byte(task.WorkflowID),
			Value: resultBytes,
		})
		if err != nil {
			log.Printf("Failed to publish result for task %s: %v\n", task.TaskID, err)
		} else {
			log.Printf("Published result for task %s: status=%s\n", task.TaskID, result.Status)
		}
	}

	reader.Close()
	writer.Close()
	fmt.Println("Worker stopped.")
}

func cleanupDedupEntries(ctx context.Context) {
	ticker := time.NewTicker(1 * time.Minute)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			now := time.Now()
			processedTasks.Range(func(key, value any) bool {
				entry := value.(dedupEntry)
				if now.Sub(entry.timestamp) > dedupTTL {
					processedTasks.Delete(key)
				}
				return true
			})
		}
	}
}

func processTask(registry *handler.Registry, task handler.TaskPayload) handler.TaskResult {
	h, err := registry.Get(task.HandlerName)
	if err != nil {
		return handler.TaskResult{
			TaskID:  task.TaskID,
			Status:  "FAILED",
			Message: fmt.Sprintf("Handler not found: %s", task.HandlerName),
		}
	}

	output, err := h.Execute(task.Payload)
	if err != nil {
		return handler.TaskResult{
			TaskID:  task.TaskID,
			Status:  "FAILED",
			Message: err.Error(),
		}
	}

	return handler.TaskResult{
		TaskID:  task.TaskID,
		Status:  "COMPLETED",
		Message: output,
	}
}
