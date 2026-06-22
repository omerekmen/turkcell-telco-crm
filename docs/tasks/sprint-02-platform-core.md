# Sprint 02 - Platform Core Libraries

## Objective

Build the framework-agnostic internal platform (`platform-core/*`): the response/exception/context
primitives, the CQRS contracts, the in-process Mediator with its pipeline behaviors, and the
outbox/inbox cores. These modules MUST NOT import Spring (PLATFORM-SPEC hard rules). Starters
(Sprint 03) wrap them for Spring Boot.

## Included Epics

- Epic 2: Platform Core Libraries (`platform-common`, `platform-cqrs`, `platform-mediator`,
  `platform-outbox`, `platform-inbox`)

## Cross-cutting constraints (apply to all tasks here)

- `platform-core/*` may import only: JDK, slf4j-api, jakarta.validation-api (optional), jakarta
  annotations, Jackson annotations. No Spring.
- GroupId `com.telco.platform`, version `1.0.0-SNAPSHOT`. Base packages per PLATFORM-SPEC Section 1.
- Records for immutable value types; every public type gets a short Javadoc.

## Tasks

---

### 2.1 platform-common

#### 2.1.1 API response contract
- ID: 2.1.1
- Title: Implement ApiResult, ApiError, ApiMeta, PageResult, CursorPage
- Description: In package `com.telco.platform.common.api`, implement `ApiResult<T>(success, data,
  error, meta)` with `ok(...)`/`failure(...)` factories; `ApiError(code, message, details, traceId)`;
  `ApiMeta(traceId, correlationId, timestamp, service, path)`; `PageResult<T>` (offset); `CursorPage<T>`
  (cursor). Annotate with `@JsonInclude(NON_NULL)`.
- Business Purpose: Universal external response envelope (NFR-14, ADR-015).
- Inputs: PLATFORM-SPEC Section 2.1, ADR-015.
- Outputs: Records under `common.api`.
- Acceptance Criteria:
  - `ApiResult.ok(data, meta).success()` is true and `error()` is null; `failure(...)` inverse.
  - Serializing a result omits null fields (unit test asserts JSON has no null keys).
- Dependencies: none (Sprint 01 build available)
- Complexity: S

#### 2.1.2 Exception hierarchy and error codes
- ID: 2.1.2
- Title: Implement sealed PlatformException hierarchy and ErrorCode
- Description: In `com.telco.platform.common.exception`, sealed `PlatformException extends
  RuntimeException` carrying `ErrorCode code` and optional `Map<String,Object> details`. Permitted
  subtypes: `ResourceNotFoundException`, `ValidationException`, `ConflictException`,
  `UnauthenticatedException`, `AccessDeniedException`, `BusinessRuleException`,
  `DependencyFailureException`. `ErrorCode` interface (`String code()`) plus `CommonErrorCode` enum
  (RESOURCE_NOT_FOUND, VALIDATION_FAILED, CONFLICT, UNAUTHENTICATED, ACCESS_DENIED,
  BUSINESS_RULE_VIOLATION, DEPENDENCY_FAILURE, INTERNAL_ERROR). HTTP mapping lives in starter-api,
  not here.
- Business Purpose: Consistent, typed domain failures mapped to stable error codes.
- Inputs: PLATFORM-SPEC Section 2.2.
- Outputs: Sealed exception types, `ErrorCode`, `CommonErrorCode`.
- Acceptance Criteria:
  - Hierarchy compiles as sealed with exactly the listed permitted subtypes.
  - `ValidationException` carries field violations in `details`.
- Dependencies: none
- Complexity: M

#### 2.1.3 Context and correlation primitives
- ID: 2.1.3
- Title: Implement UserContext, CorrelationContext, holders, and constants
- Description: In `com.telco.platform.common.context`: `UserContext(userId, roles, tenantId)` with
  `hasRole(String)` and `anonymous()`; `UserContextHolder` (ThreadLocal set/get/clear);
  `CorrelationContext(traceId, correlationId)` and `CorrelationContextHolder`; `CurrentUserProvider`
  interface defaulting to anonymous; `CorrelationConstants` (header and MDC key names). Pure
  ThreadLocal, no Spring.
- Business Purpose: Carry identity and correlation across the request without framework coupling
  (NFR-13).
