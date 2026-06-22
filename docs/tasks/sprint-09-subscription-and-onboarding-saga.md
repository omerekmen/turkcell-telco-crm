# Sprint 09 - Subscription and Onboarding Saga

## Objective

Build subscription-service (9005) with its lifecycle state machine and MSISDN allocation, then wire
the end-to-end new-line onboarding saga (order -> payment -> subscription -> fulfillment + welcome
SMS) including compensation on failure. Completing this sprint delivers acceptance criterion AC-01.

Covers FR-13, FR-14, FR-15 (FR-16 MNP is post-MVP, scaffolded only) and the saga orchestration of
FR-10/FR-12.

## Included Epics

- Epic 9: Subscription Lifecycle and Onboarding Saga (subscription-service + cross-service saga)

## Tasks

---

### 9.1 Subscription Service - Scaffold and Schema

#### 9.1.1 Scaffold subscription-service from template
- ID: 9.1.1
- Title: Create subscription-service from the service template
- Description: Instantiate `microservices/subscription-service` (port 9005, base package
  `com.telco.subscription`) from the template; depend on starter-api, starter-security,
  starter-mediator, starter-observability, starter-outbox, starter-inbox; own the `subscription`
  database; CQRS+Mediator; audit logging enabled.
- Business Purpose: Standardized subscription-domain service skeleton.
- Inputs: ADR-017.
- Outputs: subscription-service skeleton building and registering.
- Acceptance Criteria:
  - Service starts, registers, exposes Swagger UI.
- Dependencies: 3.4.1, Sprint 04
- Complexity: S

#### 9.1.2 Subscription schema migration
- ID: 9.1.2
- Title: Create Flyway migration for subscriptions, MSISDN pool, SIM cards
- Description: `V1__subscription.sql` creating `subscriptions` (id, customer_id, msisdn, tariff_code,
  tariff_version, status, activated_at, terminated_at), `msisdn_pool` (msisdn primary key, status,
  reserved_until), `sim_cards` (iccid, imsi, msisdn, status), plus outbox/inbox tables.
- Business Purpose: Persist subscription lifecycle and number/SIM inventory (FR-13, FR-15).
- Inputs: analysis Section 10.4, FR-13, FR-15.
- Outputs: Flyway migration.
- Acceptance Criteria:
  - Migration applies; status supports ACTIVE/SUSPENDED/TERMINATED; MSISDN pool supports
    FREE/RESERVED/ALLOCATED.
- Dependencies: 9.1.1
- Complexity: M

#### 9.1.3 Seed MSISDN pool
- ID: 9.1.3
- Title: Seed the MSISDN pool with FREE numbers
- Description: Seed a range of FREE MSISDNs for allocation during onboarding (test/dev data via Flyway
  or an admin endpoint).
- Business Purpose: Provide allocatable numbers for activation (FR-13).
- Inputs: analysis Section 10.4.
- Outputs: MSISDN seed data.
- Acceptance Criteria:
  - A fresh DB has FREE MSISDNs available; allocation reduces the FREE count.
- Dependencies: 9.1.2
- Complexity: S

---

### 9.2 Subscription Service - Domain

#### 9.2.1 Subscription state machine
- ID: 9.2.1
- Title: Implement Subscription aggregate lifecycle
- Description: `Subscription` aggregate enforcing ACTIVE -> SUSPENDED -> ACTIVE (reactivate) ->
  TERMINATED transitions (FR-14); a customer may hold multiple subscriptions (FR-15). Illegal
  transitions throw `BusinessRuleException`.
- Business Purpose: Encode subscription lifecycle invariants (FR-14, FR-15).
- Inputs: FR-14, FR-15, analysis Section 8.4.
- Outputs: Domain aggregate.
- Acceptance Criteria:
  - Suspend/reactivate/terminate transitions behave per spec; terminating an already-terminated
    subscription throws; one customer can have multiple active subscriptions.
- Dependencies: 9.1.2
- Complexity: M

#### 9.2.2 MSISDN allocation and release
- ID: 9.2.2
- Title: Implement atomic MSISDN allocation/release with events
- Description: Allocate a FREE MSISDN atomically (reserve -> allocate) on activation, release it to
  FREE on termination, emitting `msisdn.allocated.v1` / `msisdn.released.v1`. Use row-level locking
  to prevent double allocation.
