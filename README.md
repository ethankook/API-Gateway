# Orchard

## Overview

Orchard is a Spring Boot service platform for authenticated API gateway routing and durable HTTP job scheduling.

Core use cases:

- Route external API traffic to downstream services through a configurable gateway.
- Validate JWT callers and forward trusted principal headers to internal services.
- Apply per-route token-bucket rate limiting.
- Create one-time or recurring HTTP jobs that execute against target URLs.
- Track job status, attempts, retries, and execution history in PostgreSQL.

## Architecture

Orchard is a Maven multi-module project:

- `common`: shared servlet error response helpers.
- `gateway`: edge service that performs request tracing, route matching, JWT authentication, rate limiting, and HTTP proxying.
- `scheduler`: job API and execution engine backed by PostgreSQL and Flyway migrations.

Request flow:

1. Clients call the `gateway` service.
2. Gateway assigns `X-Request-Id`, resolves a configured route, validates JWTs when required, rate-limits the caller, and proxies the request.
3. For internal routes, gateway injects `X-Gateway-Internal-Token` plus authenticated principal headers.
4. Scheduler accepts trusted gateway calls, persists jobs, claims due jobs with `FOR UPDATE SKIP LOCKED`, executes HTTP requests, records job runs, and schedules retries or the next recurring run.

## Features

- Configurable path-prefix gateway routes.
- JWT authentication with issuer validation.
- User and service principals via JWT claims.
- Admin-aware job authorization.
- Internal service token forwarding.
- Per-route or default in-memory rate limiting.
- One-time and recurring cron jobs.
- PostgreSQL persistence with Flyway schema migration.
- Concurrent job execution with bounded thread pools.
- Retry handling with exponential backoff and jitter.
- Stuck-job recovery on scheduler startup.
- SSRF-oriented target URL validation.
- Health and actuator-compatible bypass routes.

## Getting Started

### Prerequisites

- Java 21
- Docker and Docker Compose
- Maven wrapper included as `./mvnw`
- PostgreSQL 16 if not using Docker Compose

### Environment

Create `.env` in the repository root:

```bash
cp .env.example .env
```

Then replace `JWT_SECRET_KEY`, `GATEWAY_INTERNAL_TOKEN`, and `DB_PASSWORD` with deployment-specific secrets. Do not commit `.env`; it is ignored by Git.

Environment variables:

| Variable | Required | Default | Used by | Description |
| --- | --- | --- | --- | --- |
| `JWT_SECRET_KEY` | Yes | None | gateway | HS256 signing secret. Must be at least 32 bytes. |
| `GATEWAY_INTERNAL_TOKEN` | Yes | Empty | gateway, scheduler | Shared token used for gateway-to-scheduler authentication. |
| `SCHEDULER_URL` | No | `http://localhost:9191` | gateway | Scheduler base URL for the default jobs route. |
| `DB_HOST` | No | `localhost` | scheduler | PostgreSQL host. |
| `DB_PORT` | No | `4432` | scheduler, Docker Compose | PostgreSQL port. |
| `DB_NAME` | No | `orchard` | scheduler, Docker Compose | PostgreSQL database name. |
| `DB_USER` | Yes | `localuser` in Docker Compose | scheduler, Docker Compose | PostgreSQL username. |
| `DB_PASSWORD` | Yes | `secret` in Docker Compose | scheduler, Docker Compose | PostgreSQL password. |

### Setup

```bash
docker compose up -d postgres
./mvnw clean package
./startup-services.sh
```

Services start on:

- Gateway: `http://localhost:9090`
- Scheduler: `http://localhost:9191`
- PostgreSQL: `localhost:4432`

Run tests:

```bash
./mvnw test
```

For production, run gateway and scheduler as separate Java services, bind scheduler to a private network, and expose only gateway publicly.

## Configuration

Gateway configuration is in `gateway/src/main/resources/application.properties`.

