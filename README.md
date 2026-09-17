# Kairo Orchestrator

Most orchestrators fail at scale because they rely on database polling or suffer from dual-write inconsistencies. Kairo is a lightweight, distributed DAG execution engine built to specifically solve these failure modes. 

It achieves **Exactly-Once Execution** and **Zero-Data-Loss** by completely offloading state propagation to the infrastructure layer using the Transactional Outbox pattern, Change Data Capture (CDC), and Distributed Locking.

## The Architecture

```mermaid
flowchart TD
    Client([API Request]) --> API[API Service (Java)]
    API -- "1. ACID Tx (DAG + Event)" --> DB[(PostgreSQL)]
    DB -. "2. WAL Tailing" .-> CDC[Debezium Kafka Connect]
    CDC -- "3. Stream Event" --> Topic1([workflow-events])
    
    Topic1 -- "4. Consume" --> Worker[Go Worker Fleet]
    Worker -- "5. Idempotency Lock" --> Redis[(Redis)]
    Worker -- "6. Publish Result" --> Topic2([task-results])
    
    Topic2 -- "7. Consume" --> Sched[Scheduler Service (Java)]
    Sched -- "8. Pessimistic Lock & Update" --> DB
```

## How It Solves the Hard Problems

### 1. The Dual-Write Problem
If a system writes state to a database and then publishes to Kafka, a crash between those two steps results in a permanently lost event. 
Kairo avoids this entirely. It writes the workflow state and an Outbox event to Postgres in a single ACID transaction. Debezium tails the Postgres Write-Ahead Log (WAL) and streams to Kafka. **If it commits to the database, it is mathematically guaranteed to hit Kafka.**

### 2. The Duplicate Execution Problem
Kafka guarantees at-least-once delivery. If a worker crashes mid-execution or a network partition occurs, Kafka will redeliver the event, potentially causing a duplicate action (e.g. charging a user twice).
Kairo's Go workers intercept this using a **Redis Distributed Lock (SETNX)** with a TTL. If a duplicate message arrives, the worker checks the lock state, recognizes the cache hit, and safely skips execution.

### 3. The Parallel Completion Race Condition
If two parallel DAG tasks finish at the exact same millisecond, two workers will try to evaluate if the workflow is complete simultaneously, potentially overwriting each other and leaving the workflow stuck.
Kairo's Java Scheduler uses **PostgreSQL Pessimistic Write Locks** (`SELECT ... FOR UPDATE`) to serialize state transitions, guaranteeing the BFS DAG traversal never corrupts.

### 4. Distributed Tracing Loss
When execution jumps between threads, OpenTelemetry contexts are often lost. Kairo implements custom Micrometer TaskDecorators to propagate trace IDs across asynchronous boundaries, ensuring end-to-end trace visibility in Grafana Tempo.

## Running Locally

Kairo requires Java 21, Go 1.24, and Docker.

```bash
# 1. Spin up Postgres, Kafka, Redis, and Observability
docker-compose up -d

# 2. Register the Debezium Outbox Router
./scripts/register-debezium.sh

# 3. Start the Orchestrator
./gradlew :api-service:bootRun
./gradlew :scheduler-service:bootRun

# 4. Start the Worker Fleet
cd worker-service && go run main.go
```
