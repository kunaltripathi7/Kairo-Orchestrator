# Kairo Orchestrator 🚀

![Architecture](https://img.shields.io/badge/Architecture-Microservices-blue)
![Language](https://img.shields.io/badge/Language-Java%20%7C%20Go-orange)
![Pattern](https://img.shields.io/badge/Pattern-CDC%20Outbox-green)

Kairo is an enterprise-grade, distributed Directed Acyclic Graph (DAG) workflow orchestrator. It allows you to define complex, multi-step workflows as JSON and executes them reliably across distributed worker nodes. 

Inspired by systems like Temporal and Netflix Conductor, Kairo solves the hardest problems in distributed systems: **Guaranteed At-Least-Once execution, Idempotency, and Zero-Data-Loss Event Streaming.**

## 🌟 Key Features & Architectural Patterns

### 1. Transactional Outbox Pattern via Debezium CDC
Traditional orchestrators often suffer from the "Dual-Write Problem" (saving state to the database and publishing to Kafka separately, risking inconsistency if one fails). 
- Kairo solves this by saving workflow state and an Outbox Event to PostgreSQL in a **single ACID transaction**.
- A **Debezium Kafka Connect** cluster continuously tails the PostgreSQL Write-Ahead Log (WAL) (`wal_level=logical`) and streams these events into Kafka in real-time with sub-millisecond latency. 

### 2. Polyglot Microservices
- **API Service (Java/Spring Boot):** Handles REST requests, validates DAG payloads, and initiates workflows.
- **Scheduler Service (Java/Spring Boot):** Consumes task results, uses a Breadth-First Search (BFS) algorithm to unlock downstream dependent tasks, and handles DAG state transitions.
- **Worker Fleet (Go):** High-concurrency worker nodes that consume Kafka events, execute the business logic via a Handler Registry pattern, and publish results.

### 3. Distributed Locking & Idempotency (Redis)
Kafka guarantees at-least-once delivery, which can result in duplicate task executions during network partitions or worker crashes. 
- Kairo's Go workers implement a **Redis Distributed Lock (SETNX)**. 
- When a worker pulls a task, it atomically acquires a lock with a TTL. If a worker crashes mid-execution, the lock expires. If it succeeds, the result is cached, ensuring a task is *never* processed twice, achieving true exactly-once semantics.

### 4. Concurrency Safety (Pessimistic Locking)
When parallel tasks complete at the exact same millisecond, race conditions can corrupt the DAG state. Kairo's Java Scheduler utilizes PostgreSQL **Pessimistic Write Locks** (`SELECT ... FOR UPDATE`) to serialize state transitions, guaranteeing data integrity.

### 5. Full Observability (OpenTelemetry)
- **Tracing:** Spring Boot Micrometer + Zipkin exporter propagates trace contexts across asynchronous boundaries (e.g., `CompletableFuture` thread pools) to **Grafana Tempo**.
- **Metrics:** **Prometheus** scrapes application and infrastructure metrics.
- **Dashboards:** **Grafana** visualizes the health of the entire orchestrator.

## 🏗️ Architecture Diagram
*(Imagine a diagram here showing API -> Postgres -> Debezium -> Kafka -> Go Worker -> Kafka -> Scheduler -> Postgres)*

## 🚀 Getting Started

### Prerequisites
- Docker & Docker Compose
- Java 21
- Go 1.24

### Spin up the Infrastructure
Bring up PostgreSQL, Kafka, Kafka Connect, Redis, and the Observability Stack (Prometheus, Grafana, Tempo):
```bash
docker-compose up -d
```

### Register the Debezium Connector
Once Kafka Connect is running, register the Outbox Event Router:
```bash
./scripts/register-debezium.sh
```

### Run the Services
**1. Start the API Service:**
```bash
./gradlew :api-service:bootRun
```
**2. Start the Scheduler Service:**
```bash
./gradlew :scheduler-service:bootRun
```
**3. Start the Go Worker:**
```bash
cd worker-service && go run main.go
```

## 💡 Example Workflow Definition
Submit a DAG to `POST /api/v1/workflows`:
```json
{
  "name": "E-Commerce Checkout",
  "tasks": [
    { "name": "ValidateOrder", "handler": "validate-order", "dependsOn": [] },
    { "name": "ChargePayment", "handler": "charge-payment", "dependsOn": ["ValidateOrder"] },
    { "name": "SendReceipt", "handler": "send-notification", "dependsOn": ["ChargePayment"] }
  ]
}
```
