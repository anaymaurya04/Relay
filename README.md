# Relay

> **A durable execution and reliability layer for business-critical API operations.**

Relay sits between an application and the external services it depends on. Its job is the failure case where **the request fails, but the outcome of the operation is unknown**.

A timeout, a dropped connection, or a crashed worker should not silently lose an operation or create an accidental duplicate.

**Status:** Phase 3 of 5 — operations are created idempotently and executed over real HTTP with recorded outcomes. Retry scheduling and the demo package are next.

---

## The Problem

Consider a payment:

```text
Application
     │
     │ Charge ₹5,000
     ▼
  External
  Service
     │
     │ ✓ Payment processed
     ▼
     X Response lost
```

The application sees a timeout. But what actually happened?

- The request may never have reached the service.
- The service may still be processing it.
- The operation may have succeeded.
- The operation may have succeeded and the response was lost.

Simply retrying can create a **duplicate operation**. Relay is built to handle that ambiguity.

---

## Core Principle

> **Network outcome and business outcome are not necessarily the same thing.**

A `200` does not prove the business operation finished, and a timeout does not prove it failed. Relay records what it actually observed and refuses to pretend it knows more.

---

## What Works Today

| Method | Path | Purpose |
| ------ | ---- | ------- |
| `POST` | `/operations` | Create an operation. Requires `Caller-Id` and `Idempotency-Key` headers. |
| `GET` | `/operations/{id}` | Read one operation. |
| `GET` | `/operations/{id}/attempts` | Read the attempt history for an operation. |
| `POST` | `/operations/claim-next` | Debug endpoint. Claims one pending operation. Not part of the public API. |

### Create an operation

```http
POST /operations
Caller-Id: demo-caller
Idempotency-Key: payment-42
Content-Type: application/json

{
  "targetUrl": "http://localhost:9090/ok",
  "httpMethod": "POST",
  "requestBody": "{\"event\":\"payment.succeeded\"}"
}
```

```json
{
  "id": "ba2eceb4-1743-4c42-8180-35316238eb18",
  "state": "PENDING",
  "targetUrl": "http://localhost:9090/ok",
  "httpMethod": "POST"
}
```

`requestBody` is optional, forwarded byte-for-byte, and **never returned** by the read API.

Repeating the same `Caller-Id` + `Idempotency-Key` returns the existing operation with `200`, even when requests race. The first request wins; the payload of a duplicate is not compared.

---

## How It Works

```text
                    ┌──────────────┐
                    │    Client    │
                    └──────┬───────┘
                           │  POST /operations
                           ▼
                    ┌──────────────┐
                    │   Relay API  │  validate + idempotent create
                    └──────┬───────┘
                           │
                           ▼
                 ┌───────────────────┐
                 │    PostgreSQL     │
                 │  operations       │  intent, one row per caller/key
                 │  attempts         │  one row per execution
                 └─────────┬─────────┘
                           │  claim (FOR UPDATE SKIP LOCKED)
                           ▼
                       Worker(s)
                           │
                           │  real HTTP call
                           ▼
                   External Service
```

There is no message broker. PostgreSQL is both the database and the queue, which keeps the first version to one process and one datastore.

An **operation** is the caller's intent. An **attempt** is one actual execution of it. They are separate rows because retries, auditing, and "how many times did we call the destination?" all depend on that distinction.

### Operation states

```text
             claim                 2xx
PENDING ───────────▶ PROCESSING ────────▶ SUCCESS
   ▲                    │
   │                    ├── 3xx / 4xx ──▶ FAILED
   │                    │
   │                    └── 5xx / 408 / 425 / 429
   │                             or timeout / network error
   │                                      │
   │                                      ▼
   │                                RETRY_WAITING
   └──────────────────────────────────────┘
                (Phase 4: claim again when due)
```

`SUCCESS` and `FAILED` are terminal. `RETRY_WAITING` is truthful today but **not claimed yet** — retry scheduling arrives in Phase 4.

### Attempt states

| State | Meaning |
| ----- | ------- |
| `RUNNING` | Committed **before** the HTTP call, so the evidence survives a crash. |
| `SUCCEEDED` | Destination returned 2xx. |
| `FAILED` | Non-2xx response, or the connection never completed. |
| `UNKNOWN` | Timeout. The request may have arrived; the outcome is genuinely unknown. |

### Outcome classification

| Situation | Attempt | Operation | Retried later? |
| --------- | ------- | --------- | -------------- |
| `2xx` | `SUCCEEDED` | `SUCCESS` | No |
| `3xx` (redirects are never followed) | `FAILED` | `FAILED` | No |
| Other `4xx` | `FAILED` | `FAILED` | No |
| `408`, `425`, `429`, `5xx` | `FAILED` | `RETRY_WAITING` | Yes (Phase 4) |
| Timeout | `UNKNOWN` | `RETRY_WAITING` | Yes |
| Connection refused / DNS / TLS | `FAILED` | `RETRY_WAITING` | Yes |

### Delivery semantics

