# GenPhish Campaign Service

`campaign-service` is the orchestration and analytics backbone of GenPhish.
It manages companies, employees, phishing campaigns, AI generation workflows, and campaign intelligence dashboards.

## Why this service exists

Security teams need one place to:

- model organizations and employee populations,
- launch phishing simulations with flexible targeting,
- integrate AI-generated phishing content safely,
- measure behavioral risk over time.

This service does exactly that, with an event-driven architecture that scales across services.

## Core capabilities

- Multi-tenant company and employee management
- Bulk employee import (`CSV` and `XLSX`)
- Campaign lifecycle management:
  - `DRAFT`, `GENERATING`, `SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `FAILED`
- Flexible targeting:
  - `ALL_COMPANY`, `DEPARTMENT`, `INDIVIDUAL`, `HIGH_RISK`
- AI campaign generation and partial regeneration
- Scheduled campaign launch automation
- Tracking event ingestion and risk-score updates
- Dashboard analytics for company/department/campaign-level insights

## API surface

Base URL: `http://localhost:8080`

### Company

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/companies` | Create a company |
| `GET` | `/api/v1/companies` | List active companies |
| `GET` | `/api/v1/companies/{companyId}` | Get company by id |

### Employee

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/companies/{companyId}/employees/import` | Bulk import employees from `CSV/XLSX` |
| `POST` | `/api/v1/companies/{companyId}/employees` | Create single employee |
| `PUT` | `/api/v1/companies/{companyId}/employees/{employeeId}` | Update employee |
| `GET` | `/api/v1/companies/{companyId}/employees` | List employees (`department` / `riskThreshold` optional query filters) |
| `GET` | `/api/v1/companies/{companyId}/employees/{employeeId}/risk-profile` | Employee risk profile |
| `DELETE` | `/api/v1/companies/{companyId}/employees/{employeeId}` | Soft deactivate employee |

### Campaign

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/companies/{companyId}/campaigns` | Create campaign (AI or static template mode) |
| `GET` | `/api/v1/companies/{companyId}/campaigns` | List campaigns |
| `GET` | `/api/v1/companies/{companyId}/campaigns/{campaignId}` | Get campaign by id |
| `POST` | `/api/v1/companies/{companyId}/campaigns/{campaignId}/start` | Start campaign immediately |
| `POST` | `/api/v1/companies/{companyId}/campaigns/{campaignId}/regenerate` | Regenerate AI content (`ALL`, `ONLY_EMAIL`, `ONLY_LANDING_PAGE`) |
| `DELETE` | `/api/v1/companies/{companyId}/campaigns/{campaignId}` | Soft delete campaign |

### Analytics

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/companies/{companyId}/analytics/dashboard` | Company dashboard metrics |

## Event contracts (Kafka)

| Topic | Direction | Purpose |
|---|---|---|
| `ai_generation_requests` | Campaign Service -> AI Engine | Request AI email/landing content generation |
| `ai_generation_responses` | AI Engine -> Campaign Service | Receive AI generation result + template reference |
| `email_delivery_queue` | Campaign Service -> Delivery Worker | Push personalized email delivery jobs |
| `tracking_events` | Tracker Service -> Campaign Service | Consume open/click/submit telemetry |

## Configuration

The service loads env values from `.env` via:

- `spring.config.import=optional:file:./.env[.properties]`

Start by copying:

```bash
cp .env.example .env
```

Key variables:

| Variable | Description | Default |
|---|---|---|
| `SERVER_PORT` | HTTP port | `8080` |
| `POSTGRES_*` | PostgreSQL connection | see `.env.example` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | `kafka-1:9092,kafka-2:9092` |
| `REDIS_HOST` / `REDIS_PORT` | Redis cache endpoint | `redis` / `6379` |
| `TRACKER_BASE_URL` | Tracker link/pixel base URL | `http://tracker-service:8081` |
| `PYTHON_SERVICE_BASE_URL` | AI engine HTTP fallback endpoint | `http://ai-engine-service:5000` |
| `CAMPAIGN_HIGH_RISK_THRESHOLD` | High-risk employee threshold | `70.0` |
| `CAMPAIGN_CHECK_RATE_MS` | Scheduler interval for due campaigns | `300000` |
| `CORS_ALLOWED_ORIGINS` | Allowed frontend origins | `https://app.example.com` |

## Local development

### Prerequisites

- Java `21`
- Maven `3.9+`
- PostgreSQL, Kafka, Redis running

You can start infra from project root:

```bash
docker compose up -d postgres redis zookeeper kafka
```

Run the service:

```bash
cd campaign-service
cp .env.example .env
mvn spring-boot:run
```

If you run the app directly on your host machine (not inside Docker network), update `.env` hostnames like:

- `POSTGRES_HOST=localhost`
- `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`
- `REDIS_HOST=localhost`
- `TRACKER_BASE_URL=http://localhost:8081`
- `PYTHON_SERVICE_BASE_URL=http://localhost:5000`

Run tests:

```bash
mvn test
```

## Docker (production-oriented)

This repository includes a multi-stage, layered, distroless Dockerfile:

- Maven builder stage
- Spring Boot layered extraction (`jarmode=tools`)
- Distroless Java runtime (`nonroot`)

Build:

```bash
docker build -t genphish-campaign-service:prod .
```

Run:

```bash
docker run --rm -p 8080:8080 --env-file .env genphish-campaign-service:prod
```

## Security and production notes

- Runtime image uses distroless + non-root user model.
- CORS is enabled for `/api/**` using `CORS_ALLOWED_ORIGINS`.
- This service currently does not implement authentication/authorization by itself.
  - Recommended: place behind API Gateway / service mesh auth (JWT, mTLS, RBAC).
- Tracking-based risk updates are idempotent for duplicate events.

## Position in GenPhish

In short:

1. Create company + employee population
2. Launch AI or static phishing campaign
3. Dispatch delivery jobs through Kafka
4. Ingest telemetry from tracker
5. Continuously compute risk and dashboard insights