| Property | Description |
| --- | --- |
| `server.port` | Gateway HTTP port. |
| `auth.jwt.secret` | JWT signing secret, normally sourced from `JWT_SECRET_KEY`. |
| `auth.jwt.issuer` | Required JWT issuer. Defaults to `Orchard`. |
| `gateway.routes[n].routeId` | Stable route identifier for logs and rate-limit keys. |
| `gateway.routes[n].pathPrefix` | Incoming request path prefix. |
| `gateway.routes[n].downstreamUrl` | Downstream base URL that receives the matched path tail. |
| `gateway.routes[n].requiresAuth` | Whether a valid bearer JWT is required. |
| `gateway.routes[n].requiresInternalToken` | Whether gateway injects `X-Gateway-Internal-Token`. |
| `gateway.routes[n].methods` | Allowed HTTP methods. Empty means all methods. |
| `gateway.routes[n].rateLimitCapacity` | Optional route token bucket capacity. |
| `gateway.routes[n].rateLimitRefillRate` | Optional route token refill rate per second. |
| `gateway.connect-timeout-ms` | Downstream connection timeout. |
| `gateway.read-timeout-ms` | Downstream response timeout. |
| `rate-limiting.default-capacity` | Default token bucket capacity. |
| `rate-limiting.default-refill-rate` | Default refill rate per second. |

Scheduler configuration is in `scheduler/src/main/resources/application.properties`.

| Property | Description |
| --- | --- |
| `spring.datasource.*` | PostgreSQL connection settings. |
| `spring.jpa.open-in-view` | Disabled by default for REST API request isolation. |
| `spring.flyway.*` | Schema migration settings for the `scheduler` schema. |
| `scheduler.require-authenticated-caller` | Requires gateway token and principal headers when true. |
| `scheduler.gateway-internal-token` | Shared gateway token expected in `X-Gateway-Internal-Token`. |
| `scheduler.poll-interval-ms` | Delay between scheduler polling cycles. |
| `scheduler.executor-core-pool-size` | Core job execution threads. |
| `scheduler.executor-max-pool-size` | Maximum job execution threads. |
| `scheduler.batch-size` | Number of due jobs claimed per poll. |
| `scheduler.stuck-job-threshold-minutes` | Age after which `RUNNING` jobs are reset on startup. |
| `scheduler.http-client.*` | Callback HTTP client timeouts and connection pool size. |

## Usage

### Integrate a Downstream Service

Add a gateway route:

```properties
gateway.routes[1].routeId=orders-service
gateway.routes[1].pathPrefix=/api/v1/orders
gateway.routes[1].downstreamUrl=http://orders-service:8080/api/v1/orders
gateway.routes[1].requiresAuth=true
gateway.routes[1].requiresInternalToken=true
gateway.routes[1].methods[0]=GET
gateway.routes[1].methods[1]=POST
```

Clients call the gateway:

```bash
curl -H "Authorization: Bearer $JWT" \
  http://localhost:9090/api/v1/orders/123
```

The downstream service receives:

- `X-Request-Id`
- `X-Forwarded-For`
- `X-Forwarded-Host`
- `X-Authenticated-Principal-Type`
- `X-Authenticated-Principal-Id`
- `X-Authenticated-Is-Admin`
- `X-Gateway-Internal-Token` when enabled for the route

### Create a Scheduled Job

```bash
curl -X POST http://localhost:9090/api/v1/jobs/ \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "send-webhook",
    "type": "ONE_TIME",
    "fireAt": "2030-01-01T00:00:00Z",
    "targetUrl": "https://example.com/webhooks/orchard",
    "httpMethod": "POST",
    "payload": {"event": "scheduled"},
    "maxAttempts": 3,
    "retryBackoffSeconds": 30
  }'
```

Scheduler callback requests include:

- `X-Scheduler-Job-Id`
- `X-Scheduler-Attempt`
- `X-Scheduler-Fire-Time`

