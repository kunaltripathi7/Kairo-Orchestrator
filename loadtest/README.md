# Kairo Orchestrator Load Testing Tool

A robust load testing tool designed to blast workflows at the Kairo Orchestrator and measure durability, dispatch latency, and throughput.

## Usage

```bash
cd loadtest
go run main.go [flags]
```

## Flags

- `-n`: Total workflows to submit (default: 100)
- `-c`: Concurrency / parallel workers for submission (default: 10)
- `-url`: Base URL (default: http://localhost:8080)
- `-timeout`: Max time to wait for workflow completion in seconds (default: 120)
- `-poll-interval`: Polling interval in milliseconds (default: 500)
- `-ramp`: Ramp-up duration in seconds (default: 0)
- `-dag`: DAG type: "linear" (A->B->C), "fanout" (A->[B,C,D]->E), "diamond" (A->[B,C]->D) (default: "linear")

## Examples

**Basic blast test:**
```bash
go run main.go -n 1000 -c 50
```

**High concurrency test:**
```bash
go run main.go -n 10000 -c 200 -dag fanout -ramp 10
```
