# Sprint 11 - Billing

## Objective

Build billing-service (9007): a monthly bill-run that generates invoices for all active postpaid
subscribers, composes invoice lines (monthly fee, addons, overage, VAS, taxes), renders PDFs, emits
invoice events, and reconciles payment. Delivers acceptance criterion AC-02 and the bill-run
performance target (NFR-02).

Covers FR-21, FR-22, FR-23, FR-24.

## Included Epics

- Epic 11: Billing (billing-service)

## Tasks

---

### 11.1 Scaffold and Schema

#### 11.1.1 Scaffold billing-service from template
- ID: 11.1.1
- Title: Create billing-service from the service template
- Description: Instantiate `microservices/billing-service` (port 9007, base package
  `com.telco.billing`) from the template; depend on starter-api, starter-security, starter-mediator,
  starter-observability, starter-outbox, starter-inbox; own the `billing` database. Architecture
  mode: Domain Orchestration (ADR-004) - bill-run orchestrates usage/subscription/pricing read models
  across Invoice/InvoiceLine/BillCycle aggregates; declare it in CLAUDE.md/README.
- Business Purpose: Standardized billing-domain service skeleton.
- Inputs: ADR-017.
- Outputs: billing-service skeleton building and registering.
- Acceptance Criteria:
  - Service starts, registers, exposes Swagger UI.
- Dependencies: 3.4.1, Sprint 04
- Complexity: S

#### 11.1.2 Billing schema migration
- ID: 11.1.2
- Title: Create Flyway migration for invoices, lines, bill cycles
- Description: `V1__billing.sql` creating `invoices` (id, customer_id, subscription_id, period_start,
  period_end, sub_total, tax, grand_total, currency, status, due_date, issued_at, pdf_ref),
  `invoice_lines` (id, invoice_id, description, quantity, unit_price, line_total), and `bill_cycles`
  (id, customer_id, day_of_month, next_run_date), plus outbox/inbox tables.
- Business Purpose: Persist invoices, lines, and billing schedules (FR-21, FR-22).
- Inputs: analysis Section 10.6, FR-21, FR-22.
- Outputs: Flyway migration.
- Acceptance Criteria:
  - Migration applies; invoice status supports DRAFT/ISSUED/PAID/OVERDUE; money columns are numeric
    with currency.
- Dependencies: 11.1.1
- Complexity: M

---

### 11.2 Inputs for Billing

#### 11.2.1 Maintain billing inputs from events
- ID: 11.2.1
- Title: Consume subscription and usage events into billing read models
- Description: Idempotent consumers of `subscription.activated.v1` / `subscription.suspended.v1` /
  `subscription.terminated.v1` (active subscriber set and bill cycle) and `usage.aggregated.v1`
  (period overage) and `tariff.price-changed.v1` (current pricing), maintaining local read models so
  the bill-run needs no synchronous cross-service calls.
- Business Purpose: Loose-coupled, replayable billing inputs (FR-21, FR-22, NFR-11).
- Inputs: event-catalog, FR-21, FR-22.
- Outputs: Event consumers + billing read models.
- Acceptance Criteria:
  - Active subscribers, their tariff/addons, and aggregated overage are available locally for a
    period without calling other services; consumers are idempotent.
- Dependencies: 11.1.2, 9.3.2, 10.4.3, 7.4.2
- Complexity: L

---

### 11.3 Bill-Run and Invoice Generation

#### 11.3.1 Invoice line composition
- ID: 11.3.1
- Title: Implement invoice line builder (fee, addons, overage, VAS, tax)
- Description: A domain service composing invoice lines for a subscriber-period: monthly tariff fee,
  addon fees, overage charges, VAS fees, and taxes; compute sub_total, tax, grand_total with
  `BigDecimal` and TRY currency (FR-22).
- Business Purpose: Accurate invoice composition (FR-22).
- Inputs: FR-22, analysis Section 4.6.
- Outputs: Line-composition service + tests.
- Acceptance Criteria:
  - For a subscriber with a fee, an addon, and overage, the invoice has the expected lines and totals;
    tax is applied; rounding uses a defined scale.
- Dependencies: 11.2.1
- Complexity: M

#### 11.3.2 Bill-run job and trigger endpoint
- ID: 11.3.2
- Title: Implement bill-run generating invoices for active postpaid subscribers
- Description: A bill-run process (scheduled monthly + manual `POST /api/v1/billing/runs` admin
  trigger) that, for each active postpaid subscriber due in the cycle, composes an invoice, persists
  it ISSUED, and emits `invoice.generated.v1`. Idempotent per (subscriber, period) so a re-run does
  not duplicate invoices. Must process 100K subscribers under 30 minutes (NFR-02) via batching/
  parallelism.
- Business Purpose: Automated monthly invoicing (FR-21, AC-02 step 1-2).
- Inputs: FR-21, NFR-02, event-catalog `invoice.generated.v1`, AC-02.
- Outputs: Bill-run job + admin endpoint + event.
- Acceptance Criteria:
  - Triggering a run creates one ISSUED invoice per eligible subscriber and emits
    `invoice.generated.v1`; a re-run for the same period creates no duplicates; a batched run meets
    the NFR-02 throughput target in a load test.
