# Relay

> **A durable execution and reliability layer for business-critical API operations.**

Relay sits between an application and the external services it depends on. Its purpose is to handle the failure cases where **the network request fails, but the outcome of the actual operation is unknown**.

A timeout, dropped connection, or crashed worker should not automatically result in a lost operation or an accidental duplicate.

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

The application sees a timeout.

But what actually happened?

* The request may never have reached the service.
* The service may still be processing it.
* The operation may have succeeded.
* The operation may have succeeded but the response was lost.

Simply retrying can therefore create a **duplicate operation**.

Relay is designed to handle this ambiguity.

---

## What Relay Does

```text
                    ┌──────────────┐
                    │   Client     │
                    └──────┬───────┘
                           │
                           ▼
                    ┌──────────────┐
                    │  Relay API   │
                    └──────┬───────┘
                           │
                           ▼
                  ┌──────────────────┐
                  │   PostgreSQL     │
                  │                  │
                  │ Operations       │
                  │ Attempts         │
                  │ Leases           │
                  │ Outbox           │
                  └────────┬─────────┘
                           │
                           ▼
                       Worker(s)
                           │
                           ▼
                  External Service
```

Relay provides mechanisms for:

* Durable operation persistence
* Operation state management
* Idempotency
* Asynchronous execution
* Retry policies
* Failure classification
* Crash recovery
* Worker leases
* Dead-letter handling
* Circuit breaking
* Observability
* Transactional outbox

---

## Core Principle

> **Network outcome and business outcome are not necessarily the same thing.**

A successful HTTP response does not represent the entirety of the problem, and a failed request does not necessarily mean the underlying business operation failed.

Relay exists to manage that gap.

---

## Engineering Approach

This project follows a **failure-first engineering approach**.

```text
Understand
    ↓
Design
    ↓
Implement
    ↓
Break
    ↓
Observe
    ↓
Debug
    ↓
Refine
```

The system is not considered complete merely because the happy path works.

Failure scenarios are deliberately introduced and the resulting behavior is observed and verified.

---

## Technology

| Component  | Technology  |
| ---------- | ----------- |
| Language   | Java        |
| Framework  | Spring Boot |
| Database   | PostgreSQL  |
| API        | REST        |
| Build      | Maven       |
| Testing    | JUnit       |
| Deployment | Docker      |

Additional infrastructure will be introduced only when a concrete requirement justifies it.

---

## Project Goals

Relay is being developed as a focused backend engineering and distributed-systems project.

The project aims to demonstrate practical understanding of:

* Distributed-system failure modes
* State machines
* Database concurrency
* Idempotency
* Retry engineering
* Asynchronous workers
* Crash recovery
* Leases
* Delivery semantics
* Observability
* Failure injection
* Concurrency testing
* Performance analysis

---

## Example Lifecycle

```text
PENDING
   │
   ▼
PROCESSING
   │
   ├──────────────► SUCCEEDED
   │
   ├──────────────► RETRY_WAIT
   │                    │
   │                    └──────► PROCESSING
   │
   └──────────────► FAILED
                         │
                         ▼
                       DLQ
```

The exact state model will evolve during implementation and will be documented as design decisions are made.

---

## Development Status

🚧 **Active development**

The project is being built incrementally, with the initial focus on:

1. Operation abstraction
2. State machine
3. PostgreSQL persistence
4. Idempotency
5. Asynchronous execution
6. Retry engine
7. Crash recovery
8. Reliability testing

---

## Documentation

Design decisions, architecture diagrams, failure experiments, and implementation notes will be documented alongside the project.

The repository is being developed as both a working system and an engineering case study: **not just what was built, but why it was built that way and how it behaves under failure.**
