# Sprint 14 - Testing and Hardening

## Objective

Raise the platform to production quality: full acceptance-criteria validation, contract testing of
event schemas and APIs, security hardening (PII-at-rest encryption coverage, audit-log completeness,
PII telemetry masking, mTLS posture decision), and performance validation against the NFR targets.

Covers NFR-01, NFR-02, NFR-06, NFR-12, NFR-16, NFR-17 and final validation of AC-01/02/03.

## Included Epics

- Epic 14: Quality, Security, and Performance Hardening

## Tasks

---

### 14.1 Acceptance and End-to-End Testing

#### 14.1.1 Full acceptance suite (AC-01, AC-02, AC-03)
- ID: 14.1.1
- Title: Build a cross-service acceptance test suite
- Description: An automated end-to-end suite (Testcontainers or compose-based) running all three
  acceptance scenarios against the full service set through the gateway, including failure/
  compensation variants.
- Business Purpose: Prove the MVP delivers its committed business outcomes (AC-01/02/03).
- Inputs: requirements Section 5 (AC-01/02/03), Sprints 09/10/11/12.
- Outputs: Acceptance test suite + CI stage.
- Acceptance Criteria:
  - AC-01, AC-02, AC-03 all pass through the gateway end to end, including AC-01 compensation; the
    suite runs in CI.
- Dependencies: 9.5.2, 10.7.1, 11.6.1, 12.6.1
- Complexity: L

#### 14.1.2 Contract tests for events and APIs
- ID: 14.1.2
- Title: Add event-schema and API contract tests
- Description: Producer/consumer contract tests for each Avro event (schema-registry backward
  compatibility, required fields) and API contract tests for cross-service Feign calls, preventing
  breaking changes (NFR-16).
- Business Purpose: Lock service boundaries against accidental breakage (NFR-16).
- Inputs: ADR-019, NFR-16, event-catalog.
- Outputs: Contract test suite.
- Acceptance Criteria:
  - A breaking event/API change fails a contract test; all current contracts pass.
- Dependencies: 3.3.2, Sprints 06-12
- Complexity: M

#### 14.1.3 Coverage gate
- ID: 14.1.3
- Title: Enforce minimum test coverage in CI
- Description: Configure JaCoCo coverage thresholds per module bound to CI; merges blocked below the
  agreed threshold (NFR-17).
- Business Purpose: Sustained test discipline (NFR-17).
- Inputs: ADR-013, NFR-17.
- Outputs: Coverage config + CI gate.
- Acceptance Criteria:
  - CI fails a PR that drops a module below its coverage threshold.
- Dependencies: 1.4.1
- Complexity: S

---

### 14.2 Security Hardening

#### 14.2.1 PII-at-rest encryption audit
- ID: 14.2.1
- Title: Verify AES-GCM encryption of all PII at rest
- Description: Audit every service storing PII (customer TCKN, payment card data) to confirm AES-GCM
  encryption with keys from secrets, and that no PII column stores plaintext (NFR-06).
- Business Purpose: Regulatory PII protection (NFR-06, KVKK/GDPR).
- Inputs: NFR-06, ADR-011, 6.2.3.
- Outputs: Encryption audit + fixes.
- Acceptance Criteria:
  - A DB inspection confirms all PII columns are ciphertext; a key-rotation procedure is documented.
- Dependencies: 6.2.3, 8.4.2
- Complexity: M

#### 14.2.2 PII telemetry masking audit
- ID: 14.2.2
- Title: Verify PII masking across logs, traces, and metrics
- Description: Confirm the masking converter (ADR-021) redacts TCKN, card number, MSISDN, and email
  in all logs, trace attributes, and metric labels across every service.
- Business Purpose: No PII leakage into observability (ADR-021, NFR-12).
- Inputs: ADR-021, 3.2.7, 13.2.1.
- Outputs: Masking audit + fixes.
- Acceptance Criteria:
  - Seeded PII values never appear unmasked in Loki, Tempo, or Prometheus for any service.
- Dependencies: 13.2.1
- Complexity: M

#### 14.2.3 Audit-log completeness
- ID: 14.2.3
- Title: Verify audit logging in identity, customer, payment, subscription
- Description: Confirm every state-changing operation in the four mandated services writes an audit
  row with actor, action, entity, and correlationId (NFR-12).
- Business Purpose: Complete regulatory audit trail (NFR-12).
- Inputs: NFR-12, 5.6.1.
- Outputs: Audit-coverage verification + fixes.
- Acceptance Criteria:
  - A representative state change in each of the four services produces a correct audit row; gaps are
    closed.
- Dependencies: 5.6.1, 6.3.5, 8.5.2, 9.3.1
- Complexity: M

#### 14.2.4 mTLS posture and security review
- ID: 14.2.4
- Title: Document mTLS decision and run a security review
- Description: Document the gateway-behind-trust model and the deferred-mTLS decision for MVP (per
  analysis Section 13), and run a dependency/security review (token handling, rate limiting, input
  validation, error leakage).
- Business Purpose: Explicit, reviewed security posture (NFR-05, ADR-011).
- Inputs: analysis Section 13, ADR-011.
- Outputs: Security posture doc + review findings + fixes.
- Acceptance Criteria:
  - The mTLS deferral is documented with the production recommendation; no high-severity findings
    remain open; error responses never leak stack traces.
- Dependencies: Sprints 04-13
- Complexity: M

---

### 14.3 Performance Validation

#### 14.3.1 API latency load test (NFR-01)
- ID: 14.3.1
- Title: Validate p95 API latency < 300ms under load
- Description: A load test (k6/Gatling) exercising representative read/write endpoints through the
  gateway, asserting p95 < 300ms at the target concurrency (NFR-01).
- Business Purpose: Meet the latency SLO (NFR-01).
- Inputs: NFR-01.
- Outputs: Load test scripts + report.
- Acceptance Criteria:
  - p95 latency for the tested endpoints is < 300ms at target load; results are recorded in the
    dashboard.
- Dependencies: 13.3.2
- Complexity: M

#### 14.3.2 Bill-run throughput test (NFR-02)
- ID: 14.3.2
- Title: Validate bill-run for 100K subscribers < 30 minutes
- Description: Seed 100K active postpaid subscribers and measure a full bill-run, tuning batching/
  parallelism to meet the target (NFR-02).
- Business Purpose: Meet the bill-run throughput SLO (NFR-02).
- Inputs: NFR-02, 11.3.2.
- Outputs: Seed harness + bill-run performance report.
- Acceptance Criteria:
  - A 100K-subscriber bill-run completes in under 30 minutes with no duplicate invoices.
- Dependencies: 11.3.2
- Complexity: L

---

## Sprint Deliverables

- Automated acceptance suite (AC-01/02/03 incl. compensation), event/API contract tests, and a
  coverage gate in CI.
- Security hardening: verified PII encryption at rest, PII telemetry masking, audit-log completeness,
  and a documented mTLS/security posture.
- Performance validation against NFR-01 (p95 latency) and NFR-02 (bill-run throughput).

## Exit Criteria

- All MVP acceptance criteria pass end to end in CI; contract tests guard event/API boundaries.
- PII is encrypted at rest and masked in telemetry everywhere; audit logging is complete in the four
  mandated services; no high-severity security findings remain.
- NFR-01 and NFR-02 targets are met and recorded.
</content>
