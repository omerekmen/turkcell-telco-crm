# Sprint 13 - Observability and Resilience

## Objective

Roll out end-to-end observability across all services (distributed tracing, structured logging,
metrics, dashboards, alerts) and complete the resilience posture (Resilience4j circuit breaker,
retry, bulkhead on every outbound call). The platform primitives exist from Sprint 03; this sprint
verifies they are wired in every service and meet the NFR targets.

Covers NFR-07, NFR-08, NFR-09, NFR-10, NFR-13, and supports NFR-01/NFR-04.

## Included Epics

- Epic 13: Observability and Resilience Rollout

## Tasks

---

### 13.1 Distributed Tracing

#### 13.1.1 OTel trace export wiring per service
- ID: 13.1.1
- Title: Wire OpenTelemetry trace export to the collector/Tempo in all services
- Description: Confirm every service uses starter-observability with Micrometer Tracing exporting OTLP
  to the collector (Tempo backend), including Kafka producer/consumer span propagation so traces span
  the saga across services (NFR-07, roadmap BL-03).
- Business Purpose: A single trace follows a request and its events across all services (NFR-07).
- Inputs: ADR-012, PLATFORM-SPEC Section 9.6, roadmap BL-03.
- Outputs: Verified OTLP export config + Kafka span propagation.
- Acceptance Criteria:
  - An onboarding request produces one connected trace in Tempo spanning gateway, order, payment, and
    subscription, including Kafka spans.
- Dependencies: Sprints 04-12, 3.2.6
- Complexity: M

#### 13.1.2 traceId/correlationId propagation verification
- ID: 13.1.2
- Title: Verify traceId/correlationId on every request and log line
- Description: Confirm the gateway injects correlationId, starter-observability propagates trace/
  correlation into MDC, and both appear in every service log and trace (NFR-13).
- Business Purpose: 100% request traceability (NFR-13).
- Inputs: NFR-13.
- Outputs: Verification tests/checks.
- Acceptance Criteria:
  - Sampled logs across all services contain `traceId` and `correlationId`; the gateway-issued
    correlationId matches the value seen downstream and in the trace.
- Dependencies: 13.1.1, 4.3.4
- Complexity: S

---

### 13.2 Logging

#### 13.2.1 Structured JSON logging to Loki across services
- ID: 13.2.1
- Title: Confirm structured JSON logs ship to Loki for all services
- Description: Ensure every service uses the shared logback JSON config (3.2.7) and that logs are
  collected into Loki with service/trace labels; PII masking is active everywhere (NFR-08, ADR-021).
- Business Purpose: Centralized, queryable, PII-safe logs (NFR-08).
- Inputs: ADR-012, ADR-021, 3.2.7.
- Outputs: Verified Loki ingestion + masking.
- Acceptance Criteria:
  - Loki shows JSON logs from every service labeled by service and traceId; a known PII value never
    appears unmasked in any service's logs.
- Dependencies: 3.2.7, Sprints 04-12
- Complexity: M

---

### 13.3 Metrics, Dashboards, Alerts

#### 13.3.1 Metrics exposure and Prometheus scraping
- ID: 13.3.1
- Title: Expose Micrometer metrics and scrape with Prometheus
- Description: Ensure every service exposes `/actuator/prometheus` (HTTP latency, JVM, Kafka
  consumer lag, Resilience4j metrics) and Prometheus scrapes them (NFR-09).
- Business Purpose: Quantitative health and performance signals (NFR-09).
- Inputs: ADR-012, NFR-09.
- Outputs: Metrics endpoints + Prometheus scrape config.
- Acceptance Criteria:
  - Prometheus lists all services as up and collects HTTP, JVM, and Resilience4j metrics.
- Dependencies: Sprints 04-12
- Complexity: M

#### 13.3.2 Grafana dashboards
- ID: 13.3.2
- Title: Provision Grafana dashboards for platform and domains
- Description: Provision dashboards for API latency (p50/p95/p99 vs NFR-01), error rates, Kafka
  consumer lag, saga throughput, bill-run duration (NFR-02), and circuit-breaker state.
- Business Purpose: Operoverview of platform health and NFR adherence (NFR-01, NFR-02, NFR-09).
- Inputs: ADR-012, NFR-01, NFR-02.
- Outputs: Provisioned Grafana dashboards.
- Acceptance Criteria:
  - Dashboards render with live data; p95 latency and bill-run duration panels exist and populate.
- Dependencies: 13.3.1
- Complexity: M

#### 13.3.3 Alert rules
- ID: 13.3.3
- Title: Define alerting rules for SLO breaches
- Description: Prometheus/Grafana alert rules for p95 > 300ms sustained, error-rate spikes, consumer
  lag growth, and breaker-open state, aligned to the 99.5% uptime objective (NFR-01, NFR-04).
- Business Purpose: Proactive detection of SLO violations (NFR-01, NFR-04).
- Inputs: NFR-01, NFR-04.
- Outputs: Alert rule definitions.
- Acceptance Criteria:
  - A simulated latency/error breach fires the corresponding alert.
- Dependencies: 13.3.2
- Complexity: S

---

### 13.4 Resilience

#### 13.4.1 Resilience4j on all outbound calls
- ID: 13.4.1
- Title: Apply circuit breaker, retry, and bulkhead to every external call
- Description: Ensure every synchronous outbound call (Feign clients, PSP, object storage) and
  critical resource pool is wrapped with Resilience4j circuit breaker, retry, and bulkhead, surfacing
  `DependencyFailureException` on open circuits (NFR-10). Centralize config defaults.
- Business Purpose: Prevent cascading failures across services (NFR-10).
- Inputs: ADR-005, NFR-10, analysis Section 5.
- Outputs: Resilience4j config + annotations/wrappers per service.
- Acceptance Criteria:
  - With a downstream forced to fail, the breaker opens and the caller degrades gracefully with a
    mapped error; metrics show breaker state transitions.
- Dependencies: 8.2.2, 8.5.1, Sprints 04-12
- Complexity: L

#### 13.4.2 Resilience integration tests
- ID: 13.4.2
- Title: Add resilience behavior tests
- Description: Tests injecting downstream failure/latency to assert breaker open/half-open/closed
  transitions, retry counts, and bulkhead rejection.
- Business Purpose: Verify resilience configuration behaves as intended (NFR-10, NFR-17).
- Inputs: 13.4.1.
- Outputs: Resilience test suite.
- Acceptance Criteria:
  - Tests confirm breaker transitions, bounded retries, and bulkhead limits for representative calls.
- Dependencies: 13.4.1
- Complexity: M

---

## Sprint Deliverables

- End-to-end distributed tracing (including Kafka spans) into Tempo, structured PII-masked JSON logs
  into Loki, Prometheus metrics with Grafana dashboards and alerts, all verified across every service.
- Resilience4j circuit breaker/retry/bulkhead on all outbound calls with behavior tests.

## Exit Criteria

- A single onboarding trace spans all involved services; every request log carries traceId/correlationId.
- Dashboards show p95 latency, bill-run duration, consumer lag, and breaker state with live data;
  SLO-breach alerts fire on simulated breaches.
- All outbound calls are resilience-guarded and verified.
- NFR-07, NFR-08, NFR-09, NFR-10, NFR-13 satisfied.
</content>
