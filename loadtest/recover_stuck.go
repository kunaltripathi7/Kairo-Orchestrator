package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"time"

	"github.com/jackc/pgx/v4"
	"github.com/segmentio/kafka-go"
)

type TaskPayload struct {
	TaskID        string `json:"taskId"`
	WorkflowID    string `json:"workflowId"`
	HandlerName   string `json:"handlerName"`
	Payload       string `json:"payload"`
	CorrelationID string `json:"correlationId"`
}

func main() {
	conn, err := pgx.Connect(context.Background(), "postgres://kairo:kairo@localhost:5433/kairo")
	if err != nil {
		log.Fatal(err)
	}
	defer conn.Close(context.Background())

	writer := &kafka.Writer{
		Addr:     kafka.TCP("localhost:9094"),
		Topic:    "task-queue",
		Balancer: &kafka.LeastBytes{},
	}
	defer writer.Close()

	for {
		rows, err := conn.Query(context.Background(), "SELECT id, workflow_id, handler_name, payload FROM tasks WHERE status = 'SCHEDULED' AND updated_at < NOW() - INTERVAL '15 seconds' AND locked_until IS NULL")
		if err != nil {
			log.Fatal(err)
		}

		var count int
		for rows.Next() {
			var taskId, workflowId, handlerName, payload string
			if err := rows.Scan(&taskId, &workflowId, &handlerName, &payload); err != nil {
				log.Println(err)
				continue
			}

			msg := TaskPayload{
				TaskID:      taskId,
				WorkflowID:  workflowId,
				HandlerName: handlerName,
				Payload:     payload,
			}
			msgBytes, _ := json.Marshal(msg)

			err = writer.WriteMessages(context.Background(), kafka.Message{
				Key:   []byte(workflowId),
				Value: msgBytes,
			})
			if err != nil {
				log.Println("Failed to publish:", err)
			} else {
				log.Printf("Recovered task %s", taskId)
				
				// Update DB so we don't recover it again immediately
				_, _ = conn.Exec(context.Background(), "UPDATE tasks SET updated_at = NOW() WHERE id = $1", taskId)
			}
			count++
		}
		rows.Close()

		if count > 0 {
			log.Printf("Recovered %d tasks", count)
		}
		time.Sleep(5 * time.Second)
	}
}
