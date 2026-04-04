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
- App: http://localhost:9090
- Postgres: localhost:5432

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
- `repository/` — JPA repositories
- `service/` — Business logic
- `controller/` — REST endpoints
- `tenant/` — Tenant context and isolation
