# Sprint 03 - Platform Starters and Event Contracts

## Objective

Expose `platform-core` as Spring Boot starters (the only thing services depend on, ADR-018), define
the Avro event contracts and shared envelope (ADR-019), and ship the service template plus a
reference service that proves the whole stack wires together. After this sprint a new domain service
is a copy-and-fill exercise.

## Included Epics

- Epic 3: Platform Starters, Event Contracts, and Service Template

## Cross-cutting constraints

- Spring lives ONLY in `platform-autoconfigure` and `platform-starters/*`. Each starter ships
  `@AutoConfiguration` classes, conditional beans, typed `@ConfigurationProperties`, and an
  `AutoConfiguration.imports` file. Property prefix root `telco.platform`. No business logic.

## Tasks

---

### 3.1 Shared Autoconfigure

#### 3.1.1 platform-autoconfigure primitives
- ID: 3.1.1
- Title: Implement PlatformProperties and Jackson autoconfiguration
- Description: In `com.telco.platform.autoconfigure`: `PlatformProperties` constants (prefix root
  `telco.platform`); `PlatformJacksonAutoConfiguration` registering a
  `Jackson2ObjectMapperBuilderCustomizer` (JavaTimeModule, `WRITE_DATES_AS_TIMESTAMPS=false`,
  `NON_NULL`). Register via `META-INF/spring/.../AutoConfiguration.imports`.
- Business Purpose: One consistent JSON/time policy across all services.
- Inputs: PLATFORM-SPEC Section 8.
- Outputs: Autoconfigure module classes + imports file.
- Acceptance Criteria:
  - A test `ApplicationContextRunner` loads the autoconfig and serializes an `Instant` as ISO-8601,
    not a timestamp.
- Dependencies: Sprint 02 complete
- Complexity: S

---

### 3.2 Starters

#### 3.2.1 starter-api
- ID: 3.2.1
- Title: Implement GlobalExceptionHandler and ApiResult wiring
- Description: `@RestControllerAdvice GlobalExceptionHandler` mapping each `PlatformException`
  subtype to its HTTP status and `ApiResult.failure(...)`, populating `ApiMeta` from
  `CorrelationContextHolder` and the request path; map `MethodArgumentNotValidException`/
  `ConstraintViolationException` to 400; fall back to 500 `INTERNAL_ERROR` without leaking stack
  traces. Conditional on web; property `telco.platform.api.enabled` (default true).
- Business Purpose: Uniform RFC-7807-aligned error responses everywhere (NFR-14, ADR-015).
- Inputs: PLATFORM-SPEC Section 9.1.
- Outputs: starter-api autoconfig + handler.
- Acceptance Criteria:
  - `ResourceNotFoundException` -> 404 with `ApiResult.failure` and a populated `ApiMeta`.
  - A bean-validation failure -> 400; an unmapped exception -> 500 with no stack trace in the body.
- Dependencies: 2.1.1, 2.1.2, 2.1.3
- Complexity: M

#### 3.2.2 starter-mediator
- ID: 3.2.2
- Title: Implement MediatorAutoConfiguration and Spring HandlerRegistry
- Description: `MediatorAutoConfiguration` providing a Spring `HandlerRegistry` (resolving
  CommandHandler/QueryHandler/EventHandler beans), the `Mediator` bean (`InProcessMediator`), and
  behavior beans: Performance/Logging/Authorization always-on; Validation
  `@ConditionalOnBean(Validator.class)`; Transaction `@ConditionalOnBean(PlatformTransactionManager)`
  with a `SpringTransactionRunner`. Order config AFTER Hibernate/DataSource/Transaction/Validation
  autoconfig. Default `CurrentUserProvider` `@ConditionalOnMissingBean`. Props
  `telco.platform.mediator.*` (e.g. `performance.slow-threshold-ms` default 500).
- Business Purpose: Turn the pure mediator into a wired Spring dispatch pipeline.
- Inputs: PLATFORM-SPEC Section 9.2.
- Outputs: starter-mediator autoconfig.
- Acceptance Criteria:
  - Context test: a registered `CommandHandler` bean is invoked via `Mediator.send`; behaviors run
    in pipeline order; Transaction behavior present only when a transaction manager exists.
- Dependencies: 2.3.2, 2.3.3
- Complexity: L

#### 3.2.3 starter-security
- ID: 3.2.3
- Title: Implement JWT/gateway-trust security autoconfiguration
- Description: `JwtProperties` (`telco.platform.security.jwt.*`); `JwtService` (validate + parse
  claims, optional issue helper); `JwtAuthFilter` (`OncePerRequestFilter`): when
  `telco.platform.security.gateway-trust.enabled`, trust `X-User-Id`/`X-User-Roles`; otherwise
  validate the Bearer JWT. Populate `UserContextHolder` and Spring `SecurityContext`; provide a
  `CurrentUserProvider` bean (overrides mediator default). `SecurityAutoConfiguration` conditional on
  `telco.platform.security.enabled` and security/web classes.