Execution behavior:

- `2xx`: success.
- `4xx`: non-retryable failure.
- `5xx` or network timeout: retry until `maxAttempts`.
- Recurring jobs use the next cron fire time after a successful or terminal failed occurrence.

## Authentication / Authorization

Clients authenticate to gateway with:

```http
Authorization: Bearer <jwt>
```

Gateway validates:

- HMAC signature using `auth.jwt.secret`.
- Issuer matching `auth.jwt.issuer`.
- Exactly one principal claim: `userId` or `serviceId`.
- Optional `admin` boolean claim.

Example JWT payload:

```json
{
  "iss": "Orchard",
  "userId": 42,
  "admin": false,
  "exp": 1893456000
}
```

Scheduler authorization:

- Scheduler should normally be called through gateway.
- Direct scheduler calls must include a valid `X-Gateway-Internal-Token`.
- Scheduler requires `X-Authenticated-Principal-Type` and `X-Authenticated-Principal-Id` when `scheduler.require-authenticated-caller=true`.
- Non-admin callers can only read, list, and delete jobs owned by their principal.
- Admin callers can list and access jobs across owners.

## API Reference

Gateway:

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/health` | Gateway health check. |
| Any configured | `gateway.routes[n].pathPrefix/**` | Proxies to the configured downstream URL. |

Scheduler jobs API, normally reached through gateway at `/api/v1/jobs`:

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/v1/jobs/` | Create a one-time or recurring job. |
| `GET` | `/api/v1/jobs` | List jobs visible to the caller. Optional filters: `ownerType`, `ownerId`, `status`. |
| `GET` | `/api/v1/jobs/{id}` | Get one job. |
| `GET` | `/api/v1/jobs/{id}/runs` | List execution attempts for a job. |
| `DELETE` | `/api/v1/jobs/{id}` | Delete a job and cascaded run history. |

Create job request:

| Field | Required | Description |
| --- | --- | --- |
| `name` | Yes | 1-200 character job name. |
| `type` | Yes | `ONE_TIME` or `RECURRING`. |
| `fireAt` | For `ONE_TIME` | Future ISO-8601 instant. |
| `cronExpression` | For `RECURRING` | Spring cron expression. |
| `targetUrl` | Yes | Public `http` or `https` URL. |
| `httpMethod` | Yes | `GET`, `POST`, `PUT`, `DELETE`, or `PATCH`. |
| `payload` | No | JSON body for callback requests. |
| `maxAttempts` | Yes | `1` to `20`. |
| `retryBackoffSeconds` | Yes | `1` to `3600`. |

## Extending / Customization

- Add gateway routes in `gateway/src/main/resources/application.properties` or external Spring configuration.
- Customize gateway authentication in `gateway/middleware/Authorization`.
- Customize rate limiting in `gateway/middleware/RateLimiting`.
- Add scheduler APIs in `scheduler/controller` and job behavior in `scheduler/service`.
- Change retry behavior in `SchedulerRetryPolicy`.
- Adjust target URL validation in `TargetUrlGuard`.
- Add database changes through Flyway migrations in `scheduler/src/main/resources/db/migration`.
- Reuse shared filter error formatting from `common`.

## Notes / Limitations

- Gateway rate limiting is in-memory; use an external store before running multiple gateway instances.
- Scheduler uses PostgreSQL locking and can run multiple instances against the same database.
- Scheduler target URLs must resolve to public addresses; localhost, private networks, link-local, multicast, and similar ranges are blocked.
- The default gateway route only exposes the scheduler jobs API.
- `POST /api/v1/jobs/` includes a trailing slash because the scheduler controller maps creation to `/`.
- JWT issuance is not included; consuming systems must mint compatible tokens.
- Generate unique production values for `JWT_SECRET_KEY`, `GATEWAY_INTERNAL_TOKEN`, and database credentials.
