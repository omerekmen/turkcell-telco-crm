# Sprint 08 - Order and Payment

## Objective

Build order-service (9004) and payment-service (9008): order capture with synchronous customer/price
validation, order state model, the saga state foundation, and event emission; plus a mock-PSP payment
service with idempotency and timed retry. This sprint assembles the producing/consuming halves that
Sprint 09 stitches into the full onboarding saga.

Covers FR-09, FR-10, FR-11, FR-12 (order) and FR-25, FR-26, FR-27 (payment).

## Included Epics

- Epic 8: Order Orchestration and Payment (order-service, payment-service)

## Tasks

---

### 8.1 Order Service - Scaffold and Schema

#### 8.1.1 Scaffold order-service from template
- ID: 8.1.1
- Title: Create order-service from the service template
- Description: Instantiate `microservices/order-service` (port 9004, base package `com.telco.order`)
  from the template; depend on starter-api, starter-security, starter-mediator, starter-observability,
  starter-outbox, starter-inbox; own the `order` database. Architecture mode: Domain Orchestration
  (ADR-004) - saga workflow over Order/SagaState with compensation; declare it in CLAUDE.md/README.
- Business Purpose: Standardized order-orchestration service skeleton.
- Inputs: ADR-017.
- Outputs: order-service skeleton building and registering.
- Acceptance Criteria:
  - Service starts, registers, exposes Swagger UI.
- Dependencies: 3.4.1, Sprint 04
- Complexity: S

#### 8.1.2 Order schema migration
- ID: 8.1.2
- Title: Create Flyway migration for orders, items, saga state
- Description: `V1__order.sql` creating `orders` (id, customer_id, status, total_amount, currency,
  created_at), `order_items` (id, order_id, product_code, product_type, quantity, unit_price), and
  `saga_state` (id, order_id, current_step, payload jsonb, last_updated). Plus platform outbox/inbox
  tables.
- Business Purpose: Persist orders and saga progress (FR-10, FR-11).
- Inputs: analysis Section 10.3, FR-10, FR-11.
- Outputs: Flyway migration.
- Acceptance Criteria:
  - Migration applies on Testcontainers Postgres; status supports DRAFT, PENDING_PAYMENT, PAID,
    FULFILLED, CANCELLED.
- Dependencies: 8.1.1
- Complexity: M

---

### 8.2 Order Service - Domain and Validation

#### 8.2.1 Order domain model and status machine
- ID: 8.2.1
- Title: Implement Order aggregate with status transitions
- Description: `Order` aggregate enforcing DRAFT -> PENDING_PAYMENT -> PAID -> FULFILLED and
  -> CANCELLED transitions (FR-11), with `OrderItem` lines carrying a price snapshot. Illegal
  transitions throw `BusinessRuleException`.
- Business Purpose: Encode the order lifecycle as invariants (FR-11).
- Inputs: FR-11, analysis Section 10.3.
- Outputs: Domain aggregate + entities.
- Acceptance Criteria:
  - Valid transitions succeed; e.g. FULFILLED->PENDING_PAYMENT throws `BusinessRuleException`.
- Dependencies: 8.1.2
- Complexity: M

#### 8.2.2 Synchronous customer and price validation (Feign + Resilience4j)
- ID: 8.2.2
- Title: Implement synchronous customer check and catalog price snapshot
- Description: At order creation, synchronously call customer-service (customer exists and is ACTIVE)
  and product-catalog-service (price-quote 7.4.4) via OpenFeign, wrapped in a Resilience4j circuit
  breaker/retry. Store the returned price as an immutable snapshot on the order item (analysis 9.1).
- Business Purpose: Immediate validation and price snapshotting before committing an order (FR-09).
- Inputs: analysis Section 9.1, FR-09, NFR-10.
- Outputs: Feign clients + circuit breakers + snapshot logic.
- Acceptance Criteria:
  - Ordering for a non-existent/inactive customer is rejected; the order item stores the catalog
    price snapshot; downstream failure trips the breaker and surfaces `DependencyFailureException`.
- Dependencies: 8.2.1, 6.3.1, 7.4.4
- Complexity: L

---

### 8.3 Order Service - Application

#### 8.3.1 Create order command and endpoint
- ID: 8.3.1
- Title: Implement POST /api/v1/orders with Idempotency-Key
- Description: `CreateOrderCommand` creating an order (DRAFT->PENDING_PAYMENT), persisting items with
  snapshots, initializing `saga_state`, and publishing `order.created.v1` via the outbox. Honor the
  `Idempotency-Key` header so a retried create returns the same order (FR-26 pattern reuse).