- Business Purpose: Gateway-behind-trust authentication and identity propagation (NFR-05, FR-IAM-03).
- Inputs: PLATFORM-SPEC Section 9.3, ADR-011.
- Outputs: starter-security autoconfig + filter + JwtService.
- Acceptance Criteria:
  - With gateway-trust on, a request carrying `X-User-Id`/`X-User-Roles` populates `UserContext`.
  - With gateway-trust off, a valid Bearer JWT authenticates and an invalid one is rejected.
- Dependencies: 2.1.3
- Complexity: L

#### 3.2.4 starter-outbox
- ID: 3.2.4
- Title: Implement JDBC outbox store, serializer, and migration
- Description: `JdbcOutboxStore` (spring-jdbc), `JacksonEventSerializer`, `OutboxAutoConfiguration`
  wiring `DefaultOutboxService`. Flyway migration under `db/migration/platform/` (V900+) creating a
  Debezium-friendly `outbox` table (id uuid, aggregate_type, aggregate_id, event_type, payload jsonb,
  headers jsonb, trace_id, correlation_id, created_at timestamptz, status) indexed on
  `(status, created_at)`. Optional disabled relay scheduler. Props `telco.platform.outbox.*`.
- Business Purpose: Persist event intent atomically with domain writes (ARC-05).
- Inputs: PLATFORM-SPEC Sections 9.4, 10.
- Outputs: starter-outbox autoconfig, JDBC store, Flyway migration.
- Acceptance Criteria:
  - Testcontainers Postgres: `OutboxService.publish` writes a row with status NEW; the migration
    creates the table and index with `CREATE TABLE IF NOT EXISTS`.
- Dependencies: 2.4.1
- Complexity: M

#### 3.2.5 starter-inbox
- ID: 3.2.5
- Title: Implement JDBC inbox store, behavior wiring, and migration
- Description: `JdbcInboxStore` (spring-jdbc), `InboxAutoConfiguration` wiring `DefaultInboxService`
  and contributing `InboxBehavior` as a `PipelineBehavior` bean. Flyway migration creating an
  `inbox` table (message_id, handler, processed_at, primary key (message_id, handler)). Props
  `telco.platform.inbox.*`.
- Business Purpose: Idempotent consumption guard available to every service via the mediator.
- Inputs: PLATFORM-SPEC Sections 9.5, 10.
- Outputs: starter-inbox autoconfig, JDBC store, Flyway migration.
- Acceptance Criteria:
  - Testcontainers Postgres: first `markProcessed` returns true, a duplicate returns false;
    `InboxBehavior` is registered as a behavior bean.
- Dependencies: 2.5.1, 3.2.2
- Complexity: M

#### 3.2.6 starter-observability
- ID: 3.2.6
- Title: Implement correlation filter and tracing customizer
- Description: `CorrelationFilter` (`OncePerRequestFilter`): read or generate `X-Correlation-Id`, set
  `CorrelationContextHolder` + MDC (`traceId`, `correlationId`), echo the header on the response, and
  clear on completion. `ObservabilityAutoConfiguration` registers the filter (conditional on web) and
  a Micrometer tracing customizer (conditional on micrometer classes) exporting OTLP to the collector.
  Props `telco.platform.observability.*` (e.g. `correlation.enabled` default true).
- Business Purpose: Every request carries traceId/correlationId into logs and traces (NFR-07/08/13).
- Inputs: PLATFORM-SPEC Section 9.6, ADR-012.
- Outputs: starter-observability autoconfig + filter.
- Acceptance Criteria:
  - A request without `X-Correlation-Id` gets one generated and echoed on the response; MDC keys are
    populated during handling and cleared after.
- Dependencies: 2.1.3
- Complexity: M

#### 3.2.7 Logback structured-JSON and PII masking
- ID: 3.2.7
- Title: Provide shared logback-spring JSON config with PII masking
- Description: Ship a shared `logback-spring.xml` (in starter-observability or a logging starter)
  emitting structured JSON with MDC trace/correlation fields, and a masking converter that redacts
  PII (TCKN, card number, MSISDN, email) per ADR-021.
- Business Purpose: Centralized structured logging to Loki with no PII leakage (NFR-08, ADR-021).
- Inputs: ADR-012, ADR-021.
- Outputs: Shared logback config + masking converter + unit test.
- Acceptance Criteria:
  - Log output is valid JSON containing `traceId`/`correlationId`; a logged TCKN/card number appears
    masked (unit test asserts the raw value is absent).
- Dependencies: 3.2.6
- Complexity: M

---

### 3.3 Event Contracts (Avro)

#### 3.3.1 Event envelope and MVP schemas
- ID: 3.3.1
- Title: Define EventEnvelope and core MVP Avro schemas
- Description: In `platform-event-contracts`, Avro schemas under `src/main/avro/*.avsc`, namespace
  `com.telco.platform.events.<domain>`. Define shared `EventEnvelope.avsc` (eventId, eventType,
  occurredAt timestamp-millis, traceId, correlationId, payload) and the MVP schemas matching the
  event catalog: `customer-registered`, `customer-kyc-approved`, `order-created`, `payment-completed`,
  `payment-failed`, `subscription-activated`, `subscription-suspended`, `invoice-generated`,
  `quota-threshold-reached`, `quota-exceeded`, `ticket-opened`. Versioned record names end in `V1`;
  fields nullable-friendly for backward compatibility. Generate via avro-maven-plugin.
