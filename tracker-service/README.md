# GenPhish Tracker Service

`tracker-service` is the telemetry edge for GenPhish.
It captures phishing interaction signals in real time and streams them to Kafka for downstream analytics and risk scoring.

## Why this service exists

Tracking endpoints must be:

- very fast,
- resilient under burst traffic,
- privacy-conscious by default.

This service is intentionally narrow and optimized for those goals.

## Core capabilities

- Pixel-open tracking (`EMAIL_OPENED`)
- Link-click tracking (`LINK_CLICKED`)
- Credential-submit event tracking (`CREDENTIALS_SUBMITTED`)
- Kafka event publishing with low-latency batching
- Health endpoint for orchestration probes
- Graceful shutdown with timeout-based draining

## Privacy by design

`/track/submit` does not parse, store, or forward credential fields.
Only telemetry metadata (`campaignId`, `employeeId`, `companyId`, `eventType`, `timestamp`) is emitted.

## API surface

Base URL: `http://localhost:8081`

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/healthz` | Liveness/readiness check |
| `GET` | `/track/open` | Publishes `EMAIL_OPENED`, returns transparent `1x1 GIF` |
| `GET` | `/track/click` | Publishes `LINK_CLICKED`, redirects to landing page |
| `POST` | `/track/submit` | Publishes `CREDENTIALS_SUBMITTED`, redirects to awareness page |

## Tracking ID query parameters

Accepted aliases:

- Campaign id: `c`, `campaign_id`, `campaignId`
- Employee id: `e`, `employee_id`, `employeeId`, `user_id`, `userId`
- Company id: `co`, `company_id`, `companyId`

If ids are missing or invalid, the service still returns safe fallback responses (pixel or redirect) and avoids crashing request flow.

## Event contract

Kafka topic: `tracking_events` (configurable via `KAFKA_TOPIC`)

Payload:

```json
{
  "campaignId": "uuid",
  "employeeId": "uuid",
  "companyId": "uuid",
  "eventType": "EMAIL_OPENED | LINK_CLICKED | CREDENTIALS_SUBMITTED",
  "timestamp": "2026-04-29T10:00:00Z"
}
```

## Configuration

Create local config:

```bash
cp .env.example .env
```

Key variables:

| Variable | Description | Default |
|---|---|---|
| `PORT` | HTTP port | `8081` |
| `GIN_MODE` | Gin mode (`release`, `debug`) | `release` |
| `PUBLISH_TIMEOUT_MS` | Kafka publish timeout per request | `300` |
| `KAFKA_BROKERS` | Kafka broker list | `kafka-1:9092,kafka-2:9092` |
| `KAFKA_TOPIC` | Tracking topic | `tracking_events` |
| `KAFKA_BATCH_TIMEOUT_MS` | Kafka writer batch timeout | `5` |
| `KAFKA_BATCH_SIZE` | Kafka writer batch size | `200` |
| `KAFKA_ASYNC` | Async Kafka write mode | `true` |
| `LANDING_PAGE_URL` | Redirect target for click endpoint (`{campaignId}` placeholder supported) | `http://localhost:3000/phishing` |
| `AWARENESS_PAGE_URL` | Redirect target for submit endpoint | `http://localhost:3000/awareness` |

Example dynamic route config:

```env
LANDING_PAGE_URL=https://app.example.com/phishing/{campaignId}
```

## Local development

### Prerequisites

- Go `1.23+`
- Kafka cluster reachable from service

Run:

```bash
cd tracker-service
cp .env.example .env
go mod tidy
go run ./cmd/api
```

If Kafka is running on your host, set:

- `KAFKA_BROKERS=localhost:9092`

Run tests:

```bash
go test ./...
```

## Docker (production-oriented)

The Dockerfile is optimized for production:

- multi-stage build
- static binary (`CGO_ENABLED=0`)
- distroless runtime
- explicit non-root user

Build:

```bash
docker build -t genphish-tracker-service:prod .
```

Run:

```bash
docker run --rm -p 8081:8081 --env-file .env genphish-tracker-service:prod
```

Health check example:

```bash
curl -s http://localhost:8081/healthz
```

## Position in GenPhish

1. Email links/pixels hit tracker endpoints
2. Service validates ids and emits event to Kafka
3. `campaign-service` consumes `tracking_events`
4. Risk scores and campaign analytics are updated