- Inputs: PLATFORM-SPEC Section 2.3.
- Outputs: Context records, holders, provider interface, constants.
- Acceptance Criteria:
  - Setting then clearing a holder leaves `get()` empty; `anonymous()` has no roles.
  - Constant values match PLATFORM-SPEC (`X-Correlation-Id`, `X-User-Id`, `X-User-Roles`, MDC keys).
- Dependencies: none
- Complexity: S

---

### 2.2 platform-cqrs

#### 2.2.1 CQRS marker and handler contracts
- ID: 2.2.1
- Title: Implement Request/Command/Query/Event and handler interfaces
- Description: In `com.telco.platform.cqrs`: `Request<R>`, `Command<R> extends Request<R>`,
  `Query<R> extends Request<R>`, `Event` (marker), `CommandHandler<C,R>`, `QueryHandler<Q,R>`,
  `EventHandler<E>`, and a `Unit` singleton for void commands. No logic, no Spring, no annotations.
- Business Purpose: The contract surface every domain service implements (ARC-03).
- Inputs: PLATFORM-SPEC Section 3.
- Outputs: CQRS interfaces and `Unit`.
- Acceptance Criteria:
  - A sample `Command<Unit>` and handler compile against the contracts in a unit test.
- Dependencies: none
- Complexity: S

---

### 2.3 platform-mediator

#### 2.3.1 Pipeline contracts and ordering
- ID: 2.3.1
- Title: Implement RequestHandlerDelegate, PipelineBehavior, PipelineOrder
- Description: In `com.telco.platform.mediator.pipeline`: functional `RequestHandlerDelegate<R>`;
  `PipelineBehavior` (`supports`, `handle`, default `order()`); `PipelineOrder` constants
  (VALIDATION 100, AUTHORIZATION 200, LOGGING 300, INBOX 350, TRANSACTION 400, PERFORMANCE 500,
  DEFAULT 1000; lower = outer).
- Business Purpose: Defines the cross-cutting behavior chain order.
- Inputs: PLATFORM-SPEC Section 4.1.
- Outputs: Pipeline contracts and order constants.
- Acceptance Criteria:
  - Constants equal the PLATFORM-SPEC values and lower order wraps outer.
- Dependencies: 2.2.1
- Complexity: S

#### 2.3.2 Mediator dispatcher
- ID: 2.3.2
- Title: Implement Mediator, HandlerRegistry, InProcessMediator
- Description: In `com.telco.platform.mediator`: `Mediator` (`send`/`query`/`publish`),
  `HandlerRegistry` (resolution port, implemented later by the starter), `InProcessMediator` that
  sorts behaviors ascending by `order()`, builds the chain (index 0 outermost), applies only
  supporting behaviors, then dispatches to the registry handler. Missing command/query handler ->
  `IllegalStateException`; events with no handlers are a no-op. Sort via
  `Comparator.comparingInt(PipelineBehavior::order)`. No Spring.
- Business Purpose: Single dispatch point routing all domain operations through behaviors (ARC-03).
- Inputs: PLATFORM-SPEC Section 4.2.
- Outputs: `Mediator`, `HandlerRegistry`, `InProcessMediator`.
- Acceptance Criteria:
  - Unit test: behaviors execute in ascending-order nesting; a non-supporting behavior is skipped.
  - Missing handler throws `IllegalStateException`; publishing an event with no handlers does not throw.
- Dependencies: 2.3.1
- Complexity: L

#### 2.3.3 Pipeline behaviors and support ports
- ID: 2.3.3
- Title: Implement Validation, Authorization, Logging, Performance, Transaction behaviors
- Description: In `com.telco.platform.mediator.behavior`: `ValidationBehavior` (jakarta `Validator`,
  throws `ValidationException` with violations); `AuthorizationBehavior(CurrentUserProvider,
  List<AuthorizationRule>)`; `LoggingBehavior(serviceName, List<RequestLogWriter>)` skipping
  `NotLoggable`; `PerformanceBehavior(slowThresholdMs)`; `TransactionBehavior(TransactionRunner)`
  wrapping only `Command`. Support ports in `behavior.support`: `TransactionRunner`,
  `AuthorizationRule`, `RequestLogWriter`, `RequestLogEntry`, `NotLoggable`. Provide
  `Slf4jRequestLogWriter`.
- Business Purpose: Cross-cutting validation, authz, logging, timing, and transactionality applied
  uniformly to every request.
