# Sprint 10 - Usage Metering

## Objective

Build usage-service (9006): consume CDR events from Kafka, decrement quota balances, expose
near-real-time remaining quota, emit 80%/100% threshold events, and aggregate overage for billing.
Provide a CDR simulator to drive the flow. Delivers acceptance criterion AC-03.

Covers FR-17, FR-18, FR-19, FR-20.

## Included Epics

- Epic 10: Usage and Quota (usage-service + CDR simulator)

## Tasks

---

### 10.1 Scaffold and Schema

#### 10.1.1 Scaffold usage-service from template
- ID: 10.1.1
- Title: Create usage-service from the service template
- Description: Instantiate `microservices/usage-service` (port 9006, base package `com.telco.usage`)
  from the template; depend on starter-api, starter-security, starter-mediator, starter-observability,
  starter-outbox, starter-inbox; own the `usage` database; CQRS+Mediator. This is a write-heavy
  service; tune the consumer for throughput.
- Business Purpose: Standardized usage-domain service skeleton.
- Inputs: ADR-017, analysis Section 8.5.
- Outputs: usage-service skeleton building and registering.
- Acceptance Criteria:
  - Service starts, registers, exposes Swagger UI.
- Dependencies: 3.4.1, Sprint 04
- Complexity: S

#### 10.1.2 Usage schema migration
- ID: 10.1.2
- Title: Create Flyway migration for quota and usage records
- Description: `V1__usage.sql` creating `quotas` (id, subscription_id, period_start, period_end,
  minutes_remaining, sms_remaining, mb_remaining, minutes_total, sms_total, mb_total) and
  `usage_records` (id, subscription_id, type, quantity, recorded_at, cdr_ref, overage boolean), plus
  outbox/inbox tables. Index for fast quota lookup by subscription.
- Business Purpose: Persist quotas and usage history (FR-17, FR-18).
- Inputs: analysis Section 10.5, FR-17, FR-18.
- Outputs: Flyway migration.
- Acceptance Criteria:
  - Migration applies; quota and usage_records exist with an index supporting near-real-time reads.
- Dependencies: 10.1.1
- Complexity: M

---

### 10.2 Quota Provisioning

#### 10.2.1 Provision quota on subscription activation
- ID: 10.2.1
- Title: Consume subscription.activated.v1 to create a quota period
- Description: Idempotent (inbox) consumer of `subscription.activated.v1` that creates the current
  period quota from the tariff allowances (minutes/sms/mb) snapshot. Roll a new period at period end.
- Business Purpose: Establish the balance to meter against (FR-17, FR-18).
- Inputs: event-catalog `subscription.activated.v1`, FR-18.
- Outputs: Quota-provisioning consumer.
- Acceptance Criteria:
  - Activation creates a quota for the current period with totals from the tariff; the consumer is
    idempotent.
- Dependencies: 10.1.2, 9.3.1
- Complexity: M

---

### 10.3 CDR Ingestion and Metering

#### 10.3.1 CDR event schema and topic
- ID: 10.3.1
- Title: Define CDR Avro schema and topic
- Description: Define `cdr-recorded.v1` (subscriptionId, type VOICE/SMS/DATA, quantity, occurredAt,
  cdrRef) in platform-event-contracts and ensure the topic exists. CDR is high-volume and
  replayable (analysis Section 9.1).
- Business Purpose: Contract for the usage stream (FR-17).
- Inputs: analysis Section 8.5/9.1, event-catalog `usage.recorded.v1`.
- Outputs: Avro schema + topic.
- Acceptance Criteria:
  - Schema generates a `CdrRecordedV1` record; the topic accepts produced CDR events.
- Dependencies: 3.3.1
- Complexity: S

#### 10.3.2 CDR consumer and quota decrement
- ID: 10.3.2
- Title: Implement idempotent CDR consumer updating quota
- Description: Idempotent (inbox, keyed by cdrRef) consumer of `cdr-recorded.v1` that records a
  `usage_record` and atomically decrements the matching quota balance by type, emitting
  `usage.recorded.v1`. Concurrency-safe decrement (row lock / atomic update).
- Business Purpose: Real-time quota metering from the CDR stream (FR-17).
- Inputs: FR-17, event-catalog `usage.recorded.v1`.
- Outputs: CDR consumer + decrement logic.
- Acceptance Criteria:
  - A CDR event decrements the correct balance once (duplicate cdrRef ignored); concurrent events do
    not corrupt the balance (parallel test).
- Dependencies: 10.2.1, 10.3.1
- Complexity: L

---

### 10.4 Thresholds and Overage

#### 10.4.1 Threshold detection (80% / 100%)
- ID: 10.4.1
- Title: Emit quota.threshold-reached.v1 and quota.exceeded.v1
- Description: After each decrement, evaluate consumption against totals; emit
  `quota.threshold-reached.v1` once when crossing 80% and `quota.exceeded.v1` once when reaching 100%,
  guarding against duplicate emission per period/threshold (FR-19).
