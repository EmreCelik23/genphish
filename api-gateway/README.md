# GenPhish API Gateway

This module adds a single external entrypoint for backend traffic.

## Why this exists

- Centralize incoming HTTP to one port
- Keep backend service ports internal
- Add lightweight gateway-layer controls (header presence + rate limiting)

## Routes

- `/api/*` -> `campaign-service:8080`
- `/track/*` -> `tracker-service:8081`
- `/oauth/callback` -> `tracker-service:8081`
- `/healthz` -> gateway liveness

## Security behavior

- `/api/*` requires at least one of:
  - `Authorization` header
  - `X-Service-Token` header
- Gateway forwards auth and tenant headers:
  - `Authorization`
  - `X-Service-Token`
  - `X-Internal-Token`
  - `X-Company-Id`
  - `X-Tenant-Id`
- `/actuator/*` is blocked with `403` at gateway level.

Important:
- Final auth/token validation is still enforced in backend services.
- JWT bearer validation (HS256) is enforced by `campaign-service` when `JWT_AUTH_ENABLED=true`.
- Tracker endpoints remain public by design for simulation links, but signed query validation stays active in tracker service.

## Run with Compose

1. Start infra (root compose):

```bash
docker compose up -d
```

2. Start backend apps + gateway:

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d --build
```

3. Check gateway:

```bash
curl -s http://localhost:8088/healthz
```

4. Use gateway as external base URL:

- Campaign API: `http://localhost:8088/api/...`
- Tracking links: `http://localhost:8088/track/...`

## Config knobs

- `GATEWAY_PORT` (default `8088`)
- `TRACKER_BASE_URL_PUBLIC` (recommended `http://localhost:8088`)
- `APP_PUBLIC_BASE_URL` (recommended `http://localhost:8088`)
- `INTERNAL_SERVICE_TOKEN` (shared campaign <-> ai-engine token)
- JWT settings are configured in `campaign-service/.env` (`JWT_AUTH_*` variables).
