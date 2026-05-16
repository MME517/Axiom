# WorkHub — Multi-Tenant SaaS Backend

> A cloud-native, multi-tenant project management API built with Spring Boot 3, PostgreSQL, and RabbitMQ — deployed via Docker Compose, Kubernetes, and Terraform IaC.

## Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Java | 21+ | Application runtime |
| Maven | 3.9+ | Build & dependency management |
| Docker | 24+ | Containerization |
| Docker Compose | v2+ | Local orchestration |
| Minikube | 1.32+ | Local Kubernetes cluster |
| kubectl | 1.28+ | Kubernetes CLI |
| Terraform / OpenTofu | 1.5+ | Infrastructure as Code |

## Run Locally

### Option 1: Docker Compose (recommended)
```bash
docker-compose up --build -d
```

### Option 2: Local Maven
```bash
# Start infrastructure first
docker-compose up postgres rabbitmq -d

# Run app
./mvnw spring-boot:run
```

### Option 3: Kubernetes (Minikube)
```bash
minikube start --driver=docker --memory=4096 --cpus=2
eval $(minikube docker-env)
docker build -t workhub-app:latest .
kubectl apply -f k8s/
```

### Option 4: Terraform IaC
```bash
cd terraform/
cp terraform.tfvars.example terraform.tfvars   # fill in credentials
terraform init && terraform plan && terraform apply
```

> 📘 See **[DEPLOYMENT.md](DEPLOYMENT.md)** for full step-by-step instructions for all deployment methods.

## Default Ports
- App: http://localhost:8080
- Postgres: localhost:5434
- RabbitMQ AMQP: localhost:5672
- RabbitMQ Management UI: http://localhost:15672

## Test Credentials
| Email | Password | Role | Tenant |
|---|---|---|---|
| admin@acme.com | admin123 | TENANT_ADMIN | Acme Corp |
| user@acme.com | user123 | TENANT_USER | Acme Corp |
| admin@beta.com | admin123 | TENANT_ADMIN | Beta Inc |

## API Endpoints
| Method | Endpoint | Auth | Role | Description |
|---|---|---|---|---|
| POST | /auth/login | No | — | Returns JWT token |
| GET | /auth/me | Yes | Any | Returns current user + tenant |
| POST | /projects | Yes | TENANT_ADMIN | Create a new project |
| GET | /projects | Yes | Any | List all projects (tenant-scoped) |
| GET | /projects/{id} | Yes | Any | Get project by ID (tenant-scoped) |
| POST | /projects/{id}/tasks | Yes | Any | Create a task under a project |
| PATCH | /tasks/{id} | Yes | Any | Update task status |
| POST | /projects/{id}/generate-report | Yes | Any | Trigger async report generation (202 Accepted) |
| GET | /projects/{id}/report-status | Yes | Any | Poll async report completion status |
| GET | /actuator/health | Yes | TENANT_ADMIN | Full health details |
| GET | /actuator/health/liveness | No | — | Kubernetes liveness probe |
| GET | /actuator/health/readiness | No | — | Kubernetes readiness probe |
| GET | /actuator/prometheus | Yes | TENANT_ADMIN | Prometheus metrics scrape |

## Environment Variables
| Variable | Default | Description |
|---|---|---|
| SPRING_DATASOURCE_URL | jdbc:postgresql://localhost:5434/workhub | JDBC connection string |
| SPRING_DATASOURCE_USERNAME | workhub_user | Database username |
| SPRING_DATASOURCE_PASSWORD | workhub_pass | Database password |
| SPRING_RABBITMQ_HOST | localhost | RabbitMQ hostname |
| SPRING_RABBITMQ_PORT | 5672 | RabbitMQ AMQP port |
| SPRING_RABBITMQ_USERNAME | guest | RabbitMQ username |
| SPRING_RABBITMQ_PASSWORD | guest | RabbitMQ password |
| SPRING_PROFILES_ACTIVE | — | Set to `prod` for container deployments |
| JWT_SECRET | (in application.yml) | JWT signing key (256-bit min) |

## Package Structure
```
src/main/java/com/workhub/
├── config/         Spring configuration + DataSeeder
├── controller/     REST endpoints
├── dto/            Request and response DTOs
├── entity/         JPA entities (Tenant, User, Project, Task, Job)
├── exception/      Global exception handler
├── filter/         HTTP filters (CorrelationIdFilter)
├── messaging/      RabbitMQ producer/consumer + async workflows
├── repository/     JPA repositories
├── security/       JWT and security setup
├── service/        Business logic
└── tenant/         Tenant context and isolation
```

