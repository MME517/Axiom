# OBSERVABILITY.md — WorkHub Phase 2

## 1. Overview

This document describes how to verify the health and metrics of the
RabbitMQ-based async report generation pipeline introduced in Phase 2.

---

## 2. Infrastructure Health

### 2.1 Spring Actuator — Composite Health

```
GET /actuator/health
Authorization: Bearer <TENANT_ADMIN_JWT>
```

Expected `status: UP` components:

| Component | What it checks |
|-----------|---------------|
| `db`      | JDBC connection to PostgreSQL |
| `rabbit`  | AMQP connection to RabbitMQ |
| `diskSpace` | Available disk space |
| `livenessState` | JVM responsiveness |
| `readinessState` | Application ready to serve traffic |

Example response snippet:

```json
{
  "status": "UP",
  "components": {
    "rabbit": {
      "status": "UP",
      "details": {
        "version": "3.13.x"
      }
    }
  }
}
```

### 2.2 Public liveness / readiness probes (no auth required)

```
GET /actuator/health/liveness
GET /actuator/health/readiness
```

---

## 3. RabbitMQ Management UI

When running via Docker Compose the Management UI is accessible at:

```
http://localhost:15672
username: guest
password: guest
```

Navigate to **Queues** to verify:

| Queue name | Expected state |
|------------|---------------|
| `report.generate` | Ready, accepting messages |
| `report.generate.dlq` | Empty under normal operation; messages appear here only after 3 failed processing attempts |

Navigate to **Exchanges** to verify:

| Exchange | Type | Purpose |
|----------|------|---------|
| `workhub.reports` | direct | Routes `report.generate` messages to the work queue |
| `workhub.reports.dlx` | direct | Dead-Letter Exchange; receives nacked/expired messages |

---

## 4. Micrometer / Prometheus Metrics

```
GET /actuator/prometheus
Authorization: Bearer <TENANT_ADMIN_JWT>
```

### 4.1 Custom WorkHub messaging counters

| Metric name | Description |
|-------------|-------------|
| `workhub_report_messages_published_total` | Messages published by `ReportProducer` |
| `workhub_report_messages_processed_total` | Messages processed successfully by `ReportConsumer` |
| `workhub_report_messages_duplicate_total` | Duplicate messages skipped by idempotency guard |
| `workhub_report_messages_failed_total` | Messages that failed all retry attempts |

### 4.2 Standard Spring AMQP metrics (auto-registered)

| Metric | Description |
|--------|-------------|
| `spring_rabbitmq_listener_*` | Listener container throughput & errors |
| `rabbitmq_connections` | Active AMQP connections |

### 4.3 Querying with curl

```bash
# Get all WorkHub-specific counters
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/actuator/prometheus \
  | grep "workhub_report"
```

---

## 5. Correlation ID Tracing

Every log line emitted during message processing includes the
`correlationId` MDC value:

```
2026-04-26 21:00:00 [rabbit-listener-1] [correlationId=3fa85f64-...] INFO  c.w.messaging.ReportConsumer - [CONSUMER] Received report job | messageId=... jobId=... projectId=...
```

The correlation ID lifecycle:

```
Client HTTP Request
  → X-Correlation-ID: <uuid>        (or server auto-generates one)
  ↓
POST /projects/{id}/generate-report (202)
  → X-Correlation-ID: <uuid>        (echoed in response header)
  ↓
RabbitMQ message.correlationId = <uuid>
  ↓
ReportConsumer MDC.put("correlationId", <uuid>)
  ↓
All consumer log lines carry the correlationId
  ↓
Job.correlationId = <uuid>           (persisted to DB)
```

To trace a single request end-to-end:

```bash
# 1. Trigger report with a known correlation ID
curl -X POST http://localhost:8080/projects/<id>/generate-report \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-ID: my-trace-123"

# 2. Grep application logs
docker logs workhub-app 2>&1 | grep "correlationId=my-trace-123"

# 3. Verify status in DB
curl http://localhost:8080/projects/<id>/report-status \
  -H "Authorization: Bearer $TOKEN"
```

---

## 6. Dead-Letter Queue Verification

To verify the DLQ receives poisoned messages:

1. Publish a message with an invalid `jobId` so the consumer throws after 3 retries.
2. In the RabbitMQ UI → **Queues** → `report.generate.dlq` → **Get Messages**:
   - The rejected message appears with the original payload intact.
3. The `workhub_report_messages_failed_total` counter increments.

---

## 7. Summary Checklist

- [ ] `GET /actuator/health` shows `rabbit: UP`
- [ ] `GET /actuator/prometheus` returns `workhub_report_messages_*` counters
- [ ] RabbitMQ Management UI shows `report.generate` queue bound to `workhub.reports` exchange
- [ ] Application logs include `[correlationId=...]` on all consumer lines
- [ ] `GET /projects/{id}/report-status` returns `reportStatus: DONE` after ~2 s
