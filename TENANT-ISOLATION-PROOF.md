# Tenant Isolation Proof

## Approach
Shared database with `tenant_id` column on every entity.
Every repository query filters by `tenantId` extracted from JWT via TenantContext.

## Test Setup
- Tenant A: Acme Corp (admin@acme.com)
- Tenant B: Beta Inc (admin@beta.com)
- Data created under Tenant A: 3 projects, 1 task

---

## Test 1 — Cross-Tenant Read (GET /projects/{id})

**Action:** Tenant B attempts to read Tenant A's project by ID.

**Request:**

GET /projects/7554bbed-19b0-49c3-a797-41da233f27e7
Authorization: Bearer <Tenant B JWT>

**Expected:** 404 Not Found
**Result:** 404 Not Found 

```json
{"error": "Project not found", "status": 404}
```

---

## Test 2 — Cross-Tenant List (GET /projects)

**Action:** Tenant B attempts to list all projects.

**Request:**

GET /projects
Authorization: Bearer <Tenant B JWT>

**Expected:** Empty array []
**Result:** Empty array [] 

```json
[]
```

---

## Test 3 — Cross-Tenant Update (PATCH /tasks/{id})

**Action:** Tenant B attempts to update Tenant A's task.

**Request:**

PATCH /tasks/11d4e08a-9214-433e-aa75-db6a6a1e31b9
Authorization: Bearer <Tenant B JWT>
{"status": "DONE"}

**Expected:** 404 Not Found
**Result:** 404 Not Found 

```json
{"error": "Task not found", "status": 404}
```

---

## Why 404 and not 403?

Returning 404 instead of 403 for cross-tenant access is intentional.
Returning 403 would confirm the resource exists, which leaks information.
Returning 404 reveals nothing about whether the resource exists at all,
providing stronger security through obscurity at the data layer.

---

## Repository Isolation Evidence

Every repository method includes mandatory tenantId filtering:

- `findAllByTenantId(tenantId)` — list queries
- `findByProjectIdAndTenantId(projectId, tenantId)` — single read
- `findByTaskIdAndTenantId(taskId, tenantId)` — single read/update
- `findAllByProjectIdAndTenantId(projectId, tenantId)` — task list

No query can execute without a tenantId filter.
TenantContext throws TenantContextMissingException if tenantId is null,
preventing any accidental unscoped query from reaching the database.