- Business Purpose: Conflict-free number assignment (FR-13).
- Inputs: FR-13, event-catalog MSISDN events.
- Outputs: Allocation service + events.
- Acceptance Criteria:
  - Concurrent allocations never assign the same MSISDN (test with parallel allocation); termination
    returns the number to FREE and emits `msisdn.released.v1`.
- Dependencies: 9.2.1, 9.1.3
- Complexity: M

---

### 9.3 Subscription Service - Application and Lifecycle Endpoints

#### 9.3.1 Internal create/activate subscription
- ID: 9.3.1
- Title: Implement POST /api/v1/subscriptions (internal) and activation
- Description: `ActivateSubscriptionCommand` (called by the saga) allocating an MSISDN, creating an
  ACTIVE subscription with the tariff snapshot, and publishing `subscription.activated.v1`. On
  allocation/creation failure, publish a `SubscriptionActivationFailed` signal for compensation.
- Business Purpose: Automatic activation when an order completes (FR-13, AC-01 step 5).
- Inputs: FR-13, event-catalog `subscription.activated.v1`, analysis Section 9.2.
- Outputs: Activate command, endpoint, events.
- Acceptance Criteria:
  - Activation allocates an MSISDN, sets status ACTIVE, emits `subscription.activated.v1`; on failure
    it emits an activation-failed signal and allocates no number.
- Dependencies: 9.2.2
- Complexity: L

#### 9.3.2 Suspend, reactivate, terminate endpoints
- ID: 9.3.2
- Title: Implement subscription lifecycle endpoints
- Description: `POST /{id}/suspend`, `/{id}/reactivate`, `/{id}/terminate` commands emitting
  `subscription.suspended.v1` / `subscription.terminated.v1` (terminate also releases the MSISDN).
  Suspend is also triggered by payment failure after a grace period (consume `payment.failed.v1`).
- Business Purpose: Manage the subscription lifecycle (FR-14).
- Inputs: FR-14, event-catalog subscription events.
- Outputs: Lifecycle commands + endpoints + events + payment-failed consumer.
- Acceptance Criteria:
  - Suspend/reactivate/terminate transition correctly and emit their events; terminate releases the
    MSISDN; a post-grace `payment.failed.v1` suspends the subscription idempotently.
- Dependencies: 9.3.1
- Complexity: M

#### 9.3.3 Get subscription and customer subscriptions
- ID: 9.3.3
- Title: Implement subscription read endpoints
- Description: `GET /api/v1/subscriptions/{id}` and `GET /api/v1/subscriptions?customerId=...`
  returning `ApiResult` with status, MSISDN, and tariff.
- Business Purpose: Subscription visibility (FR-15).
- Inputs: FR-15.
- Outputs: Queries + endpoints.
- Acceptance Criteria:
  - Reads return the subscription(s); a customer with multiple subscriptions returns all of them.
- Dependencies: 9.3.1
- Complexity: S

#### 9.3.4 MNP state-machine scaffold (post-MVP)
- ID: 9.3.4
- Title: Scaffold MNP port number portability state machine (deferred)
- Description: Define the MNP state-machine interface and states without full implementation,
  documenting it as post-MVP (FR-16). No active endpoint.
- Business Purpose: Reserve a clean extension point for number portability (FR-16).
- Inputs: FR-16 (post-MVP).
- Outputs: MNP interface/state enum + docs.
- Acceptance Criteria:
  - The MNP states/interface compile and are documented as deferred; no MVP flow depends on them.
- Dependencies: 9.2.1
- Complexity: S

---

### 9.4 Onboarding Saga Wiring

#### 9.4.1 Payment-completed consumer in subscription-service
- ID: 9.4.1
- Title: Consume payment.completed.v1 to activate subscription
- Description: Idempotent (inbox) consumer of `payment.completed.v1` invoking
  `ActivateSubscriptionCommand` for the order's subscription (saga step 4).
- Business Purpose: Asynchronously activate on successful payment (FR-13, AC-01).
- Inputs: event-catalog saga sequence, FR-13.
- Outputs: Kafka consumer + inbox guard.
- Acceptance Criteria:
  - One `payment.completed.v1` yields exactly one activation (inbox-deduplicated) and a
    `subscription.activated.v1` event.
