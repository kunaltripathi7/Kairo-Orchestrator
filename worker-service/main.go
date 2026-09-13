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
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil)) // setting it to output json
	slog.SetDefault(logger)

	slog.Info("Starting Kairo Worker Service (Go)...")

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

	slog.Info(fmt.Sprintf("Listening on topic: %s (group: %s)", taskQueueTopic, consumerGroup))

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

		logger := slog.With("correlationId", task.CorrelationID, "taskId", task.TaskID, "workflowId", task.WorkflowID)
		logger.Info("Received task", "handler", task.HandlerName)

		var result handler.TaskResult

		if entry, exists := processedTasks.Load(task.TaskID); exists {
			logger.Warn("Task already processed, skipping duplicate execution")
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
			logger.Error("Failed to marshal result", "error", err)
			continue
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

	reader.Close()
	writer.Close()
	slog.Info("Worker stopped.")
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