- Business Purpose: Order placement that starts the saga (FR-09, FR-10).
- Inputs: FR-09, FR-10, event-catalog `order.created.v1`.
- Outputs: Command, handler, DTOs, endpoint, event, idempotency guard.
- Acceptance Criteria:
  - A valid order returns 201, status PENDING_PAYMENT, emits `order.created.v1`; replaying the same
    `Idempotency-Key` returns the original order without a duplicate.
- Dependencies: 8.2.2
- Complexity: L

#### 8.3.2 Get order
- ID: 8.3.2
- Title: Implement GET /api/v1/orders/{id}
- Description: `GetOrderQuery` returning the order with items, status, and saga step as `ApiResult`.
- Business Purpose: Order visibility for clients and support.
- Inputs: analysis Section 8.3.
- Outputs: Query + endpoint.
- Acceptance Criteria:
  - GET returns the order with current status; unknown id returns 404.
- Dependencies: 8.3.1
- Complexity: S

#### 8.3.3 Cancel order and compensation event
- ID: 8.3.3
- Title: Implement POST /api/v1/orders/{id}/cancel emitting order.cancelled.v1
- Description: `CancelOrderCommand` transitioning to CANCELLED (only from allowed states) and
  publishing `order.cancelled.v1` (consumed by payment and subscription for compensation, FR-12).
- Business Purpose: Order cancellation triggering compensation (FR-12).
- Inputs: FR-12, event-catalog `order.cancelled.v1`.
- Outputs: Cancel command + endpoint + event.
- Acceptance Criteria:
  - Cancelling a PENDING_PAYMENT/PAID order emits `order.cancelled.v1`; cancelling a FULFILLED order
    is rejected with a business-rule error.
- Dependencies: 8.3.1
- Complexity: M

---

### 8.4 Payment Service - Scaffold and Schema

#### 8.4.1 Scaffold payment-service from template
- ID: 8.4.1
- Title: Create payment-service from the service template
- Description: Instantiate `microservices/payment-service` (port 9008, base package
  `com.telco.payment`) from the template; depend on starter-api, starter-security, starter-mediator,
  starter-observability, starter-outbox, starter-inbox; own the `payment` database; audit logging
  enabled. Architecture mode: Domain Orchestration (ADR-004) - charge/retry/refund workflow
  coordinating the PSP and saga events; declare it in CLAUDE.md/README.
- Business Purpose: Standardized payment-domain service skeleton.
- Inputs: ADR-017.
- Outputs: payment-service skeleton building and registering.
- Acceptance Criteria:
  - Service starts, registers, exposes Swagger UI.
- Dependencies: 3.4.1, Sprint 04
- Complexity: S

#### 8.4.2 Payment schema migration
- ID: 8.4.2
- Title: Create Flyway migration for payments, attempts, idempotency
- Description: `V1__payment.sql` creating `payments` (id, invoice_id nullable, order_id nullable,
  amount, currency, method, status, external_ref, payment_request_id unique, paid_at),
  `payment_attempts` (id, payment_id, attempt_no, response, attempted_at), and an audit table. The
  unique `payment_request_id` enforces idempotency (FR-26).
- Business Purpose: Persist payments and attempts with idempotency (FR-26).
- Inputs: analysis Section 10.7, FR-26.
- Outputs: Flyway migration.
- Acceptance Criteria:
  - Migration applies; a duplicate `payment_request_id` insert is rejected by the unique constraint.
- Dependencies: 8.4.1
- Complexity: M

---

### 8.5 Payment Service - Domain and Application

#### 8.5.1 Mock PSP adapter
- ID: 8.5.1
- Title: Implement mock PSP gateway adapter
- Description: A `PspGateway` port with a mock implementation simulating success/failure (configurable,
  e.g. by amount or test flag) returning an external reference. Wrapped in a Resilience4j circuit
  breaker as an external dependency (NFR-10).
- Business Purpose: Stand in for a real PSP so payment flows are testable (FR-25).
- Inputs: FR-25, analysis Section 8.7, NFR-10.
- Outputs: PSP port + mock adapter.
- Acceptance Criteria:
  - The mock returns success and failure deterministically per its config; calls are breaker-guarded.
- Dependencies: 8.4.2
- Complexity: M