- Dependencies: 11.3.1, 5.5.1
- Complexity: L

#### 11.3.3 Invoice PDF rendering and storage
- ID: 11.3.3
- Title: Render invoice PDF and store the reference
- Description: Render each invoice to PDF and store it in MinIO/local FS, recording `pdf_ref` on the
  invoice; the PDF is available via the read API and referenced in notifications (FR-23).
- Business Purpose: Deliver a human-readable invoice document (FR-23, AC-02).
- Inputs: FR-23, analysis Section 7.1 (MinIO).
- Outputs: PDF renderer + storage adapter.
- Acceptance Criteria:
  - Each generated invoice has a stored PDF whose `pdf_ref` resolves; the PDF lists the invoice lines
    and totals.
- Dependencies: 11.3.2, Sprint 01 (object storage)
- Complexity: M

---

### 11.4 Payment Reconciliation

#### 11.4.1 Reconcile payments and emit invoice.paid.v1
- ID: 11.4.1
- Title: Consume payment.completed.v1 to settle invoices
- Description: Idempotent consumer of `payment.completed.v1` matching the invoice, transitioning it to
  PAID, and emitting `invoice.paid.v1` (FR-24). Optionally emit on the auto-pay path where payment
  consumes `invoice.generated.v1`.
- Business Purpose: Close the billing loop on payment (FR-24, AC-02 step 5).
- Inputs: FR-24, event-catalog `invoice.paid.v1`, AC-02.
- Outputs: Reconciliation consumer + event.
- Acceptance Criteria:
  - A matching `payment.completed.v1` moves the invoice to PAID and emits `invoice.paid.v1`; the
    consumer is idempotent.
- Dependencies: 11.3.2, 8.5.2
- Complexity: M

#### 11.4.2 Overdue detection
- ID: 11.4.2
- Title: Detect overdue invoices and emit invoice.overdue.v1
- Description: A scheduled check marking unpaid invoices past `due_date` as OVERDUE and emitting
  `invoice.overdue.v1` (consumed by notification and ticket).
- Business Purpose: Flag non-payment for dunning and support (FR-24 adjacent).
- Inputs: event-catalog `invoice.overdue.v1`.
- Outputs: Overdue scheduler + event.
- Acceptance Criteria:
  - An unpaid invoice past due is marked OVERDUE once and emits `invoice.overdue.v1`.
- Dependencies: 11.3.2
- Complexity: S

---

### 11.5 Read API

#### 11.5.1 Invoice read endpoints
- ID: 11.5.1
- Title: Implement invoice list/get/PDF endpoints
- Description: `GET /api/v1/invoices?customerId=...`, `GET /api/v1/invoices/{id}`, and
  `GET /api/v1/invoices/{id}/pdf` (streams/redirects to the stored PDF), returning `ApiResult` with
  pagination.
- Business Purpose: Invoice visibility and document retrieval (FR-23).
- Inputs: FR-23, analysis Section 8.6.
- Outputs: Queries + endpoints.
- Acceptance Criteria:
  - Listing returns a customer's invoices paginated; the PDF endpoint returns the stored document;
    unknown id returns 404.
- Dependencies: 11.3.3
- Complexity: S

---

### 11.6 Tests

#### 11.6.1 Billing integration tests and AC-02
- ID: 11.6.1
- Title: Add billing integration tests including AC-02 (Testcontainers)
- Description: Testcontainers (Postgres, Kafka) tests covering input read-model consumers, bill-run
  with idempotency, line composition correctness, PDF generation, reconciliation to PAID, overdue
  detection, and AC-02 end to end.
- Business Purpose: Verify the billing domain and AC-02 (NFR-17).
- Inputs: 11.2-11.5, AC-02.
- Outputs: Integration test suite.
- Acceptance Criteria:
  - FR-21..24 flows pass; AC-02 passes: bill-run aggregates usage, generates a PDF invoice, emits
    `invoice.generated.v1`, and on payment emits `invoice.paid.v1`.
- Dependencies: 11.4.1, 11.4.2, 11.5.1
- Complexity: L

---

## Sprint Deliverables

- billing-service (9007): event-fed read models, invoice line composition, idempotent monthly
  bill-run with manual trigger, PDF rendering/storage, payment reconciliation, overdue detection,
  invoice read APIs, and integration tests.
- AC-02 integration test.

## Exit Criteria

- AC-02 passes: a bill-run aggregates last-period usage per subscriber, generates a PDF invoice,
  emits `invoice.generated.v1` (notification consumes it in Sprint 12), and on payment emits
  `invoice.paid.v1`.
- The bill-run is idempotent per (subscriber, period) and meets the NFR-02 throughput target in a
  load test.
- FR-21, FR-22, FR-23, FR-24 pass.
</content>