---

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

### Running the Phase 2 Demo
```bash
# Using Postman
- Import: workhub-phase2-week12.postman_collection.json
- Set baseUrl variable
- Run requests in order (1-7)

# Using curl/bash
bash demo-phase2.sh
```

---

## Phase 3 — Week 14: Cloud-Native Delivery + IaC + CI

### Deliverables
- ✅ **Docker & Compose** — Multi-stage Dockerfile + 3-service compose stack with healthchecks
- ✅ **Kubernetes Manifests** — 7 manifests (namespace, secret, configmap, postgres, rabbitmq, app, service)
- ✅ **Terraform IaC** — Kubernetes Track with `hashicorp/kubernetes` provider (9 resources)
- ✅ **CI Pipeline** — GitHub Actions with build, test (RabbitMQ service), and Docker image jobs
- ✅ **DEPLOYMENT.md** — Comprehensive deployment guide for all 3 methods
- ✅ **Integration Demo** — 19-check validation script (`demo-phase3.sh`)

### Repository Structure

```
Axiom/
├── docker-compose.yml              # Local compose stack (3 services)
├── Dockerfile                      # Multi-stage build (Maven → JRE, non-root)
├── k8s/                            # Kubernetes manifests
│   ├── namespace.yaml
│   ├── secret.yaml
│   ├── configmap.yaml
│   ├── postgres-deployment.yaml
│   ├── rabbitmq-deployment.yaml
│   ├── deployment.yaml
│   └── service.yaml
├── terraform/                      # Terraform IaC (Kubernetes Track)
│   ├── main.tf                     # Provider + 9 resource definitions
│   ├── variables.tf                # 24 input variables (sensitive flagged)
│   ├── outputs.tf                  # Connection strings & deployment summary
│   └── terraform.tfvars.example    # Template (no secrets committed)
├── .github/workflows/ci.yml        # CI pipeline
├── demo-phase2.sh                  # Phase 2 demo script
├── demo-phase3.sh                  # Phase 3 deployment demo (19 checks)
├── DEPLOYMENT.md                   # Full deployment guide
├── OBSERVABILITY.md                # Health checks, metrics, correlation IDs
├── TENANT-ISOLATION-PROOF.md       # Cross-tenant access denial proof
├── src/                            # Spring Boot application source
└── pom.xml
```

### Kubernetes Deployment (Minikube)

```bash
# Start Minikube
minikube start --driver=docker --memory=4096 --cpus=2

# Build image inside Minikube's Docker daemon
eval $(minikube docker-env)
docker build -t workhub-app:latest .

# Apply all manifests
kubectl apply -f k8s/

# Verify pods are running
kubectl get all -n workhub
```

| Manifest | Resource |
|---|---|
| `namespace.yaml` | `workhub` namespace |
| `secret.yaml` | DB/RabbitMQ/JWT credentials (Opaque) |
| `configmap.yaml` | JDBC URL, RabbitMQ host, Spring profile |
| `postgres-deployment.yaml` | PostgreSQL 15 Deployment + ClusterIP Service |
| `rabbitmq-deployment.yaml` | RabbitMQ 3.13 Deployment + ClusterIP Service |
| `deployment.yaml` | WorkHub app with liveness/readiness probes + resource limits |
| `service.yaml` | ClusterIP on port 80 → 8080 |

### Terraform Infrastructure as Code

The `terraform/` directory provisions the **exact same Kubernetes stack** declaratively using the `hashicorp/kubernetes` provider:

```bash
cd terraform/
cp terraform.tfvars.example terraform.tfvars   # fill in credentials
terraform init     # downloads kubernetes provider
terraform plan     # previews 9 resources
terraform apply    # provisions everything
```

**Resources managed (9 total):**

| # | Resource | Terraform Type |
|---|---|---|
| 1 | Namespace `workhub` | `kubernetes_namespace` |
| 2 | Secret `workhub-secret` | `kubernetes_secret` |
| 3 | ConfigMap `workhub-config` | `kubernetes_config_map` |
| 4 | PostgreSQL Deployment | `kubernetes_deployment` |
| 5 | PostgreSQL Service | `kubernetes_service` |
| 6 | RabbitMQ Deployment | `kubernetes_deployment` |
| 7 | RabbitMQ Service | `kubernetes_service` |
| 8 | WorkHub App Deployment | `kubernetes_deployment` |
| 9 | WorkHub App Service | `kubernetes_service` |

