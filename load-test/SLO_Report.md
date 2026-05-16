# WorkHub SaaS – Load Test & SLO Report

**Load Test Tool:** k6   
**Environment:** Local Docker Compose (Postgres + RabbitMQ + Spring Boot app)  
**Script:** `load-test/workhub-load-test.js`

---

## 1. Test Configuration

| Parameter | Value |
|---|---|
| Tool | k6 |
| Total Duration | 2m30s |
| Max Virtual Users (VUs) | 20 |
| Load Shape | 4-stage ramp |
| Total Iterations | 295 |
| Total Requests | 885 |
| Throughput | 5.87 req/s |

### Load Stages

| Stage | Duration | Target VUs |
|---|---|---|
| Ramp up | 30s | 5 |
| Steady load | 1m | 10 |
| Spike | 30s | 20 |
| Ramp down | 30s | 0 |

---

## 2. Endpoints Tested

| Endpoint | Scenario |
|---|---|
| `POST /auth/login` | Simulates user authentication |
| `GET /projects` | Lists all projects for the authenticated tenant |
| `GET /actuator/health` | Observability / health check |

---

## 3. Threshold Results (SLO Targets)

| Metric | SLO Target | Actual | Status |
|---|---|---|---|
| `error_rate` | < 1% | **24.63%** |  FAIL |
| `http_req_duration` p(95) | < 500ms | **4.23s** |  FAIL |
| `http_req_failed` | < 1% | **0.00%** | ✅ PASS |
| `project_list_duration` p(95) | < 400ms | **699.41ms** |  FAIL |
| `task_update_duration` p(95) | < 600ms | **0s** | ✅ PASS |

---

## 4. Detailed Results

### 4.1 Check Results

| Check | Result |
|---|---|
| Total checks | 2,360 |
| Checks passed | 90.76% (2,142 / 2,360) |
| Checks failed | 9.23% (218 / 2,360) |

| Specific Check | Passed | Failed |
|---|---|---|
| GET /projects → 200 |  100% | — |
| GET /projects → has body |  100% | — |
| GET /projects < 500ms |  88% (261/295) | 34 failed |
| POST /auth/login → 200 |  100% | — |
| login returns token |  100% | — |
| login < 500ms |  37% (111/295) | 184 failed |
| GET /actuator/health → 200 |  100% | — |
| health status UP |  100% | — |

### 4.2 Response Time Metrics

| Metric | avg | min | median | p(90) | p(95) | max |
|---|---|---|---|---|---|---|
| `http_req_duration` (all) | 793.42ms | 3.48ms | 238.67ms | 2.88s | **4.23s** | 8.79s |
| `project_list_duration` | 203.5ms | 4.52ms | 82.69ms | 528.16ms | **699.41ms** | 1.12s |
| `iteration_duration` | 4.38s | 2.17s | 3.61s | 8.03s | 8.86s | 12.25s |

### 4.3 Traffic & Network

| Metric | Value |
|---|---|
| Total requests | 885 |
| Throughput | 5.87 req/s |
| HTTP errors (4xx/5xx) | 0 (0.00%) |
| Data received | 607 kB (4.0 kB/s) |
| Data sent | 327 kB (2.2 kB/s) |

---

## 5. SLO Summary

| SLO | Target | Actual | Met? |
|---|---|---|---|
| HTTP error rate (4xx/5xx) | < 1% | 0.00% |  YES |
| p(95) latency — all requests | < 500ms | 4.23s |  NO |
| p(95) latency — GET /projects | < 400ms | 699.41ms |  NO |
| login response < 500ms | > 95% of requests | 37% |  NO |
| Application availability | 100% uptime | 100% (all 200s) |  YES |
| Health endpoint status | UP | UP |  YES |

---

## 6. Analysis & Observations

### What worked well
- **Zero HTTP failures:** All 885 requests returned valid HTTP responses (200 OK). The application never crashed, rejected connections, or returned 4xx/5xx errors during the test. Availability was 100%.
- **Health endpoint:** `GET /actuator/health` consistently returned `{"status":"UP"}` throughout, confirming the app and its dependencies (Postgres, RabbitMQ) remained stable under load.
- **Functional correctness under load:** GET /projects always returned a body and POST /auth/login always returned a valid token — the app's business logic was unaffected by concurrency.

### Where performance degraded
- **Login latency is the main bottleneck:** `POST /auth/login` breached the 500ms target in 63% of requests (184 out of 295). This is likely because each login involves a bcrypt password hash verification, which is intentionally CPU-intensive for security. Under concurrent load, bcrypt operations queue up and inflate response times significantly.
- **Project list latency degraded at peak:** `GET /projects` p(95) reached 699ms against a 400ms target. This is a database query with tenant filtering — under 20 concurrent VUs the connection pool experiences contention and query latency increases.
- **High `error_rate` custom metric:** The 24.63% rate reflects the two latency-based checks failing (login < 500ms and projects < 500ms). Importantly, this is **not** HTTP failures — all requests succeeded. The "errors" are purely latency threshold violations in the k6 custom metric.

### Root cause
The local Docker Compose deployment runs on a single machine with shared CPU and memory between Postgres, RabbitMQ, and the Spring Boot JVM. Under 20 concurrent users:
1. The JVM thread pool handles concurrent requests but bcrypt CPU cost spikes response time for login.
2. The HikariCP connection pool (default size: 10) becomes a bottleneck under 20 VUs hitting the DB simultaneously.

---

## 7. Recommendations

| Recommendation | Impact |
|---|---|
| Increase HikariCP pool size (`spring.datasource.hikari.maximum-pool-size=20`) | Reduces DB contention under high concurrency |
| Add async bcrypt / use a lighter hashing factor for non-prod | Reduces login latency under load |
| Scale to multiple app replicas via Kubernetes (already configured in `k8s/`) | Distributes CPU load across pods |
| Add a Redis cache for `GET /projects` (frequently read, rarely changed) | Reduces DB hits and cuts latency |
| Set p(95) SLO targets per environment: 500ms for local, 200ms for production | Aligns expectations with deployment tier |

---

## 8. Conclusion

The WorkHub backend demonstrated **full functional stability** under a load of up to 20 concurrent virtual users — zero HTTP errors, 100% availability, and correct business logic throughout. However, it did **not meet the latency SLOs** defined for this test, primarily due to bcrypt CPU cost on login and database connection pool contention on the shared local Docker environment.

For a production deployment using the Kubernetes manifests already in `k8s/`, horizontal scaling to 2–3 replicas with a properly sized connection pool would be expected to bring p(95) latency within the defined SLO targets.