#### 8.5.2 Idempotent charge command and endpoint
- ID: 8.5.2
- Title: Implement POST /api/v1/payments with idempotency
- Description: `ChargePaymentCommand` keyed by `paymentRequestId`/`Idempotency-Key`: if already
  processed, return the prior result; otherwise charge via the PSP, persist the payment + attempt,
  and publish `payment.completed.v1` or `payment.failed.v1` via the outbox.
- Business Purpose: Reliable, non-duplicating payment processing (FR-25, FR-26).
- Inputs: FR-25, FR-26, event-catalog payment events.
- Outputs: Charge command, handler, DTOs, endpoint, events.
- Acceptance Criteria:
  - A successful charge emits `payment.completed.v1`; a failure emits `payment.failed.v1`; replaying
    the same `paymentRequestId` returns the original result and does not re-charge.
- Dependencies: 8.5.1
- Complexity: L

#### 8.5.3 Consume order.created for auto-charge
- ID: 8.5.3
- Title: Implement order.created.v1 consumer initiating payment
- Description: Idempotent (inbox) consumer of `order.created.v1` that initiates a charge for the order
  amount, reusing the charge command. This is the saga step 3 producer of payment outcomes.
- Business Purpose: Drive the onboarding saga from order to payment asynchronously (FR-10).
- Inputs: event-catalog saga sequence, FR-10.
- Outputs: Kafka consumer + inbox guard.
- Acceptance Criteria:
  - Consuming `order.created.v1` produces exactly one payment attempt (inbox-deduplicated) and emits
    a payment outcome event.
- Dependencies: 8.5.2
- Complexity: M

#### 8.5.4 Failed-payment retry scheduler
- ID: 8.5.4
- Title: Implement 24/72/168h retry for failed payments
- Description: A scheduler re-attempting failed payments at 24, 72, and 168 hours, recording each as a
  new `payment_attempt`, emitting `payment.completed.v1` on eventual success or giving up after the
  final interval (FR-27).
- Business Purpose: Recover transient payment failures on a defined cadence (FR-27).
- Inputs: FR-27.
- Outputs: Retry scheduler + attempt tracking.
- Acceptance Criteria:
  - A failed payment is retried at the configured intervals; a later success emits
    `payment.completed.v1`; attempts are capped after 168h.
- Dependencies: 8.5.2
- Complexity: M

#### 8.5.5 Get payment and refund
- ID: 8.5.5
- Title: Implement GET /api/v1/payments/{id} and POST /{id}/refund
- Description: `GetPaymentQuery` and `RefundPaymentCommand` (used in compensation) emitting
  `payment.refunded.v1`. Refund is idempotent.
- Business Purpose: Payment visibility and compensation support (FR-12 compensation).
- Inputs: analysis Section 8.7, event-catalog `payment.refunded.v1`.
- Outputs: Query/command + endpoints + event.
- Acceptance Criteria:
  - GET returns the payment; refunding a completed payment emits `payment.refunded.v1`; a second
    refund of the same payment is a no-op.
- Dependencies: 8.5.2
- Complexity: M

---

### 8.6 Tests

#### 8.6.1 Order and payment integration tests
- ID: 8.6.1
- Title: Add order/payment integration tests (Testcontainers)
- Description: RestAssured + Testcontainers (Postgres, Kafka) covering order create with sync
  validation and price snapshot, idempotent order/payment replay, payment success/failure events,
  retry scheduling, refund, and order cancellation event. Mock customer/catalog where needed.
- Business Purpose: Verify both services and their event contracts (NFR-17).
- Inputs: 8.3.x, 8.5.x.
- Outputs: Integration test suites for both services.
- Acceptance Criteria:
  - FR-09..12 and FR-25..27 flows pass; idempotency and event emission asserted; breaker behavior
    verified on a simulated downstream failure.
- Dependencies: 8.3.3, 8.5.4, 8.5.5
- Complexity: L

---

## Sprint Deliverables

- order-service (9004): order capture with sync customer/price validation and snapshot, status
  machine, saga-state init, create/get/cancel endpoints, and `order.created.v1`/`order.cancelled.v1`.
- payment-service (9008): mock PSP, idempotent charge, order.created consumer, 24/72/168h retry,
  refund, and payment events.
- Integration tests for both.

## Exit Criteria

- An order can be placed (PENDING_PAYMENT) with a validated customer and price snapshot, emitting
  `order.created.v1`; payment-service consumes it and emits `payment.completed.v1`/`payment.failed.v1`.
- Idempotency holds for both order creation and payment charging; failed payments retry on schedule;
  refunds are idempotent.
- FR-09..12 and FR-25..27 pass at the service level (full saga wiring completes in Sprint 09).
</content>
