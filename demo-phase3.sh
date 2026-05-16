#!/bin/bash

# ============================================================================
# WorkHub Phase 3 — Week 14 Deployment Demo Script
# Validates: Docker/K8s deployment, IaC, health probes, async messaging,
#            tenant isolation, observability, and Kubernetes integration
# ============================================================================

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
CORRELATION_ID="trace-phase3-$(date +%s)"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

PASS=0
FAIL=0
TOTAL=0

pass() { ((PASS++)); ((TOTAL++)); echo -e "${GREEN}  ✓ PASS: $1${NC}"; }
fail() { ((FAIL++)); ((TOTAL++)); echo -e "${RED}  ✗ FAIL: $1${NC}"; }

echo ""
echo -e "${BLUE}╔══════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  ${BOLD}WorkHub Phase 3 — Cloud-Native Delivery Demo (Week 14)${NC}${BLUE}        ║${NC}"
echo -e "${BLUE}╠══════════════════════════════════════════════════════════════════╣${NC}"
echo -e "${BLUE}║  Correlation ID : ${CYAN}$CORRELATION_ID${BLUE}            ║${NC}"
echo -e "${BLUE}║  Base URL       : ${CYAN}$BASE_URL${BLUE}                          ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 1: INFRASTRUCTURE VERIFICATION
# ═══════════════════════════════════════════════════════════════════════════════
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}  [1/8] Infrastructure Health — Liveness & Readiness Probes${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Liveness
LIVENESS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health/liveness" 2>/dev/null || echo "000")
if [ "$LIVENESS_CODE" = "200" ]; then
    pass "Liveness probe returned 200 OK"
    LIVENESS_BODY=$(curl -s "$BASE_URL/actuator/health/liveness")
    echo "         Response: $LIVENESS_BODY"
else
    fail "Liveness probe returned HTTP $LIVENESS_CODE"
fi

# Readiness
READINESS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health/readiness" 2>/dev/null || echo "000")
if [ "$READINESS_CODE" = "200" ]; then
    pass "Readiness probe returned 200 OK (DB + RabbitMQ up)"
    READINESS_BODY=$(curl -s "$BASE_URL/actuator/health/readiness")
    echo "         Response: $READINESS_BODY"
else
    fail "Readiness probe returned HTTP $READINESS_CODE (dependencies may be down)"
fi
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 2: KUBERNETES INTEGRATION (if kubectl available)
# ═══════════════════════════════════════════════════════════════════════════════
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}  [2/8] Kubernetes Cluster Status${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

if command -v kubectl &> /dev/null; then
    NAMESPACE="workhub"

    # Check namespace exists
    if kubectl get namespace "$NAMESPACE" &>/dev/null; then
        pass "Namespace '$NAMESPACE' exists"

        # Pod status
        TOTAL_PODS=$(kubectl get pods -n "$NAMESPACE" --no-headers 2>/dev/null | wc -l | tr -d ' ')
        RUNNING_PODS=$(kubectl get pods -n "$NAMESPACE" --no-headers 2>/dev/null | grep -c "Running" || true)

        if [ "$TOTAL_PODS" -gt 0 ] && [ "$RUNNING_PODS" -eq "$TOTAL_PODS" ]; then
            pass "All $TOTAL_PODS pods are Running"
        else
            fail "Only $RUNNING_PODS/$TOTAL_PODS pods are Running"
        fi

        # Service check
        SERVICES=$(kubectl get svc -n "$NAMESPACE" --no-headers 2>/dev/null | wc -l | tr -d ' ')
        if [ "$SERVICES" -ge 3 ]; then
            pass "$SERVICES services deployed (postgres, rabbitmq, app)"
        else
            fail "Expected ≥3 services, found $SERVICES"
        fi

        echo ""
        echo -e "${CYAN}  Pod details:${NC}"
        kubectl get pods -n "$NAMESPACE" -o wide 2>/dev/null | sed 's/^/    /'
        echo ""
        echo -e "${CYAN}  Service details:${NC}"
        kubectl get svc -n "$NAMESPACE" 2>/dev/null | sed 's/^/    /'

    else
        echo -e "${YELLOW}  ⊘ Namespace '$NAMESPACE' not found — skipping K8s checks${NC}"
        echo "    (This is expected when running via Docker Compose)"
    fi
else
    echo -e "${YELLOW}  ⊘ kubectl not found — skipping Kubernetes checks${NC}"
fi
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 3: TERRAFORM STATE (if terraform available)
# ═══════════════════════════════════════════════════════════════════════════════
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}  [3/8] Terraform IaC Verification${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

TERRAFORM_DIR="$(cd "$(dirname "$0")" && pwd)/terraform"
if [ ! -d "$TERRAFORM_DIR" ]; then
    TERRAFORM_DIR="./terraform"
fi

if [ -d "$TERRAFORM_DIR" ]; then
    pass "terraform/ directory exists"

    # Check required files
    for FILE in main.tf variables.tf outputs.tf terraform.tfvars.example; do
        if [ -f "$TERRAFORM_DIR/$FILE" ]; then
            pass "terraform/$FILE present"
        else
            fail "terraform/$FILE missing"
        fi
    done

    # Check terraform.tfvars is NOT committed (should be in .gitignore)
    if grep -q "terraform.tfvars" .gitignore 2>/dev/null; then
        pass "terraform.tfvars is in .gitignore (secrets protected)"
    else
        fail "terraform.tfvars is NOT in .gitignore — secrets may leak!"
    fi

    # Terraform state check
    if command -v terraform &> /dev/null && [ -f "$TERRAFORM_DIR/.terraform.lock.hcl" ]; then
        echo ""
        echo -e "${CYAN}  Terraform state:${NC}"
        (cd "$TERRAFORM_DIR" && terraform show -no-color 2>/dev/null | head -20 | sed 's/^/    /')
    fi
else
    fail "terraform/ directory not found"
fi
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 4: AUTHENTICATION
# ═══════════════════════════════════════════════════════════════════════════════
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}  [4/8] Authentication & JWT Token Issuance${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@acme.com","password":"admin123"}')

TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.token // empty')
if [ -n "$TOKEN" ]; then
    pass "JWT token issued for admin@acme.com"
    echo "         Token: ${TOKEN:0:60}..."

    # Verify /auth/me
    ME_RESPONSE=$(curl -s "$BASE_URL/auth/me" -H "Authorization: Bearer $TOKEN")
    ME_EMAIL=$(echo "$ME_RESPONSE" | jq -r '.email // empty')
    if [ "$ME_EMAIL" = "admin@acme.com" ]; then
        pass "/auth/me correctly returns authenticated user"
    else
        fail "/auth/me returned unexpected data"
    fi
else
    fail "Login failed for admin@acme.com"
    echo -e "${RED}  Cannot proceed without auth token — aborting remaining tests${NC}"
    exit 1
fi
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 5: PROJECT CREATION (RBAC + Tenant Scope)
# ═══════════════════════════════════════════════════════════════════════════════
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}  [5/8] Project CRUD — RBAC & Tenant Scope${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

PROJECT_RESPONSE=$(curl -s -X POST "$BASE_URL/projects" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"Phase 3 Demo Project"}')

PROJECT_ID=$(echo "$PROJECT_RESPONSE" | jq -r '.projectId // empty')
if [ -n "$PROJECT_ID" ] && [ "$PROJECT_ID" != "null" ]; then
    pass "Project created (ID: $PROJECT_ID)"
else
    fail "Project creation failed"
    echo "    Response: $PROJECT_RESPONSE"
fi
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 6: ASYNC MESSAGING (RabbitMQ Integration)
# ═══════════════════════════════════════════════════════════════════════════════
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}  [6/8] Async Report Generation via RabbitMQ${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

if [ -n "$PROJECT_ID" ] && [ "$PROJECT_ID" != "null" ]; then
    REPORT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/projects/$PROJECT_ID/generate-report" \
      -H "Authorization: Bearer $TOKEN" \
      -H "X-Correlation-ID: $CORRELATION_ID")

    REPORT_HTTP=$(echo "$REPORT_RESPONSE" | tail -1)
    REPORT_BODY=$(echo "$REPORT_RESPONSE" | sed '$ d')

    if [ "$REPORT_HTTP" = "202" ]; then
        pass "Report generation accepted (202 Accepted)"
        JOB_ID=$(echo "$REPORT_BODY" | jq -r '.jobId // empty')
        echo "         Job ID: $JOB_ID"
        echo "         Correlation ID: $CORRELATION_ID"

        # Poll for completion
        echo ""
        echo "         Waiting 8 seconds for async processing..."
        sleep 8

        STATUS_RESPONSE=$(curl -s "$BASE_URL/projects/$PROJECT_ID/report-status" \
          -H "Authorization: Bearer $TOKEN")
        FINAL_STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.reportStatus // empty')

        if [ "$FINAL_STATUS" = "DONE" ]; then
            pass "Report processing completed (PENDING → DONE)"
        else
            fail "Report status is '$FINAL_STATUS' (expected DONE)"
        fi
    else
        fail "Report generation returned HTTP $REPORT_HTTP (expected 202)"
    fi
else
    echo -e "${YELLOW}  ⊘ Skipping — no project available${NC}"
fi
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 7: TENANT ISOLATION
# ═══════════════════════════════════════════════════════════════════════════════
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}  [7/8] Cross-Tenant Isolation Enforcement${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

BETA_LOGIN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@beta.com","password":"admin123"}')

BETA_TOKEN=$(echo "$BETA_LOGIN" | jq -r '.token // empty')

if [ -n "$BETA_TOKEN" ] && [ -n "$PROJECT_ID" ] && [ "$PROJECT_ID" != "null" ]; then
    ISOLATION_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/projects/$PROJECT_ID" \
      -H "Authorization: Bearer $BETA_TOKEN")

    if [ "$ISOLATION_CODE" = "404" ] || [ "$ISOLATION_CODE" = "403" ]; then
        pass "Tenant isolation enforced — Beta cannot access ACME project (HTTP $ISOLATION_CODE)"
    else
        fail "Tenant isolation BREACHED — Beta accessed ACME project (HTTP $ISOLATION_CODE)"
    fi
else
    echo -e "${YELLOW}  ⊘ Skipping — beta tenant login unavailable${NC}"
fi
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 8: OBSERVABILITY
# ═══════════════════════════════════════════════════════════════════════════════
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}  [8/8] Observability — Prometheus Metrics${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

METRICS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/prometheus" \
  -H "Authorization: Bearer $TOKEN" 2>/dev/null || echo "000")

if [ "$METRICS_CODE" = "200" ]; then
    pass "Prometheus metrics endpoint reachable"

    METRICS=$(curl -s "$BASE_URL/actuator/prometheus" -H "Authorization: Bearer $TOKEN")
    WORKHUB_METRICS=$(echo "$METRICS" | grep "workhub" | head -5)

    if [ -n "$WORKHUB_METRICS" ]; then
        pass "Custom workhub_report_* metrics present"
        echo ""
        echo -e "${CYAN}  Sample metrics:${NC}"
        echo "$WORKHUB_METRICS" | sed 's/^/    /'
    else
        echo -e "${YELLOW}  ⊘ No custom workhub metrics yet (normal on first run)${NC}"
    fi
else
    fail "Prometheus endpoint returned HTTP $METRICS_CODE"
fi
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# SUMMARY
# ═══════════════════════════════════════════════════════════════════════════════
echo -e "${BLUE}╔══════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  ${BOLD}Demo Summary${NC}${BLUE}                                                   ║${NC}"
echo -e "${BLUE}╠══════════════════════════════════════════════════════════════════╣${NC}"

if [ "$FAIL" -eq 0 ]; then
    echo -e "${BLUE}║  ${GREEN}Result: ALL $TOTAL CHECKS PASSED ✓${NC}${BLUE}                              ║${NC}"
else
    echo -e "${BLUE}║  ${RED}Result: $PASS/$TOTAL passed, $FAIL failed${NC}${BLUE}                            ║${NC}"
fi

echo -e "${BLUE}╠══════════════════════════════════════════════════════════════════╣${NC}"
echo -e "${BLUE}║  Correlation ID : ${CYAN}$CORRELATION_ID${NC}${BLUE}            ║${NC}"
[ -n "$PROJECT_ID" ] && echo -e "${BLUE}║  Project ID     : ${CYAN}$PROJECT_ID${NC}${BLUE}  ║${NC}"
[ -n "$JOB_ID" ]     && echo -e "${BLUE}║  Job ID         : ${CYAN}$JOB_ID${NC}${BLUE}  ║${NC}"
echo -e "${BLUE}╠══════════════════════════════════════════════════════════════════╣${NC}"
echo -e "${BLUE}║  ${NC}Phase 3 Artifacts:${BLUE}                                            ║${NC}"
echo -e "${BLUE}║    ${NC}• terraform/          — IaC (Kubernetes Track)${BLUE}               ║${NC}"
echo -e "${BLUE}║    ${NC}• k8s/                — Raw K8s manifests${BLUE}                    ║${NC}"
echo -e "${BLUE}║    ${NC}• docker-compose.yml  — Local orchestration${BLUE}                  ║${NC}"
echo -e "${BLUE}║    ${NC}• DEPLOYMENT.md       — Full deployment guide${BLUE}                ║${NC}"
echo -e "${BLUE}║    ${NC}• .github/workflows/  — CI pipeline${BLUE}                          ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════════╝${NC}"
echo ""

exit $FAIL
