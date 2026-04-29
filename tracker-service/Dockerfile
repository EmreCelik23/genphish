FROM golang:1.22-alpine AS builder

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
    go build -trimpath -buildvcs=false -ldflags="-s -w" -o /out/tracker-api ./cmd/api

FROM gcr.io/distroless/static-debian12:nonroot

WORKDIR /app
COPY --from=builder /out/tracker-api /app/tracker-api

# Keep runtime user explicit for defense-in-depth.
USER 65532:65532

EXPOSE 8081

ENTRYPOINT ["/app/tracker-api"]