- Dependencies: 9.3.1, 8.5.3
- Complexity: M

#### 9.4.2 Order saga: confirm/fulfill on activation
- ID: 9.4.2
- Title: Drive order saga to FULFILLED on subscription.activated.v1
- Description: In order-service, idempotent consumers of `payment.completed.v1` (PENDING_PAYMENT->PAID)
  and `subscription.activated.v1` (PAID->FULFILLED), updating `saga_state` at each step (FR-10).
- Business Purpose: Complete the order on successful activation (FR-10, AC-01 step 5).
- Inputs: FR-10, event-catalog saga, analysis Section 9.2.
- Outputs: Order saga consumers + saga-state updates.
- Acceptance Criteria:
  - On `payment.completed.v1` the order becomes PAID; on `subscription.activated.v1` it becomes
    FULFILLED; saga_state reflects each step; consumers are idempotent.
- Dependencies: 9.4.1, 8.3.1
- Complexity: L

#### 9.4.3 Compensation flow
- ID: 9.4.3
- Title: Implement compensation on activation failure
- Description: Wire the compensation chain: subscription activation failure -> payment refund
  (`payment.refunded.v1`) -> order CANCELLED (`order.cancelled.v1`), each consumed idempotently
  (FR-12, analysis Section 9.2 compensation).
- Business Purpose: Roll back a partially completed saga safely (FR-12).
- Inputs: FR-12, event-catalog compensation sequence.
- Outputs: Compensation consumers + events across the three services.
- Acceptance Criteria:
  - A forced activation failure triggers a refund and moves the order to CANCELLED; the MSISDN is not
    left allocated; the flow is idempotent under redelivery.
- Dependencies: 9.4.2, 8.5.5, 8.3.3
- Complexity: L

---

### 9.5 AC-01 End-to-End

#### 9.5.1 Welcome notification trigger
- ID: 9.5.1
- Title: Emit/handle welcome signal on subscription.activated.v1
- Description: Ensure `subscription.activated.v1` carries the data needed for a welcome SMS; the
  notification-service consumer is built in Sprint 12, so here verify the event payload and a
  mock-log fallback proving the welcome step (AC-01 step 6).
- Business Purpose: Welcome the new subscriber (AC-01 step 6).
- Inputs: AC-01, event-catalog.
- Outputs: Verified event payload + mock welcome log (pending Sprint 12 consumer).
- Acceptance Criteria:
  - `subscription.activated.v1` contains customer/MSISDN/tariff; a mock welcome log entry is produced
    in the absence of notification-service.
- Dependencies: 9.4.1
- Complexity: S

#### 9.5.2 AC-01 onboarding integration test
- ID: 9.5.2
- Title: End-to-end onboarding saga test (Testcontainers)
- Description: A multi-service Testcontainers test (Postgres, Kafka) driving register -> KYC approve
  -> order -> payment success -> activation -> order FULFILLED -> welcome signal, plus a failure
  variant exercising compensation.
- Business Purpose: Prove AC-01 end to end including compensation.
- Inputs: AC-01, Sprints 06/08/09.
- Outputs: AC-01 integration test (happy path + compensation).
- Acceptance Criteria:
  - Happy path ends with order FULFILLED, an ACTIVE subscription, an allocated MSISDN, and a welcome
    signal; the failure path ends with order CANCELLED, refund issued, no MSISDN allocated.
- Dependencies: 9.4.3, 9.5.1
- Complexity: L

---

## Sprint Deliverables

- subscription-service (9005): lifecycle state machine, atomic MSISDN allocation/release, activation
  and lifecycle endpoints, MNP scaffold (deferred), and subscription events.
- Fully wired onboarding saga across order/payment/subscription with compensation.
- AC-01 end-to-end integration test (happy path and compensation).

## Exit Criteria

- AC-01 passes end to end: a registered, KYC-approved customer orders a postpaid tariff, pays via
  mock PSP, gets an automatically activated subscription with an allocated MSISDN, a welcome signal,
  and a FULFILLED order.
- A forced activation failure compensates (refund + order CANCELLED) with no dangling MSISDN.
- FR-13, FR-14, FR-15 pass; FR-10 and FR-12 saga behavior validated; FR-16 scaffolded as post-MVP.
</content>
