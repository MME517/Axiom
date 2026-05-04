# Phase 2 SaaS Hardening - Validation Guide

## Deployment

```bash
# Build
mvn clean package -DskipTests

# Run
docker-compose up -d
```

**Endpoints Base**: `http://localhost:9090`

---

## 1. STRICT TENANT ISOLATION

### Test Data
- **ACME Corp** (tenant 1): admin@acme.com / admin123, user@acme.com / user123
- **Beta Inc** (tenant 2): admin@beta.com / admin123, user@beta.com / user123

### Verify READ Isolation (Cross-tenant reads get 404)

```bash
# Get ACME admin token
ACME_TOKEN=$(curl -s -X POST http://localhost:9090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@acme.com","password":"admin123"}' | jq -r .token)

# Create project as ACME admin
PROJECT_ID=$(curl -s -X POST http://localhost:9090/projects \
  -H "Authorization: Bearer $ACME_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"ACME Secret","description":"Confidential"}' | jq -r .projectId)

# Get Beta admin token
BETA_TOKEN=$(curl -s -X POST http://localhost:9090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@beta.com","password":"admin123"}' | jq -r .token)

# Try to read ACME project as Beta admin (should get 404)
curl -s -X GET http://localhost:9090/projects/$PROJECT_ID \
  -H "Authorization: Bearer $BETA_TOKEN" \
  -w "\nStatus: %{http_code}\n"
# Expected: 404 Not Found (tenant isolation enforced)
```

### Verify LIST Isolation (Cross-tenant lists see no projects)

```bash
# Beta admin lists projects (should see 0 if no Beta projects created)
curl -s -X GET http://localhost:9090/projects \
  -H "Authorization: Bearer $BETA_TOKEN" | jq .
# Expected: [] (empty array)
```

### Verify WRITE Isolation (Project creation creates project for calling user's tenant)

```bash
# Create as ACME, verify appears only in ACME's list
curl -s -X GET http://localhost:9090/projects \
  -H "Authorization: Bearer $ACME_TOKEN" | jq . | grep projectId
# Expected: ACME's project ID

curl -s -X GET http://localhost:9090/projects \
  -H "Authorization: Bearer $BETA_TOKEN" | jq . | grep projectId
# Expected: No ACME project ID
```

---

## 2. RBAC ENFORCED

### Test 401 Unauthorized (No authentication)

```bash
curl -s -X GET http://localhost:9090/projects -w "\nStatus: %{http_code}\n"
# Expected: 401 Unauthorized
```

### Test 403 Forbidden (Insufficient role)

```bash
# TENANT_USER tries to create project (admin-only)
ACME_USER_TOKEN=$(curl -s -X POST http://localhost:9090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@acme.com","password":"user123"}' | jq -r .token)

curl -s -X POST http://localhost:9090/projects \
  -H "Authorization: Bearer $ACME_USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Forbidden","description":"Test"}' \
  -w "\nStatus: %{http_code}\n"
# Expected: 403 Forbidden
```

---

## 3. ASYNC MESSAGING (RabbitMQ)

### Trigger Report Generation (202 Accepted)

```bash
# Request async report (returns 202 immediately)
RESPONSE=$(curl -s -X POST http://localhost:9090/projects/$PROJECT_ID/generate-report \
  -H "Authorization: Bearer $ACME_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: test-001" \
  -d '{}')

JOB_ID=$(echo $RESPONSE | jq -r .jobId)
echo "Job enqueued: $JOB_ID"
# Status: 202 Accepted
```

### Poll Job Status (RabbitMQ Consumer processes)

```bash
# Wait 5 seconds for RabbitMQ consumer to process
sleep 5

# Check status (should transition PENDING → DONE)
curl -s -X GET "http://localhost:9090/projects/$PROJECT_ID/report-status?jobId=$JOB_ID" \
  -H "Authorization: Bearer $ACME_TOKEN" | jq .reportStatus
# Expected: "DONE"
```

---

## 4. OBSERVABILITY

### Health Endpoint (requires authentication)

```bash
# Check application health
curl -s -X GET http://localhost:9090/actuator/health \
  -H "Authorization: Bearer $ACME_TOKEN" | jq .status
# Expected: "UP"
```

### Prometheus Metrics (TENANT_ADMIN only)

```bash
# TENANT_ADMIN can read metrics
curl -s -X GET http://localhost:9090/actuator/prometheus \
  -H "Authorization: Bearer $ACME_TOKEN" | grep workhub_report_messages
# Expected:
# workhub_report_messages_published_total{...} 1.0
# workhub_report_messages_processed_total{...} 1.0

# TENANT_USER cannot (gets 403)
curl -s -X GET http://localhost:9090/actuator/prometheus \
  -H "Authorization: Bearer $ACME_USER_TOKEN" \
  -w "\nStatus: %{http_code}\n"
# Expected: 403 Forbidden
```

### Correlation ID Propagation

```bash
# Logs contain correlation ID throughout async workflow
docker logs workhub-app 2>&1 | grep "test-001"
# Expected:
# [correlationId=test-001] ... ReportProducer - Message published
# [correlationId=test-001] ... ReportConsumer - Message received
# [correlationId=test-001] ... ReportConsumer - Report generated
```

---

## Summary Validation

All 4 Phase 2 deliverables are implemented and working:

✅ **Strict Tenant Isolation** - Multi-tenant data segregation enforced at database query level
  - READ: Cross-tenant project access returns 404
  - WRITE: Projects created under requesting user's tenant
  - LIST: Users see only their tenant's projects

✅ **RBAC Enforced** - Role-based access control with proper HTTP status codes
  - 401: Unauthenticated requests
  - 403: Authenticated but insufficient role
  - Admin-only endpoints protected with `@PreAuthorize("hasAuthority('TENANT_ADMIN')")`

✅ **Async Messaging** - RabbitMQ workflow end-to-end
  - POST `/projects/{id}/generate-report` returns 202 Accepted
  - Message published to `report.generate` queue
  - Consumer processes and updates job status to DONE

✅ **Observability** - Actuator, metrics, health checks, correlation IDs
  - `/actuator/health` - requires authentication
  - `/actuator/prometheus` - custom metrics track message counts (TENANT_ADMIN only)
  - Correlation IDs propagate through HTTP → Producer → Consumer → Handler
