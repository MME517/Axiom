# WorkHub — Multi-Tenant SaaS Backend

## Prerequisites
- Java 21+
- Docker + Docker Compose
- Maven

## Run Locally

### Option 1: Docker Compose (recommended)
```bash
mvn clean package -DskipTests
docker-compose up --build
```

### Option 2: Local Maven
```bash
# Start Postgres first
docker-compose up postgres

# Run app
./mvnw spring-boot:run
```

## Default Ports
- App: http://localhost:8080 (Phase 2)
- Postgres: localhost:5434 (Phase 2)
- RabbitMQ: localhost:5672 (Phase 2)
- RabbitMQ Management UI: http://localhost:15672

## Test Credentials
| Email | Password | Role | Tenant |
|---|---|---|---|
| admin@acme.com | admin123 | TENANT_ADMIN | Acme Corp |
| user@acme.com | user123 | TENANT_USER | Acme Corp |
| admin@beta.com | admin123 | TENANT_ADMIN | Beta Inc |

## API Endpoints
| Method | Endpoint | Auth Required | Role Required | Description |
|---|---|---|---|---|
| POST | /auth/login | No | None | Returns JWT token |
| GET | /auth/me | Yes | Any | Returns current user + tenant |
| POST | /projects | Yes | TENANT_ADMIN | Create a new project |
| GET | /projects | Yes | Any | List all projects (tenant-scoped) |
| GET | /projects/{id} | Yes | Any | Get project by ID (tenant-scoped) |
| POST | /projects/{id}/tasks | Yes | Any | Create a task under a project |
| PATCH | /tasks/{id} | Yes | Any | Update task status |
| **POST** | **/projects/{id}/generate-report** | **Yes** | **Any** | **Trigger async report generation (202 Accepted)** |
| **GET** | **/projects/{id}/report-status** | **Yes** | **Any** | **Poll async report completion status** |
| **GET** | **/actuator/health** | Yes | TENANT_ADMIN | Health check (rabbit, db, etc.) |
| **GET** | **/actuator/health/liveness** | No | None | Kubernetes liveness probe |
| **GET** | **/actuator/health/readiness** | No | None | Kubernetes readiness probe |
| **GET** | **/actuator/prometheus** | Yes | TENANT_ADMIN | Prometheus metrics (workhub counters) |

## Environment Variables
| Variable | Default |
|---|---|
| SPRING_DATASOURCE_URL | jdbc:postgresql://localhost:5432/workhub |
| SPRING_DATASOURCE_USERNAME | workhub_user |
| SPRING_DATASOURCE_PASSWORD | workhub_pass |

## Package Structure
- `entity/` — JPA entities (Tenant, User, Project, Task, Job)
- `dto/` — Request and response DTOs
- `exception/` — Global exception handler
- `config/` — Spring configuration + DataSeeder
- `security/` — JWT and security setup
- `filter/` — HTTP filters (CorrelationIdFilter)
- `repository/` — JPA repositories
- `service/` — Business logic
- `controller/` — REST endpoints
- `tenant/` — Tenant context and isolation
- `messaging/` — RabbitMQ producer/consumer + async workflows

## Phase 2 — Week 12: SaaS Hardening

### Deliverables
- ✅ **Strict Tenant Isolation** (read/write/list) — Enforced at repository layer
- ✅ **RBAC Enforcement** (401/403 behaviors) — JWT + Spring Security
- ✅ **Messaging-based Async Workflow** — RabbitMQ with idempotency + DLQ
- ✅ **Observability** — Actuator, metrics, health probes, correlation ID logging

### Key Features
1. **Async Report Generation** — POST to trigger (202 Accepted), GET to poll status
2. **Correlation ID Tracing** — Auto-generated or provided via `X-Correlation-ID` header
3. **Micrometer Metrics** — Custom counters for message throughput
4. **Dead-Letter Queue** — Failed messages after 3 retries
5. **Idempotent Consumers** — Prevent duplicate processing via `processed_messages` table

### Running the Demo
```bash
# Using Postman
- Import: workhub-phase2-week12.postman_collection.json
- Set baseUrl variable
- Run requests in order (1-7)

# Using curl/bash
bash demo-phase2.sh
```

### Key Documentation
- **OBSERVABILITY.md** — Health checks, metrics, correlation ID tracing
- **TENANT-ISOLATION-PROOF.md** — Cross-tenant access denial proof
