# WorkHub — Deployment Guide

> **Phase 3 · Week 14 — Cloud-Native Delivery + Infrastructure as Code**

This document covers three deployment methods for the WorkHub multi-tenant SaaS backend:

1. **Local Docker Compose** — fastest way to run everything locally
2. **Kubernetes (Minikube)** — production-like orchestration on a local cluster
3. **Terraform IaC** — reproducible, declarative provisioning of the Kubernetes stack

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Repository Structure](#repository-structure)
- [1. Local Docker Compose](#1-local-docker-compose)
  - [1.1 Quick Start](#11-quick-start)
  - [1.2 Verifying the Stack](#12-verifying-the-stack)
  - [1.3 Stopping & Cleanup](#13-stopping--cleanup)
- [2. Kubernetes Deployment (Minikube)](#2-kubernetes-deployment-minikube)
  - [2.1 Cluster Setup](#21-cluster-setup)
  - [2.2 Building the Image](#22-building-the-image)
  - [2.3 Applying Manifests](#23-applying-manifests)
  - [2.4 Verifying the Deployment](#24-verifying-the-deployment)
  - [2.5 Accessing the Application](#25-accessing-the-application)
  - [2.6 Troubleshooting](#26-troubleshooting)
- [3. Terraform Infrastructure as Code](#3-terraform-infrastructure-as-code)
  - [3.1 Overview](#31-overview)
  - [3.2 Directory Structure](#32-directory-structure)
  - [3.3 Configuration](#33-configuration)
  - [3.4 Provisioning](#34-provisioning)
  - [3.5 Verifying Outputs](#35-verifying-outputs)
  - [3.6 Destroying Infrastructure](#36-destroying-infrastructure)
- [4. CI Pipeline](#4-ci-pipeline)
- [5. Environment Variables Reference](#5-environment-variables-reference)
- [6. Health Checks & Probes](#6-health-checks--probes)
- [7. Demo Script](#7-demo-script)

---

## Prerequisites

| Tool | Minimum Version | Purpose |
|---|---|---|
| **Java** | 21 | Application build |
| **Maven** | 3.9+ | Dependency management |
| **Docker** | 24+ | Containerization |
| **Docker Compose** | v2+ | Local orchestration |
| **Minikube** | 1.32+ | Local Kubernetes cluster |
| **kubectl** | 1.28+ | Kubernetes CLI |
| **Terraform** | 1.5+ | Infrastructure as Code |

---

## Repository Structure

```
Axiom/
├── docker-compose.yml          # Local compose stack
├── Dockerfile                  # Multi-stage build (Maven → JRE)
├── k8s/                        # Raw Kubernetes manifests
│   ├── namespace.yaml
│   ├── secret.yaml
│   ├── configmap.yaml
│   ├── postgres-deployment.yaml
│   ├── rabbitmq-deployment.yaml
│   ├── deployment.yaml
│   └── service.yaml
├── terraform/                  # Terraform IaC (Kubernetes Track)
│   ├── main.tf
│   ├── variables.tf
│   ├── outputs.tf
│   └── terraform.tfvars.example
├── .github/workflows/ci.yml   # CI pipeline
├── demo-phase3.sh              # Phase 3 deployment demo script
├── src/                        # Spring Boot application source
└── pom.xml
```

---

## 1. Local Docker Compose

### 1.1 Quick Start

```bash
# Clone the repository
git clone <repository-url> && cd Axiom

# Build and start all services (PostgreSQL + RabbitMQ + App)
docker-compose up --build -d
```

This starts three containers:

| Container | Port | Description |
|---|---|---|
| `workhub-postgres` | `5434:5432` | PostgreSQL 15 database |
| `workhub-rabbitmq` | `5672:5672`, `15672:15672` | RabbitMQ 3.13 + Management UI |
| `workhub-app` | `8080:8080` | WorkHub Spring Boot application |

### 1.2 Verifying the Stack

```bash
# Check all containers are healthy
docker-compose ps

# Test liveness probe (no auth required)
curl -s http://localhost:8080/actuator/health/liveness | jq .

# Test readiness probe (checks DB + RabbitMQ)
curl -s http://localhost:8080/actuator/health/readiness | jq .

# Login and test the API
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@acme.com","password":"admin123"}' | jq -r '.token')

curl -s http://localhost:8080/auth/me -H "Authorization: Bearer $TOKEN" | jq .
```

### 1.3 Stopping & Cleanup

```bash
# Stop services (preserve data)
docker-compose down

# Stop and remove volumes (full reset)
docker-compose down -v
```

---

## 2. Kubernetes Deployment (Minikube)

### 2.1 Cluster Setup

```bash
# Start Minikube with Docker driver
minikube start --driver=docker --memory=4096 --cpus=2

# Verify cluster is running
kubectl cluster-info
```

### 2.2 Building the Image

The application image must be built **inside Minikube's Docker daemon** so that `imagePullPolicy: IfNotPresent` can locate it.

```bash
# Point your terminal's Docker to Minikube's daemon
eval $(minikube docker-env)

# Build the image
docker build -t workhub-app:latest .

# Verify
docker images | grep workhub-app
```

### 2.3 Applying Manifests

Apply the manifests in dependency order:

```bash
# 1. Namespace
kubectl apply -f k8s/namespace.yaml

# 2. Secrets & ConfigMap (must exist before deployments reference them)
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/configmap.yaml

# 3. Infrastructure services
kubectl apply -f k8s/postgres-deployment.yaml
kubectl apply -f k8s/rabbitmq-deployment.yaml

# 4. Application
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

Or apply everything at once:

```bash
kubectl apply -f k8s/
```

### 2.4 Verifying the Deployment

```bash
# All resources in the workhub namespace
kubectl get all -n workhub

# Wait for all pods to become Ready
kubectl wait --for=condition=Ready pod --all -n workhub --timeout=180s

# Check pod logs
kubectl logs -n workhub deploy/workhub-app --tail=50

# Verify DB connectivity from app logs
kubectl logs -n workhub deploy/workhub-app | grep "Started"
```

Expected output:

```
NAME                            READY   STATUS    RESTARTS   AGE
pod/postgres-xxx                1/1     Running   0          2m
pod/rabbitmq-xxx                1/1     Running   0          2m
pod/workhub-app-xxx             1/1     Running   0          1m

NAME                       TYPE        CLUSTER-IP       PORT(S)
service/postgres-service   ClusterIP   10.96.x.x        5432/TCP
service/rabbitmq-service   ClusterIP   10.96.x.x        5672/TCP,15672/TCP
service/workhub-service    ClusterIP   10.96.x.x        80/TCP
```

### 2.5 Accessing the Application

```bash
# Option A: Port-forward to localhost
kubectl port-forward -n workhub svc/workhub-service 8080:80 &

# Option B: Minikube service tunnel
minikube service workhub-service -n workhub --url

# Test the API
curl -s http://localhost:8080/actuator/health/liveness | jq .
```

### 2.6 Troubleshooting

| Symptom | Fix |
|---|---|
| ImagePullBackOff | Run `eval $(minikube docker-env)` then `docker build -t workhub-app:latest .` |
| CrashLoopBackOff on app | DB/RabbitMQ not ready yet — check `kubectl logs` and wait |
| Connection refused | Ensure `port-forward` is active or use `minikube service` |

---

## 3. Terraform Infrastructure as Code

### 3.1 Overview

The `terraform/` directory implements the **Kubernetes Track** using the `hashicorp/kubernetes` provider. It provisions the **exact same infrastructure** as the raw k8s manifests but in a fully declarative, reproducible manner.

**Resources managed by Terraform:**

| Resource | Terraform Type |
|---|---|
| Namespace `workhub` | `kubernetes_namespace` |
| Secret `workhub-secret` | `kubernetes_secret` |
| ConfigMap `workhub-config` | `kubernetes_config_map` |
| PostgreSQL Deployment + Service | `kubernetes_deployment` + `kubernetes_service` |
| RabbitMQ Deployment + Service | `kubernetes_deployment` + `kubernetes_service` |
| WorkHub App Deployment + Service | `kubernetes_deployment` + `kubernetes_service` |

### 3.2 Directory Structure

```
terraform/
├── main.tf                   # Provider config + all resource definitions
├── variables.tf              # Input variable declarations (sensitive flagged)
├── outputs.tf                # Connection strings, service names, summary
└── terraform.tfvars.example  # Template — copy to terraform.tfvars
```

### 3.3 Configuration

```bash
cd terraform/

# Copy the example template
cp terraform.tfvars.example terraform.tfvars

# Edit with real credentials
vi terraform.tfvars
```

Required variables to set in `terraform.tfvars`:

```hcl
db_username       = "workhub_user"
db_password       = "workhub_pass"
rabbitmq_username = "guest"
rabbitmq_password = "guest"
jwt_secret        = "workhub-super-secret-key-must-be-at-least-256-bits-long-here"
```

> ⚠️ **Never commit `terraform.tfvars`** — it contains secrets. Only the `.example` template is version-controlled.

### 3.4 Provisioning

```bash
# Ensure Minikube is running and image is built
minikube start --driver=docker
eval $(minikube docker-env)
docker build -t workhub-app:latest ../

# Initialize Terraform (downloads the kubernetes provider)
terraform init

# Preview what will be created
terraform plan

# Apply — provisions all resources
terraform apply
```

`terraform apply` will display the plan and prompt for confirmation. Type `yes` to proceed.

### 3.5 Verifying Outputs

After a successful apply, Terraform prints the output values:

```bash
terraform output

# Example output:
# namespace               = "workhub"
# postgres_connection_string = "jdbc:postgresql://postgres-service:5432/workhub"
# rabbitmq_amqp_endpoint    = "rabbitmq-service:5672"
# app_internal_url          = "http://workhub-service:80"
```

Verify with kubectl:

```bash
kubectl get all -n workhub
kubectl wait --for=condition=Ready pod --all -n workhub --timeout=180s
```

### 3.6 Destroying Infrastructure

```bash
# Tear down all Terraform-managed resources
terraform destroy
```

---

## 4. CI Pipeline

The GitHub Actions CI pipeline (`.github/workflows/ci.yml`) runs on every push to `main`, `master`, or `dev`:

| Job | Steps |
|---|---|
| **build-and-test** | Checkout → JDK 21 → `mvn clean package` → `mvn test` (with RabbitMQ service) → Upload test results |
| **build-docker-image** | Checkout → Docker Buildx → Build image (cache via GHA) |

The pipeline uses a **RabbitMQ service container** to run integration tests that exercise the async messaging pipeline.

---

## 5. Environment Variables Reference

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5434/workhub` | JDBC connection string |
| `SPRING_DATASOURCE_USERNAME` | `workhub_user` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `workhub_pass` | Database password |
| `SPRING_RABBITMQ_HOST` | `localhost` | RabbitMQ hostname |
| `SPRING_RABBITMQ_PORT` | `5672` | RabbitMQ AMQP port |
| `SPRING_RABBITMQ_USERNAME` | `guest` | RabbitMQ username |
| `SPRING_RABBITMQ_PASSWORD` | `guest` | RabbitMQ password |
| `SPRING_PROFILES_ACTIVE` | (none) | Set to `prod` for container deployments |
| `JWT_SECRET` | (in application.yml) | JWT signing key (256-bit min) |

---

## 6. Health Checks & Probes

The application exposes Spring Boot Actuator health endpoints used by both Docker Compose and Kubernetes:

| Endpoint | Auth | Purpose | Checks |
|---|---|---|---|
| `/actuator/health/liveness` | No | Kubernetes liveness probe | App is alive |
| `/actuator/health/readiness` | No | Kubernetes readiness probe | DB + RabbitMQ connectivity |
| `/actuator/health` | Yes | Full health details | All subsystems |
| `/actuator/prometheus` | Yes | Prometheus metrics scrape | Custom `workhub_report_*` counters |

**Probe Configuration (K8s):**

```yaml
livenessProbe:
  path: /actuator/health/liveness
  initialDelaySeconds: 90      # JVM startup time
  periodSeconds: 30
  failureThreshold: 3

readinessProbe:
  path: /actuator/health/readiness
  initialDelaySeconds: 60
  periodSeconds: 10
  failureThreshold: 3
```

---
## 7. Blue/Green Deployment Strategy

The `k8s/` folder contains two app deployments:
- `deployment.yaml` — **blue** (current stable version)
- `deployment-green.yaml` — **green** (new version being rolled out)

Both share the same Service selector (`app: workhub-app`), so
traffic is controlled by scaling replicas up or down.

### Switching traffic to green
```bash
kubectl scale deployment workhub-app-green --replicas=1 -n workhub
kubectl scale deployment workhub-app       --replicas=0 -n workhub
```

### Rolling back to blue
```bash
kubectl scale deployment workhub-app       --replicas=1 -n workhub
kubectl scale deployment workhub-app-green --replicas=0 -n workhub
```

Green starts at 0 replicas by default — blue serves all traffic
until an intentional cutover.
## 8. Demo Script

Run the Phase 3 deployment demo to validate the full end-to-end integration:

```bash
# Docker Compose mode
bash demo-phase3.sh

# Kubernetes mode (after port-forwarding)
bash demo-phase3.sh
```

The demo script validates:
1. ✅ Health probes (liveness + readiness)
2. ✅ Authentication & JWT issuance
3. ✅ Project creation (tenant-scoped)
4. ✅ Async report generation via RabbitMQ (202 → DONE)
5. ✅ Tenant isolation enforcement
6. ✅ Observability metrics (Prometheus)
7. ✅ Kubernetes pod/service status (if running in K8s mode)

---

## Appendix: Tagging for Submission

```bash
# Create the Phase 3 release tag
git tag -a v3-phase3-week14 -m "Phase 3: Cloud-Native Delivery + IaC + CI"
git push origin v3-phase3-week14
```
