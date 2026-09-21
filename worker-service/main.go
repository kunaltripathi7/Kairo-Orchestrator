package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/kunal/kairo-worker/handler"
	"github.com/redis/go-redis/v9"
	"github.com/segmentio/kafka-go"
)

const (
	brokerAddress   = "localhost:9094"
	taskQueueTopic  = "task-queue"
	taskResultTopic = "task-results"
	consumerGroup   = "worker-group"
	workerPoolSize  = 50
	taskChannelSize = 100
)

type IdempotencyRecord struct {
	Status string             `json:"status"` // "RUNNING" or "COMPLETED"
	Result handler.TaskResult `json:"result"`
}

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	slog.SetDefault(logger)

	slog.Info("Starting Kairo Worker Service (Go)...", "poolSize", workerPoolSize)

	// Initialize Redis Client for Distributed Locking
	rdb := redis.NewClient(&redis.Options{
		Addr: "localhost:6380",
	})
	if err := rdb.Ping(context.Background()).Err(); err != nil {
		slog.Error("Failed to connect to Redis", "error", err)
		os.Exit(1)
	}
	slog.Info("Connected to Redis successfully.")

	registry := handler.NewRegistry()

	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:     []string{brokerAddress},
		Topic:       taskQueueTopic,
		GroupID:     consumerGroup,
		MinBytes:    1,
		MaxBytes:    10e6,
		StartOffset: kafka.FirstOffset,
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

	// Buffered channel for task dispatch
	taskCh := make(chan handler.TaskPayload, taskChannelSize)

	// Launch worker pool goroutines
	var wg sync.WaitGroup
	for i := 0; i < workerPoolSize; i++ {
		wg.Add(1)
		go func(workerID int) {
			defer wg.Done()
			for task := range taskCh {
				executeTask(ctx, registry, rdb, writer, task, workerID)
			}
		}(i)
	}

	slog.Info(fmt.Sprintf("Listening on topic: %s (group: %s) with %d workers", taskQueueTopic, consumerGroup, workerPoolSize))

	// Consumer loop: read from Kafka and dispatch to worker pool
	for {
		msg, err := reader.ReadMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				break // context cancelled, shutting down
			}
			slog.Error("Error reading message", "error", err)
			continue
		}

		var task handler.TaskPayload
		if err := json.Unmarshal(msg.Value, &task); err != nil {
			slog.Error("Failed to unmarshal task", "error", err)
			continue
		}

		// Dispatch to worker pool (blocks if channel is full, providing backpressure)
		select {
		case taskCh <- task:
		case <-ctx.Done():
			break
		}
	}

	// Shutdown: close channel and wait for in-flight tasks
	close(taskCh)
	wg.Wait()
	reader.Close()
	writer.Close()
	slog.Info("Worker stopped.")
}

// executeTask handles a single task within a goroutine worker.
// It acquires a Redis distributed lock, executes the handler, and publishes the result.
func executeTask(ctx context.Context, registry *handler.Registry, rdb *redis.Client, writer *kafka.Writer, task handler.TaskPayload, workerID int) {
	logger := slog.With("correlationId", task.CorrelationID, "taskId", task.TaskID, "workflowId", task.WorkflowID, "worker", workerID)
	logger.Info("Received task", "handler", task.HandlerName)

	var result handler.TaskResult

	idempotencyKey := fmt.Sprintf("task:%s:idempotency", task.TaskID)
	runningRecord, _ := json.Marshal(IdempotencyRecord{Status: "RUNNING"})

	// Attempt to acquire distributed lock
	acquired, err := rdb.SetNX(ctx, idempotencyKey, runningRecord, 5*time.Minute).Result()
	if err != nil {
		logger.Error("Failed to communicate with Redis", "error", err)
		return
	}

	if !acquired {
		// Key already exists, check status
		val, err := rdb.Get(ctx, idempotencyKey).Result()
		if err != nil {
			logger.Error("Failed to fetch idempotency record", "error", err)
			return
		}

		var record IdempotencyRecord
		json.Unmarshal([]byte(val), &record)

		if record.Status == "COMPLETED" {
			logger.Info("Task already COMPLETED (cache hit), publishing cached result.")
			result = record.Result
		} else {
			logger.Warn("Task is currently RUNNING on another worker (or crashed recently). Skipping.")
			return
		}
	} else {
		// We acquired the lock, execute the task
		result = processTask(registry, task)

		// Cache the result in Redis with 24 hour TTL
		completedRecord, _ := json.Marshal(IdempotencyRecord{
			Status: "COMPLETED",
			Result: result,
		})
		rdb.Set(ctx, idempotencyKey, completedRecord, 24*time.Hour)
	}

	resultBytes, err := json.Marshal(result)
	if err != nil {
		logger.Error("Failed to marshal result", "error", err)
		return
	}
	err = writer.WriteMessages(ctx, kafka.Message{
		Key:   []byte(task.WorkflowID),
		Value: resultBytes,
	})
	if err != nil {
		logger.Error("Failed to publish result", "error", err)
	} else {
		logger.Info("Published result", "status", result.Status)
	}
}

func processTask(registry *handler.Registry, task handler.TaskPayload) handler.TaskResult {
	h, err := registry.Get(task.HandlerName)
	if err != nil {
		return handler.TaskResult{
			TaskID:        task.TaskID,
			Status:        "FAILED",
			Message:       fmt.Sprintf("Handler not found: %s", task.HandlerName),
			CorrelationID: task.CorrelationID,
		}
	}

	output, err := h.Execute(task.Payload)
	if err != nil {
		return handler.TaskResult{
			TaskID:        task.TaskID,
			Status:        "FAILED",
			Message:       err.Error(),
			CorrelationID: task.CorrelationID,
		}
	}

	return handler.TaskResult{
		TaskID:        task.TaskID,
		Status:        "COMPLETED",
		Message:       output,
		CorrelationID: task.CorrelationID,
	}
}
