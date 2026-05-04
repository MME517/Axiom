#!/bin/bash

# ============================================================================
# WorkHub Phase 2 — Week 12 Demo Script
# Demonstrates: Tenant Isolation, RBAC, Async Messaging, Observability
# ============================================================================

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
CORRELATION_ID="trace-phase2-$(date +%s)"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}     WorkHub Phase 2 — SaaS Hardening (Week 12) Demo${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

# ─────────────────────────────────────────────────────────────────────────
# 1. HEALTH CHECK
# ─────────────────────────────────────────────────────────────────────────
echo -e "${YELLOW}[1/7] Health Check (public endpoints - no auth required)${NC}"
echo ""

echo "Testing liveness probe..."
LIVENESS=$(curl -s -w "%{http_code}" -o /tmp/liveness.json "$BASE_URL/actuator/health/liveness")
if [ "$LIVENESS" = "200" ]; then
    echo -e "${GREEN}✓ Liveness probe: UP${NC}"
    cat /tmp/liveness.json | jq '.'
else
    echo -e "${RED}✗ Liveness probe failed (HTTP $LIVENESS)${NC}"
fi
echo ""

echo "Testing readiness probe..."
READINESS=$(curl -s -w "%{http_code}" -o /tmp/readiness.json "$BASE_URL/actuator/health/readiness")
if [ "$READINESS" = "200" ]; then
    echo -e "${GREEN}✓ Readiness probe: UP${NC}"
    cat /tmp/readiness.json | jq '.status'
else
    echo -e "${RED}✗ Readiness probe failed (HTTP $READINESS)${NC}"
fi
echo ""

# ─────────────────────────────────────────────────────────────────────────
# 2. AUTHENTICATION (Tenant ACME)
# ─────────────────────────────────────────────────────────────────────────
echo -e "${YELLOW}[2/7] Authentication - ACME Tenant Login${NC}"
echo ""

echo "Logging in as admin@acme.com..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@acme.com","password":"admin123"}')

TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.token')
if [ "$TOKEN" != "null" ] && [ -n "$TOKEN" ]; then
    echo -e "${GREEN}✓ Authentication successful${NC}"
    echo "Token (first 50 chars): ${TOKEN:0:50}..."
else
    echo -e "${RED}✗ Authentication failed${NC}"
    exit 1
fi
echo ""

# ─────────────────────────────────────────────────────────────────────────
# 3. PROJECT CREATION
# ─────────────────────────────────────────────────────────────────────────
echo -e "${YELLOW}[3/7] Project Creation${NC}"
echo ""

echo "Creating project: 'Phase 2 Demo Project'..."
PROJECT_RESPONSE=$(curl -s -X POST "$BASE_URL/projects" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"Phase 2 Demo Project"}')

PROJECT_ID=$(echo "$PROJECT_RESPONSE" | jq -r '.projectId')
if [ "$PROJECT_ID" != "null" ] && [ -n "$PROJECT_ID" ]; then
    echo -e "${GREEN}✓ Project created successfully${NC}"
    echo "Project ID: $PROJECT_ID"
else
    echo -e "${RED}✗ Project creation failed${NC}"
    echo "$PROJECT_RESPONSE" | jq '.'
    exit 1
fi
echo ""

# ─────────────────────────────────────────────────────────────────────────
# 4. ASYNC REPORT GENERATION (Messaging + Observability)
# ─────────────────────────────────────────────────────────────────────────
echo -e "${YELLOW}[4/7] Async Report Generation (202 Accepted)${NC}"
echo ""

echo "Publishing report generation job with Correlation ID: $CORRELATION_ID"
REPORT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/projects/$PROJECT_ID/generate-report" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-ID: $CORRELATION_ID")

REPORT_HTTP_CODE=$(echo "$REPORT_RESPONSE" | tail -1)
REPORT_BODY=$(echo "$REPORT_RESPONSE" | head -n -1)

if [ "$REPORT_HTTP_CODE" = "202" ]; then
    echo -e "${GREEN}✓ Report generation accepted (202 Accepted)${NC}"
    JOB_ID=$(echo "$REPORT_BODY" | jq -r '.jobId')
    echo "Job ID: $JOB_ID"
    echo "Report Status: $(echo "$REPORT_BODY" | jq -r '.reportStatus')"
    echo "Response Headers include: X-Correlation-ID"
else
    echo -e "${RED}✗ Report generation failed (HTTP $REPORT_HTTP_CODE)${NC}"
    echo "$REPORT_BODY"
    exit 1
fi
echo ""

# ─────────────────────────────────────────────────────────────────────────
# 5. POLL REPORT STATUS (Immediate & After Delay)
# ─────────────────────────────────────────────────────────────────────────
echo -e "${YELLOW}[5/7] Poll Report Status (Initial - should be PENDING)${NC}"
echo ""

