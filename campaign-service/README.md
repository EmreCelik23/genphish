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
- Category-aware phishing template generation:
  - `CREDENTIAL_HARVESTING`, `CLICK_ONLY`, `MALWARE_DELIVERY`, `OAUTH_CONSENT`
- Optional reference image upload for multimodal landing-page generation (`referenceImageUrl`)
- Secure reference-image storage modes:
  - local filesystem (default)
  - S3/MinIO object storage with presigned URLs
- Scheduled campaign launch automation
- Tracking event ingestion and risk-score updates
- Dashboard analytics for company/department/campaign-level insights
- HMAC-signed tracking links and signed OAuth `state` payload generation

## API surface

Base URL: `http://localhost:8080`

Auth requirement for API endpoints:

- Send `Authorization: Bearer <APP_API_TOKEN>` (or `X-Service-Token`).
- For company-scoped routes (`/api/v1/companies/{companyId}/...`), send `X-Company-Id: {companyId}`.

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
| `GET` | `/api/v1/companies/{companyId}/campaigns/ai-library` | List reusable AI-generated campaigns |
| `GET` | `/api/v1/companies/{companyId}/campaigns/ai-library/{campaignId}/preview` | Fetch AI template preview (`subject`, `bodyHtml`, `landingPageCode`) |
| `GET` | `/api/v1/companies/{companyId}/campaigns/{campaignId}` | Get campaign by id |
| `POST` | `/api/v1/companies/{companyId}/campaigns/{campaignId}/start` | Start campaign immediately |
| `POST` | `/api/v1/companies/{companyId}/campaigns/{campaignId}/clone` | Clone existing campaign (static content reused, AI content copied as independent template snapshot) |
| `POST` | `/api/v1/companies/{companyId}/campaigns/{campaignId}/regenerate` | Regenerate AI content (`ALL`, `ONLY_EMAIL`, `ONLY_LANDING_PAGE`) |
| `DELETE` | `/api/v1/companies/{companyId}/campaigns/{campaignId}` | Soft delete campaign |

### Template

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/companies/{companyId}/templates/generate` | Create AI template generation job |
| `POST` | `/api/v1/companies/{companyId}/templates/{templateId}/regenerate` | Regenerate existing AI template |
| `PUT` | `/api/v1/companies/{companyId}/templates/{templateId}` | Manual template update |
| `GET` | `/api/v1/companies/{companyId}/templates` | List active templates (global + tenant) |
| `GET` | `/api/v1/companies/{companyId}/templates/{templateId}` | Get template detail |
| `DELETE` | `/api/v1/companies/{companyId}/templates/{templateId}` | Soft delete template |
| `POST` | `/api/v1/companies/{companyId}/templates/upload-reference` | Upload optional reference image, returns `referenceImageUrl` |
| `GET` | `/api/v1/reference-images/{fileName}` | Serve uploaded reference image |

AI template payload fields also support:

- `difficultyLevel`: `AMATEUR` or `PROFESSIONAL` (default `PROFESSIONAL`)
- `languageCode`: `TR` or `EN` (default `TR`)
- `templateCategory`: `CREDENTIAL_HARVESTING`, `CLICK_ONLY`, `MALWARE_DELIVERY`, `OAUTH_CONSENT`
- `referenceImageUrl`: optional URL for multimodal image-to-code generation
- `aiProvider`: `openai`, `anthropic`, `gemini`, `stub`
- `aiModel`: provider-specific model override (optional)
- `allowFallbackTemplate`: `true|false` (default `false`). `false` means strict mode: AI timeout/error returns `FAILED` instead of silently saving fallback content.
- `targetUrl`: optional for clone/theme context, required in practice for `OAUTH_CONSENT` campaign launch.

Email tracking links generated by this service also include:

- `lang=TR|EN` (derived from campaign language) so tracker/front-end redirect flow preserves locale.
- `tc=<TemplateCategory>` for click-flow routing in tracker (`CLICK_ONLY` direct-awareness flow).
- `exp` + `sig` (HMAC signature) for tracker-side tamper validation.
- OAuth consent templates now generate signed state payload:
  - Base64 payload + HMAC signature
  - Includes `exp` and `nonce` for short-lived anti-replay flow in tracker.

Scheduling and safety behavior:

- Scheduled campaigns are launched by scheduler when due.
- Template `fallbackContentUsed` is persisted for reporting/audit; launch behavior is controlled by campaign lifecycle endpoints.

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
| `tracking_events` | Tracker Service -> Campaign Service | Consume open/click/submit/download/consent telemetry |

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
| `APP_PUBLIC_BASE_URL` | Public base URL used in generated `referenceImageUrl` values | `http://localhost:8080` |
| `REFERENCE_IMAGE_DIR` | Local directory for uploaded reference images | `./uploads/reference-images` |
| `REFERENCE_IMAGE_STORAGE` | `local` or `s3` | `local` |
| `REFERENCE_IMAGE_RETENTION_DAYS` | Local file retention window | `30` |
| `REFERENCE_IMAGE_S3_*` | S3/MinIO settings (`bucket`, `endpoint`, `region`, credentials, presign ttl) | see `.env.example` |
| `APP_API_TOKEN` | API bearer/service token for campaign endpoints | `genphish-dev-token` |
| `AI_SERVICE_TOKEN` | Token used by campaign -> ai-engine HTTP calls | `genphish-internal-token` |
| `SERVICE_TOKEN_HEADER` | Alternate auth header key | `X-Service-Token` |
| `COMPANY_HEADER` | Tenant header key | `X-Company-Id` |
| `TRACKING_SIGNATURE_SECRET` | HMAC secret for signed tracker links | `genphish-dev-tracking-secret` |
| `TRACKING_LINK_TTL_SECONDS` | Signed tracker link lifetime | `604800` |
| `OAUTH_STATE_HMAC_SECRET` | HMAC secret for OAuth state signing | `genphish-dev-tracking-secret` |
| `OAUTH_STATE_TTL_SECONDS` | OAuth state lifetime (seconds) | `600` |
| `CAMPAIGN_HIGH_RISK_THRESHOLD` | High-risk employee threshold | `70.0` |
| `CAMPAIGN_CHECK_RATE_MS` | Scheduler interval for due campaigns | `300000` |
| `CORS_ALLOWED_ORIGINS` | Allowed frontend origins | `https://app.example.com` |

Schema update required for existing deployments:

```sql
ALTER TABLE phishing_templates
  ADD COLUMN IF NOT EXISTS template_category VARCHAR(64) NOT NULL DEFAULT 'CREDENTIAL_HARVESTING';

ALTER TABLE phishing_templates
  ADD COLUMN IF NOT EXISTS reference_image_url VARCHAR(1024);
```

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
- API endpoints enforce token auth (`Authorization`/`X-Service-Token`) and tenant-header checks on company-scoped paths.
- Tracking links and OAuth state payloads are HMAC-signed to prevent tampering/spoofed telemetry.
- For production:
  - Rotate `APP_API_TOKEN`, `AI_SERVICE_TOKEN`, `TRACKING_SIGNATURE_SECRET`, `OAUTH_STATE_HMAC_SECRET`.
  - Prefer S3/MinIO storage mode and bucket lifecycle rules for reference-image retention.
  - Keep gateway/service-mesh controls (mTLS, network policies, rate limits) in front of services.
- Tracking-based risk updates are idempotent for duplicate events.

## Position in GenPhish

In short:

1. Create company + employee population
2. Launch AI or static phishing campaign
3. Dispatch delivery jobs through Kafka
4. Ingest telemetry from tracker
5. Continuously compute risk and dashboard insights