Relay delivers **at least once** and records every attempt it starts. It cannot promise exactly-once: a timeout may mean the destination already applied the request. Destinations must be idempotent themselves.

---

## Quick Start

### Prerequisites

- Java 17+
- Maven (or the included `mvnw`)
- PostgreSQL on `localhost:5432` with a database named `relay`

### 1. Configure credentials

The real files are git-ignored; copy the example:

```powershell
Copy-Item .env.example .env
```

`.env`:

```properties
DB_PASSWORD=your-postgres-password
```

`src/main/resources/application.properties` must also exist locally:

```properties
spring.application.name=relay
spring.datasource.url=jdbc:postgresql://localhost:5432/relay
spring.datasource.username=postgres
spring.datasource.password=${DB_PASSWORD}

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

### 2. Apply the schema migration

`ddl-auto=update` adds columns but does not reliably alter check constraints, so a manual migration is required:

```powershell
$env:PGPASSWORD = "your-postgres-password"
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" `
  -h localhost -U postgres -d relay `
  -f src/main/resources/db/manual/001_phase3_execution.sql
```

It is safe to run more than once.

### 3. Run

```powershell
mvn test                 # 15 tests, real PostgreSQL + real HTTP servers
mvn spring-boot:run      # app on :8080, worker polls every 5 s
```

### 4. Try it

```powershell
$body = @{
  targetUrl  = "http://localhost:9090/ok"
  httpMethod = "POST"
  requestBody = "hello"
} | ConvertTo-Json -Compress

$op = Invoke-RestMethod -Uri "http://localhost:8080/operations" -Method Post `
  -Headers @{ "Caller-Id" = "demo"; "Idempotency-Key" = "ok-1" } `
  -ContentType "application/json" -Body $body

Invoke-RestMethod "http://localhost:8080/operations/$($op.id)"
Invoke-RestMethod "http://localhost:8080/operations/$($op.id)/attempts"
```

Any HTTP server on `9090` works as a destination. By default only `localhost` and `127.0.0.1` are allowed.

---

## Configuration

Every property has a default, so the app starts without them.

| Property | Default | Meaning |
| -------- | ------- | ------- |
| `relay.worker.enabled` | `true` | Set `false` to disable scheduled processing (tests do this). |
| `relay.worker.poll-interval-ms` | `5000` | Wait after a poll finishes before polling again. |
| `relay.http.connect-timeout` | `2s` | TCP connect timeout. |
| `relay.http.request-timeout` | `5s` | Whole-request timeout. |
| `relay.destination.allowed-hosts` | `localhost,127.0.0.1` | Comma-separated destination allowlist, matched as a hostname string. |

---

## Tests

No mocks for the database or the HTTP client — the bugs that matter live in concurrency, transactions, and real network behaviour.

| Test class | Tests | Covers |
| ---------- | ----- | ------ |
| `HttpExecutionTest` | 9 | 2xx, 4xx, 5xx, timeout, connection refused, redirects, allowlist and input validation |
| `IdempotencyConcurrencyTest` | 1 | 8 simultaneous duplicate creates against a real database |
| `OperationTest` | 4 | Operation state machine transitions |
| `RelayApplicationTests` | 1 | Spring context loads |

Last verified run: **15 tests, 0 failures**.

---

## Technology

| Component | Technology |
| --------- | ---------- |
| Language | Java 17 |
| Framework | Spring Boot 4.1.1 (Web MVC, Data JPA) |
| Database | PostgreSQL |
| API | REST |
| Build | Maven |
| Testing | JUnit 5, real HTTP servers |
| Deployment | Not containerised yet |

Additional infrastructure is introduced only when a concrete requirement justifies it.

---

## Roadmap

- [x] **Phase 1** — operation model and PostgreSQL persistence
- [x] **Phase 2** — idempotent creation under concurrency
- [x] **Phase 3** — real HTTP execution, allowlist, attempt outcomes
- [ ] **Phase 4** — retry scheduling: backoff with jitter, attempt limits, due-time claiming
- [ ] **Phase 5** — demo packaging: mock receiver, seed script, walkthrough

Deferred until after the first demo: worker leases, crash recovery, dead-letter/replay, authentication, Flyway migrations, metrics, pagination, and a UI.

---

## Known Limitations

- No retry execution yet — `RETRY_WAITING` rows stay put until Phase 4.
- No leases or fencing, so a process that dies mid-call leaves a `RUNNING` attempt and a `PROCESSING` operation forever.
- No authentication. UUIDs are unguessable, not authorisation. Do not expose this publicly.
- The destination allowlist matches hostnames, not resolved IPs. It reduces SSRF risk; it does not eliminate it.
- A retry with the same idempotency key but a different body returns the original operation.

---

## Engineering Approach

This project follows a **failure-first** approach:

```text
Understand → Design → Implement → Break → Observe → Debug → Refine
```

The system is not complete because the happy path works. Failure scenarios are introduced deliberately, and the observed behaviour — not the intention — is what gets documented. The repository is as much an engineering case study as a working system: **not just what was built, but why, and how it behaves under failure.**