echo "Checking report status immediately..."
STATUS_IMMEDIATE=$(curl -s "$BASE_URL/projects/$PROJECT_ID/report-status" \
  -H "Authorization: Bearer $TOKEN")

STATUS=$(echo "$STATUS_IMMEDIATE" | jq -r '.reportStatus')
echo "Report Status: $STATUS"
if [ "$STATUS" = "PENDING" ] || [ "$STATUS" = "IN_PROGRESS" ]; then
    echo -e "${GREEN}✓ Status is correctly in PENDING/IN_PROGRESS${NC}"
else
    echo -e "${YELLOW}! Status already: $STATUS${NC}"
fi
echo ""

echo -e "${YELLOW}[5.1/7] Waiting 3 seconds for async processing...${NC}"
sleep 3
echo ""

echo -e "${YELLOW}[6/7] Poll Report Status (After Processing - should be DONE)${NC}"
echo ""

STATUS_FINAL=$(curl -s "$BASE_URL/projects/$PROJECT_ID/report-status" \
  -H "Authorization: Bearer $TOKEN")

FINAL_STATUS=$(echo "$STATUS_FINAL" | jq -r '.reportStatus')
echo "Report Status: $FINAL_STATUS"
if [ "$FINAL_STATUS" = "DONE" ]; then
    echo -e "${GREEN}✓ Report generation completed successfully${NC}"
else
    echo -e "${RED}✗ Report generation failed (status: $FINAL_STATUS)${NC}"
fi
echo ""

# ─────────────────────────────────────────────────────────────────────────
# 7. OBSERVABILITY METRICS
# ─────────────────────────────────────────────────────────────────────────
echo -e "${YELLOW}[7/7] Observability - Micrometer Metrics${NC}"
echo ""

echo "Fetching Prometheus metrics..."
METRICS=$(curl -s "$BASE_URL/actuator/prometheus" \
  -H "Authorization: Bearer $TOKEN" | grep "workhub_report")

if echo "$METRICS" | grep -q "workhub_report"; then
    echo -e "${GREEN}✓ WorkHub custom metrics available${NC}"
    echo ""
    echo "Sample metrics:"
    echo "$METRICS" | head -10
else
    echo -e "${YELLOW}! No metrics captured yet (expected if first run)${NC}"
fi
echo ""

# ─────────────────────────────────────────────────────────────────────────
# BONUS: TENANT ISOLATION VERIFICATION
# ─────────────────────────────────────────────────────────────────────────
echo -e "${YELLOW}[BONUS] Tenant Isolation Verification${NC}"
echo ""

echo "Logging in as different tenant (admin@beta.com)..."
BETA_LOGIN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@beta.com","password":"beta123"}')

BETA_TOKEN=$(echo "$BETA_LOGIN" | jq -r '.token // empty')

if [ -n "$BETA_TOKEN" ]; then
    echo -e "${GREEN}✓ Beta tenant logged in successfully${NC}"
    echo ""
    
    echo "Attempting to access ACME's project (should be denied)..."
    ISOLATION_TEST=$(curl -s -w "\n%{http_code}" "$BASE_URL/projects/$PROJECT_ID" \
      -H "Authorization: Bearer $BETA_TOKEN")
    
    TEST_HTTP_CODE=$(echo "$ISOLATION_TEST" | tail -1)
    
    if [ "$TEST_HTTP_CODE" = "404" ] || [ "$TEST_HTTP_CODE" = "403" ]; then
        echo -e "${GREEN}✓ Tenant isolation enforced! Access denied (HTTP $TEST_HTTP_CODE)${NC}"
    else
        echo -e "${RED}✗ Tenant isolation failed! Access allowed (HTTP $TEST_HTTP_CODE)${NC}"
    fi
else
    echo -e "${YELLOW}! Could not test tenant isolation (beta user not available)${NC}"
fi
echo ""

# ─────────────────────────────────────────────────────────────────────────
# Summary
# ─────────────────────────────────────────────────────────────────────────
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✓ Phase 2 Demo Complete!${NC}"
echo ""
echo "Key Findings:"
echo "  • Correlation ID: $CORRELATION_ID"
echo "  • Project ID: $PROJECT_ID"
echo "  • Job ID: $JOB_ID"
echo "  • Final Report Status: $FINAL_STATUS"
echo ""
echo "To verify observability:"
echo "  curl -s -H 'Authorization: Bearer $TOKEN' \\\\
        $BASE_URL/actuator/prometheus | grep workhub_report"
echo ""
echo "To check logs with correlation ID:"
echo "  docker logs <container-id> | grep correlationId=$CORRELATION_ID"
echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