- Business Purpose: Notify subscribers and trigger overage at thresholds (FR-19, AC-03).
- Inputs: FR-19, event-catalog quota events, AC-03.
- Outputs: Threshold evaluation + events.
- Acceptance Criteria:
  - Crossing 80% emits exactly one `quota.threshold-reached.v1`; reaching 100% emits exactly one
    `quota.exceeded.v1`; re-crossing within the same period does not re-emit.
- Dependencies: 10.3.2
- Complexity: M

#### 10.4.2 Overage capture post-exhaustion
- ID: 10.4.2
- Title: Flag and aggregate post-exhaustion usage as overage
- Description: After a quota is exhausted, mark further `usage_records` as overage and accumulate
  overage quantities per subscription/period for billing (FR-20).
- Business Purpose: Bill usage beyond the included allowance (FR-20, AC-03).
- Inputs: FR-20, AC-03.
- Outputs: Overage flagging + aggregation.
- Acceptance Criteria:
  - Usage after exhaustion is flagged overage and accumulated; pre-exhaustion usage is not.
- Dependencies: 10.4.1
- Complexity: M

#### 10.4.3 Usage aggregation for billing
- ID: 10.4.3
- Title: Emit usage.aggregated.v1 for the billing period
- Description: Aggregate the period's usage and overage per subscription and emit `usage.aggregated.v1`
  (consumed by billing) on bill-cycle close or on demand (FR-20).
- Business Purpose: Hand period usage to billing (FR-20, AC-02 dependency).
- Inputs: FR-20, event-catalog `usage.aggregated.v1`.
- Outputs: Aggregation job + event.
- Acceptance Criteria:
  - Aggregation produces `usage.aggregated.v1` with per-subscription totals including overage for the
    requested period.
- Dependencies: 10.4.2
- Complexity: M

---

### 10.5 Read API

#### 10.5.1 Quota and history endpoints
- ID: 10.5.1
- Title: Implement quota and usage-history read endpoints
- Description: `GET /api/v1/usage/subscriptions/{id}/quota` (remaining minutes/sms/mb) and
  `GET /api/v1/usage/subscriptions/{id}/history?from=...&to=...` returning `ApiResult` with
  pagination (FR-18).
- Business Purpose: Near-real-time quota visibility for subscribers (FR-18).
- Inputs: FR-18, analysis Section 8.5.
- Outputs: Queries + endpoints.
- Acceptance Criteria:
  - The quota endpoint reflects decrements within the freshness target; history returns records in
    the requested range, paginated.
- Dependencies: 10.3.2
- Complexity: S

---

### 10.6 CDR Simulator

#### 10.6.1 CDR simulator tool
- ID: 10.6.1
- Title: Build a CDR simulator producing usage events
- Description: A small runnable (CLI/service profile) that produces `cdr-recorded.v1` events for given
  subscriptions at a configurable rate/volume, used for AC-03 and load checks.
- Business Purpose: Drive usage flows without a real CDR mediation feed (AC-03).
- Inputs: AC-03, analysis Section 14.3.
- Outputs: CDR simulator.
- Acceptance Criteria:
  - The simulator produces a configurable stream of CDR events that usage-service consumes and meters.
- Dependencies: 10.3.1
- Complexity: M

---

### 10.7 Tests

#### 10.7.1 Usage integration tests and AC-03
- ID: 10.7.1
- Title: Add usage integration tests including AC-03 (Testcontainers)
- Description: Testcontainers (Postgres, Kafka) tests covering quota provisioning, CDR metering with
  idempotency and concurrency, 80%/100% threshold emission, overage capture, aggregation, and the
  AC-03 scenario driven by the simulator.
- Business Purpose: Verify the usage domain and AC-03 (NFR-17).
- Inputs: 10.2-10.6, AC-03.
- Outputs: Integration test suite.
- Acceptance Criteria:
  - FR-17..20 flows pass; AC-03 passes: simulator usage decrements quota, 80% and 100% events fire,
    and post-exhaustion usage is aggregated as overage for billing.
- Dependencies: 10.4.3, 10.5.1, 10.6.1
- Complexity: L

---

## Sprint Deliverables

- usage-service (9006): quota provisioning on activation, idempotent CDR metering, 80%/100% threshold
  events, overage capture and aggregation, quota/history read APIs, and a CDR simulator.
- AC-03 integration test.

## Exit Criteria

- AC-03 passes: CDR events decrement quota, an 80% warning event and a 100% exceeded event fire once
  each, and post-exhaustion usage is forwarded to billing as overage via `usage.aggregated.v1`.
- Metering is idempotent (by cdrRef) and concurrency-safe; quota reads are near-real-time.
- FR-17, FR-18, FR-19, FR-20 pass.
</content>