- Inputs: PLATFORM-SPEC Section 4.3.
- Outputs: Behavior classes, support ports, `Slf4jRequestLogWriter`.
- Acceptance Criteria:
  - Validation behavior throws on an invalid bean and passes a valid one.
  - Transaction behavior `supports` returns true only for `Command`.
  - Authorization behavior throws `AccessDeniedException`/`UnauthenticatedException` per the rule.
- Dependencies: 2.3.2, 2.1.2, 2.1.3
- Complexity: L

---

### 2.4 platform-outbox

#### 2.4.1 Outbox core types and service
- ID: 2.4.1
- Title: Implement OutboxRecord, OutboxStatus, OutboxStore, OutboxService
- Description: In `com.telco.platform.outbox`: `OutboxStatus` (NEW, PUBLISHED, FAILED);
  `OutboxRecord` (id, aggregateType, aggregateId, eventType, payload, headers, traceId,
  correlationId, createdAt, status); `EventSerializer` port; `OutboxStore` port (append,
  findByStatus, markPublished, markFailed); `OutboxService.publish(...)`; `DefaultOutboxService`
  that serializes the payload, captures correlation/trace from `CorrelationContextHolder`, and
  appends within the caller's transaction. `eventType` MUST follow `domain.event.v1`.
- Business Purpose: Atomic DB-write-plus-event-intent so Debezium can deliver reliably (ARC-05).
- Inputs: PLATFORM-SPEC Section 5.
- Outputs: Outbox core types and `DefaultOutboxService`.
- Acceptance Criteria:
  - `DefaultOutboxService.publish` builds an `OutboxRecord` with status NEW and a `domain.event.v1`
    eventType, capturing current trace/correlation (unit test with a fake store and context).
- Dependencies: 2.1.3
- Complexity: M

---

### 2.5 platform-inbox

#### 2.5.1 Inbox core types and behavior
- ID: 2.5.1
- Title: Implement IdempotentRequest, InboxStore, InboxService, InboxBehavior
- Description: In `com.telco.platform.inbox`: `IdempotentRequest` marker (`idempotencyKey()`);
  `InboxStore.markProcessed(messageId, handler)` returning true when newly inserted; `InboxService`
  + `DefaultInboxService`; `InboxBehavior` (`order = INBOX`) that supports `IdempotentRequest` and
  short-circuits (returns null) when not first-seen.
- Business Purpose: Exactly-once consumption semantics for event-driven flows (ARC-05, FR-26 reuse).
- Inputs: PLATFORM-SPEC Section 6.
- Outputs: Inbox core types and `InboxBehavior`.
- Acceptance Criteria:
  - First-seen message proceeds to the handler; a duplicate short-circuits without invoking it
    (unit test with a fake store).
- Dependencies: 2.3.1
- Complexity: M

---

### 2.6 Build and Verification

#### 2.6.1 platform-core unit tests and reactor build
- ID: 2.6.1
- Title: Add focused unit tests and verify the platform-core reactor
- Description: Add unit tests for mediator ordering/short-circuit, outbox record building, inbox
  first-seen logic, and ApiResult/exception construction. Verify the core reactor builds.
- Business Purpose: Lock in the platform contracts with regression tests before starters depend on them.
- Inputs: PLATFORM-SPEC Section 11.
- Outputs: Unit tests across the five core modules.
- Acceptance Criteria:
  - `mvn -q install -pl platform-bom,platform-core,platform-core/common,platform-core/cqrs,platform-core/mediator,platform-core/outbox,platform-core/inbox` passes with tests green.
  - No `platform-core` module imports any Spring package (verified by a dependency/import check).
- Dependencies: 2.1.1, 2.1.2, 2.1.3, 2.2.1, 2.3.2, 2.3.3, 2.4.1, 2.5.1
- Complexity: M

---

## Sprint Deliverables

- `platform-common` (API contract, exceptions, context), `platform-cqrs`, `platform-mediator`
  (dispatcher + behaviors), `platform-outbox` core, `platform-inbox` core.
- Unit-tested, Spring-free, building as a reactor.

## Exit Criteria

- Platform-core reactor builds and tests pass.
- No Spring imports anywhere in `platform-core/*`.
- Mediator ordering/short-circuit, outbox record building, and inbox first-seen logic are covered by
  passing unit tests.
</content>