**Terraform Outputs:**
```
namespace                    = "workhub"
postgres_connection_string   = "jdbc:postgresql://postgres-service:5432/workhub"
rabbitmq_amqp_endpoint       = "rabbitmq-service:5672"
app_internal_url             = "http://workhub-service:80"
```

### Phase 3 Demo — All 19 Checks Passing ✅

```bash
bash demo-phase3.sh
```

```
╔══════════════════════════════════════════════════════════════════╗
║  WorkHub Phase 3 — Cloud-Native Delivery Demo (Week 14)        ║
╚══════════════════════════════════════════════════════════════════╝

 [1/8] Infrastructure Health — Liveness & Readiness Probes
  ✓ PASS: Liveness probe returned 200 OK
  ✓ PASS: Readiness probe returned 200 OK (DB + RabbitMQ up)

 [2/8] Kubernetes Cluster Status
  ✓ PASS: Namespace 'workhub' exists
  ✓ PASS: All 3 pods are Running
  ✓ PASS: 3 services deployed (postgres, rabbitmq, app)

 [3/8] Terraform IaC Verification
  ✓ PASS: terraform/ directory exists
  ✓ PASS: terraform/main.tf present
  ✓ PASS: terraform/variables.tf present
  ✓ PASS: terraform/outputs.tf present
  ✓ PASS: terraform/terraform.tfvars.example present
  ✓ PASS: terraform.tfvars is in .gitignore (secrets protected)

 [4/8] Authentication & JWT Token Issuance
  ✓ PASS: JWT token issued for admin@acme.com
  ✓ PASS: /auth/me correctly returns authenticated user

 [5/8] Project CRUD — RBAC & Tenant Scope
  ✓ PASS: Project created

 [6/8] Async Report Generation via RabbitMQ
  ✓ PASS: Report generation accepted (202 Accepted)
  ✓ PASS: Report processing completed (PENDING → DONE)

 [7/8] Cross-Tenant Isolation Enforcement
  ✓ PASS: Tenant isolation enforced — Beta cannot access ACME project (HTTP 404)

 [8/8] Observability — Prometheus Metrics
  ✓ PASS: Prometheus metrics endpoint reachable
  ✓ PASS: Custom workhub_report_* metrics present

╔══════════════════════════════════════════════════════════════════╗
║  Result: ALL 19 CHECKS PASSED ✓                                ║
╚══════════════════════════════════════════════════════════════════╝
```

---

## CI/CD Pipeline

The GitHub Actions pipeline (`.github/workflows/ci.yml`) runs on every push to `main`, `master`, or `dev`, and on all pull requests.

### Pipeline Jobs

**Job 1 — Build & Run Tests** (`build-and-test`)
- Sets up JDK 21
- Builds the project JAR (`mvn clean package -DskipTests`)
- Runs all tests including integration tests (`mvn test`)
- Starts a real RabbitMQ broker as a service container so the Spring context loads correctly
- `MessagingReliabilityTest` uses Testcontainers to spin up its own isolated broker
- Uploads Surefire test reports as a build artifact

**Job 2 — Build Docker Image** (`build-docker-image`)
- Runs only if Job 1 passes (`needs: build-and-test`)
- Builds the Docker image using the project `Dockerfile`
- Uses GitHub Actions layer caching to speed up subsequent builds

### Pipeline Triggers

| Event | Branches |
|---|---|
| `push` | `main`, `master`, `dev` |
| `pull_request` | `main`, `master` |

### Viewing Pipeline Results

Go to the **Actions** tab in the GitHub repository to see all pipeline runs. Each run shows per-step logs and uploaded test result artifacts.

---

## Key Documentation
- **[DEPLOYMENT.md](DEPLOYMENT.md)** — Full deployment guide (Docker Compose + K8s + Terraform)
- **[OBSERVABILITY.md](OBSERVABILITY.md)** — Health checks, metrics, correlation ID tracing
- **[TENANT-ISOLATION-PROOF.md](TENANT-ISOLATION-PROOF.md)** — Cross-tenant access denial proof
- **[PHASE2-IMPLEMENTATION.md](PHASE2-IMPLEMENTATION.md)** — Phase 2 implementation details
