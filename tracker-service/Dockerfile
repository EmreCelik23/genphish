FROM golang:1.23-alpine@sha256:383395b794dffa5b53012a212365d40c8e37109a626ca30d6151c8348d380b5f AS builder

WORKDIR /src

# Copy module files first for better dependency-layer caching.
COPY go.* ./
RUN go mod download

# Copy only build-relevant source to avoid invalidating cache on unrelated changes.
COPY cmd ./cmd
COPY internal ./internal

ARG TARGETOS=linux
ARG TARGETARCH=amd64
RUN CGO_ENABLED=0 GOOS=${TARGETOS} GOARCH=${TARGETARCH} \
    go build -trimpath -buildvcs=false -ldflags="-s -w" -o /out/tracker-api ./cmd/api \
    && CGO_ENABLED=0 GOOS=${TARGETOS} GOARCH=${TARGETARCH} \
    go build -trimpath -buildvcs=false -ldflags="-s -w" -o /out/healthcheck ./cmd/healthcheck

FROM gcr.io/distroless/static-debian12:nonroot@sha256:a9329520abc449e3b14d5bc3a6ffae065bdde0f02667fa10880c49b35c109fd1

WORKDIR /app
COPY --from=builder /out/tracker-api /app/tracker-api
COPY --from=builder /out/healthcheck /app/healthcheck

# Keep runtime user explicit for defense-in-depth.
USER 65532:65532

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD ["/app/healthcheck", "http://127.0.0.1:8081/healthz"]

ENTRYPOINT ["/app/tracker-api"]
