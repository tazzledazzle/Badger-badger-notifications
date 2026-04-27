# Badger-badger-notifications

Kotlin JVM implementation of the multi-channel notification platform described in [ARCHITECTURE.md](ARCHITECTURE.md): a **gateway** enqueues work to **Redis Streams**, **workers** hydrate templates, respect **user preferences / DND**, deliver over **Email / SMS / Push** (real providers when configured, console stubs otherwise), persist **audit rows** in **PostgreSQL**, support **scheduled sends**, **retries / DLQ / fallback channel**, **per-tenant rate limits**, and **Prometheus** metrics.

## Modules

| Module | Role |
|--------|------|
| `shared` | Domain types and API DTOs (`Channel`, `NotifyRequest`, `OutboundDeliveryJob`, …). |
| `broker-api` | Broker port types (`BrokerPublisher`, `BrokerConsumer`, `NotificationStream`). |
| `broker-redis` | Redis Streams implementation. |
| `persistence` | Flyway migrations + JDBC repositories (events, templates, preferences, scheduled jobs). |
| `channels` | `ChannelSender` implementations (console, SMTP, Twilio, optional FCM HTTP v1). |
| `gateway` | Ktor HTTP API (`/v1/...`), API key auth, rate limit, `/metrics`. |
| `worker` | Stream consumer, delivery pipeline, scheduler tick, `/metrics` on port 9404. |

## Prerequisites

- JDK 17+
- PostgreSQL 16+ and Redis 7+ (or use Docker Compose below)

## Local run (Gradle)

Terminal 1 — Postgres & Redis (or point `JDBC_URL` / `REDIS_URL` at your own instances):

```bash
docker compose up -d postgres redis
```

Apply migrations and start processes:

```bash
export JDBC_URL=jdbc:postgresql://localhost:5432/badger
export JDBC_USER=badger
export JDBC_PASSWORD=badger
export REDIS_URL=redis://localhost:6379
export GATEWAY_API_KEY=dev-key

./gradlew :gateway:run &
./gradlew :worker:run &
```

Health: `curl -s http://localhost:8080/health`

## Docker Compose (full stack)

Builds gateway and worker images with the Gradle wrapper inside Docker, then runs Postgres, Redis, gateway, and worker.

```bash
docker compose up --build
```

- Gateway: `http://localhost:8080` (metrics: `/metrics`)
- Worker metrics: `http://localhost:9404/metrics`

Default API key: `GATEWAY_API_KEY=dev-key` (set in compose for gateway).

## API (selected)

All `/v1/*` routes require header: `X-API-Key: <GATEWAY_API_KEY>`.

- `POST /v1/notify` — body `NotifyRequest` (`tenantId`, `userId`, `templateId`, `channel`, `variables`, optional `idempotencyKey`, `fallbackChannel`, …). Optional header `X-Idempotency-Key`.
- `GET /v1/events/{id}` — delivery row (debug).
- `POST /v1/admin/templates` — create template + version 1.
- `GET /v1/admin/templates?tenantId=` — list.
- `GET /v1/admin/templates/{id}?tenantId=` — fetch.
- `POST /v1/admin/templates/{id}/versions` — new version (optional A/B `variantTag` on create/version requests via template tables).
- `POST /v1/preferences` — opt-in flags + DND window (`dndStart` / `dndEnd` as `HH:mm`, `timezone` IANA id).
- `POST /v1/schedule` — `ScheduleNotifyRequest` with `runAtIso` (instant ISO-8601).

## Provider environment variables

| Channel | When active | Variables |
|---------|----------------|------------|
| Email | `SMTP_HOST` set | `variables.to`, `variables.subject` |
| SMS | `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER` | `variables.to` (E.164) |
| Push | `FCM_PROJECT_ID` + `FCM_ACCESS_TOKEN` (short-lived OAuth bearer) | `variables.deviceToken` or `variables.to` |

If not configured, channels log to stdout (console stub).

## Roadmap vs ARCHITECTURE.md

| ARCH area | Status in this repo |
|-----------|---------------------|
| Gateway, broker, workers, delivery store | Implemented (Redis Streams + Postgres). |
| Templates & preferences | CRUD + worker enforcement. |
| Retries, DLQ, fallback, rate limit | Implemented (configurable `WORKER_MAX_TRIES`). |
| Scheduler | DB-backed `scheduled_jobs` + worker poller. |
| Metrics | Prometheus scrape on gateway and worker. |
| Kafka/SQS, multi-region, full compliance UI | Out of scope for this reference implementation; interfaces allow swapping broker. |

## Legacy spike

The old JGit “forward merge email” experiment is documented only in [docs/legacy-forward-merge-spike.md](docs/legacy-forward-merge-spike.md).

## Build

```bash
./gradlew build
```
