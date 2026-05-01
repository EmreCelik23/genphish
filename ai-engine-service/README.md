# GenPhish AI Engine Service

`ai-engine-service` is the asynchronous AI generation backend for GenPhish.
It consumes campaign generation jobs from Kafka, produces phishing simulation assets, stores them in MongoDB, and responds back to `campaign-service`.

## What this service does

- Consumes `ai_generation_requests` events from Kafka
- Generates:
  - phishing email subject + HTML body
  - category-aware Next.js landing/page code
- Stores generated template documents in MongoDB
- Publishes `ai_generation_responses` events with `mongoTemplateId`
- Supports category-specific flows:
  - `CREDENTIAL_HARVESTING`: login form -> `/track/submit`
  - `MALWARE_DELIVERY`: download CTA -> `/track/download`
  - `CLICK_ONLY`: no landing page required
  - `OAUTH_CONSENT`: no landing page required
- Supports optional multimodal generation with `referenceImageUrl` for `openai`, `anthropic`, and `gemini`
- If multimodal execution fails at runtime, service logs warning and safely falls back to text-only landing generation
- Exposes API fallback for campaign service cache misses:
  - `GET /api/templates/{template_id}`
  - `POST /api/templates/{template_id}/clone` (creates independent copy for campaign cloning)
- Uses circuit-breaker fallback:
  - strict mode is default: on LLM timeout/error, generation fails (`FAILED`)
  - if request explicitly sets `allowFallbackTemplate=true`, service loads static fallback template from MongoDB `fallback_templates`
  - if DB fallback is missing, uses built-in safe fallback template
- Landing code compatibility:
  - supports both query-style ids (`?c=...`) and dynamic path ids (`/phishing/{campaignId}`)
  - preserves optional `lang` query (`TR|EN`) on submit redirect flow
  - preserves signed tracking params `exp` and `sig` on action posts

## Integration contract with campaign-service

### Consumed event topic

- `ai_generation_requests`

Expected payload (camelCase):

```json
{
  "templateId": "uuid",
  "companyId": "uuid",
  "prompt": "string",
  "targetUrl": "string",
  "referenceImageUrl": "string-or-null",
  "templateCategory": "CREDENTIAL_HARVESTING|CLICK_ONLY|MALWARE_DELIVERY|OAUTH_CONSENT",
  "difficultyLevel": "AMATEUR|PROFESSIONAL (optional, default PROFESSIONAL)",
  "regenerationScope": "ALL|ONLY_EMAIL|ONLY_LANDING_PAGE",
  "existingMongoTemplateId": "optional-string",
  "languageCode": "TR|EN (optional, default TR)",
  "provider": "openai|anthropic|gemini|stub (optional)",
  "model": "optional model override",
  "allowFallbackTemplate": "boolean (optional, default false)"
}
```

### Produced event topic

- `ai_generation_responses`

Produced payload (camelCase):

```json
{
  "templateId": "uuid",
  "mongoTemplateId": "string-or-null",
  "status": "SUCCESS|FAILED",
  "errorMessage": "string-or-null",
  "fallbackUsed": "boolean"
}
```

### Template JSON shape (for fallback endpoint)

`GET /api/templates/{id}` returns:

```json
{
  "subject": "...",
  "bodyHtml": "...",
  "landingPageCode": "..."
}
```

This is intentionally aligned with `campaign-service` `EmailDeliveryProducer` parser expectations.

`POST /api/templates/{id}/clone` request body:

```json
{
  "templateId": "uuid",
  "companyId": "uuid"
}
```

## Tech stack

- FastAPI (async API and lifecycle)
- LangChain + OpenAI (or `stub` provider)
- Motor (async MongoDB)
- aiokafka (async Kafka consumer/producer)

## Project structure

```text
ai-engine-service/
  app/
    api/
    core/
    models/
    services/
    prompts/
  Dockerfile
  requirements.txt
  .env.example
```

## Local run

1. Copy env:

```bash
cp .env.example .env
```

2. Install dependencies:

```bash
python -m pip install -r requirements.txt
```

3. (Optional) set `AI_PROVIDER=openai` and fill `OPENAI_API_KEY`.
4. For API-only local tests without Kafka broker, set `KAFKA_ENABLED=false`.
5. You may use either env names:
   - `MONGODB_URI` or `MONGO_URI`
   - `KAFKA_BOOTSTRAP_SERVERS` or `KAFKA_BROKERS`
6. For multi-provider setup, configure one or more:
   - OpenAI: `OPENAI_API_KEY`
   - Anthropic: `ANTHROPIC_API_KEY`
   - Gemini: `GOOGLE_API_KEY`
7. Configure service auth shared secret with campaign-service:
   - `SERVICE_AUTH_ENABLED=true`
   - `SERVICE_AUTH_TOKEN=...`
   - `SERVICE_TOKEN_HEADER=X-Service-Token`
   - `COMPANY_HEADER=X-Company-Id`

8. Start service:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 5000 --reload
```

## Docker

```bash
docker build -t genphish-ai-engine:prod .
docker run --rm -p 5000:5000 --env-file .env genphish-ai-engine:prod
```

## Endpoints

- `GET /healthz`
- `POST /api/generate` (manual generation trigger)
- `GET /api/templates/{template_id}` (campaign fallback endpoint)
- `POST /api/templates/{template_id}/clone` (duplicate existing template for a new campaign)

Auth for `/api/*` endpoints:

- `Authorization: Bearer <SERVICE_AUTH_TOKEN>` or `X-Service-Token: <SERVICE_AUTH_TOKEN>`
- For company-scoped payloads (`/api/generate`, `/api/templates/{template_id}/clone`), `X-Company-Id` must match payload `companyId`

`POST /api/generate` success response shape:

```json
{
  "templateId": "uuid",
  "mongoTemplateId": "mongo-object-id",
  "status": "SUCCESS",
  "createdAt": "timestamp"
}
```

## Tests

```bash
python -m unittest discover -s tests -v
```

## Provider modes

- `AI_PROVIDER=stub`
  - deterministic local output, no external LLM call
- `AI_PROVIDER=openai`
  - LangChain + OpenAI structured generation
- `AI_PROVIDER=anthropic`
  - LangChain + Claude structured generation
- `AI_PROVIDER=gemini`
  - LangChain + Gemini structured generation

Request-level overrides are also supported with `provider` and `model` fields.

## Language support

- `languageCode=TR` for Turkish output (default)
- `languageCode=EN` for English output
- Alias `language` is accepted in requests

## Difficulty support

- `difficultyLevel=PROFESSIONAL` (default)
- `difficultyLevel=AMATEUR`

## Notes

- `ONLY_EMAIL` and `ONLY_LANDING_PAGE` regeneration scopes are supported when `existingMongoTemplateId` exists.
- Set `KAFKA_ENABLED=false` for local API-only development without Kafka.
- Generation timeout is controlled by `GENERATION_TIMEOUT_SECONDS` (default `15`).
- Strict behavior is default: timeout/error -> `FAILED`. Enable fallback per request with `allowFallbackTemplate=true`.
- Multimodal image-to-code flow uses `referenceImageUrl` for OpenAI/Anthropic/Gemini providers.
- Keep `SERVICE_AUTH_TOKEN` aligned with campaign-service `AI_SERVICE_TOKEN`.
- For production, configure network-level authentication for Kafka and MongoDB.