- Business Purpose: A single governed schema source for all cross-service events (NFR-16, ADR-019).
- Inputs: PLATFORM-SPEC Section 7, `docs/architecture/event-catalog.md`.
- Outputs: `.avsc` files + generated Java records.
- Acceptance Criteria:
  - `mvn -q generate-sources` produces `*V1` Java classes; each schema declares `domain.event.v1`
    naming and nullable defaults.
- Dependencies: Sprint 01 (Schema Registry available)
- Complexity: M

#### 3.3.2 Schema Registry compatibility check in build
- ID: 3.3.2
- Title: Wire Schema Registry backward-compatibility verification
- Description: Add a Maven goal (schema-registry-maven-plugin or equivalent) that validates each
  `.avsc` against the registry for BACKWARD compatibility on `verify`, and a script to register
  schemas. Fail the build on an incompatible change (ADR-019).
- Business Purpose: Prevent breaking event-contract changes from merging (NFR-16).
- Inputs: ADR-019.
- Outputs: Compatibility-check plugin binding + registration script.
- Acceptance Criteria:
  - A deliberately incompatible schema edit fails `mvn verify`; a compatible additive change passes.
- Dependencies: 3.3.1
- Complexity: M

---

### 3.4 Service Template and Reference Service

#### 3.4.1 Service template
- ID: 3.4.1
- Title: Create the standard service template (ADR-017)
- Description: `microservices/service-template` with the standard layout (`api`, `application`,
  `domain`, `infrastructure`), `pom.xml` inheriting the BOM and depending only on starters, a
  `CLAUDE.md` declaring `Architecture Mode: CQRS + MEDIATOR`, an `application.yml` (port, datasource,
  Flyway locations including `classpath:db/migration/platform`, OTLP exporter), a Dockerfile, a
  sample command/query/handler, and a controller returning `ApiResult`.
- Business Purpose: One-command basis for every domain service; enforces ADR-017 structure.
- Inputs: ADR-017, ADR-018, PLATFORM-SPEC.
- Outputs: `microservices/service-template` skeleton.
- Acceptance Criteria:
  - The template builds; its sample endpoint returns `ApiResult` via the mediator; it depends on no
    `platform-core` module directly.
- Dependencies: 3.2.1, 3.2.2, 3.2.3, 3.2.6
- Complexity: M

#### 3.4.2 Reference service end-to-end
- ID: 3.4.2
- Title: Build reference-service proving the full stack
- Description: `microservices/reference-service` derived from the template with a real entity
  (DemoItem), Flyway migration, a create command publishing a `demo-item.created.v1` event via the
  outbox, list/get queries, and integration tests using Testcontainers (Postgres + Kafka).
- Business Purpose: Living proof that mediator, outbox, inbox, security, observability, and Flyway
  all work together; the canonical example for domain teams.
- Inputs: 3.2.x, 3.3.1.
- Outputs: reference-service with migrations, handlers, outbox publish, integration tests.
- Acceptance Criteria:
  - Integration test: POST creates an item, persists it, writes an outbox row, and returns
    `ApiResult`; the list query returns it. Build and tests green.
- Dependencies: 3.2.4, 3.2.5, 3.2.7, 3.3.1, 3.4.1
- Complexity: L

#### 3.4.3 Starter integration (context-wiring) tests
- ID: 3.4.3
- Title: Add ApplicationContextRunner wiring tests per starter
- Description: For each starter, add `ApplicationContextRunner` tests asserting beans are created
  under the expected conditions and absent when disabled (BL-02 in the roadmap backlog).
- Business Purpose: Verify autoconfiguration behavior beyond mere compilation.
- Inputs: roadmap BL-02.
- Outputs: Context-wiring test per starter.
- Acceptance Criteria:
  - Each starter has a passing test asserting bean presence/absence per `@ConditionalOn...`.
- Dependencies: 3.2.1, 3.2.2, 3.2.3, 3.2.4, 3.2.5, 3.2.6
- Complexity: M

---

## Sprint Deliverables

- Six Spring Boot starters (api, mediator, security, outbox, inbox, observability) plus shared
  autoconfigure and structured-JSON/PII-masking logging.
- Avro event contracts with envelope, MVP schemas, and Schema Registry compatibility gating.
- Service template (ADR-017) and a working reference-service with Testcontainers integration tests.

## Exit Criteria

- A new service can be created from the template depending only on starters and immediately get
  ApiResult, mediator pipeline, outbox/inbox, security, and correlation/tracing for free.
- reference-service integration tests pass against Testcontainers Postgres + Kafka.
- An incompatible Avro change fails the build; structured logs are JSON with PII masked.
</content>
