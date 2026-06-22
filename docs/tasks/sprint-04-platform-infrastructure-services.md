# Sprint 04 - Platform Infrastructure Services

## Objective

Stand up the three edge/platform services from the analysis document: config-server (8888),
discovery-server (8761), and api-gateway (8080). These provide centralized configuration, service
registry, and the single secured entry point (routing, JWT validation hook, header propagation,
rate limiting, correlationId injection). They are configuration-only services with no domain logic.

## Included Epics

- Epic 4: Platform Infrastructure Services

## Tasks

---

### 4.1 Config Server

#### 4.1.1 Config-server application
- ID: 4.1.1
- Title: Implement Spring Cloud Config server (8888)
- Description: Create `microservices/config-server` running Spring Cloud Config in native/dev mode
  serving per-service configuration from a `config/` directory (datasource, Kafka, OTLP, security
  properties per service). In production this is replaced by ConfigMaps/Secrets (ADR-010); document
  the boundary.
- Business Purpose: Centralized, environment-aware configuration for all services (analysis Section 7).
- Inputs: ADR-010 (service discovery and configuration), analysis Section 7.
- Outputs: config-server app, `config/<service>.yml` files, Dockerfile.
- Acceptance Criteria:
  - `GET /<service>/default` returns that service's merged config.
  - A service started with `spring.config.import=configserver:` receives its properties.
- Dependencies: Sprint 03 complete
- Complexity: M

#### 4.1.2 Config encryption for secrets
- ID: 4.1.2
- Title: Enable encrypted config values
- Description: Configure the config-server symmetric/asymmetric key so secret properties (DB
  passwords, JWT secret) are stored encrypted (`{cipher}...`) and decrypted on serve.
- Business Purpose: Keep secrets out of plaintext config (NFR-06 adjacent, ADR-011).
- Inputs: ADR-010, ADR-011.
- Outputs: Encryption key config, encrypted sample secrets.
- Acceptance Criteria:
  - An encrypted property is served decrypted to an authorized client; the raw store shows only
    `{cipher}` text.
- Dependencies: 4.1.1
- Complexity: S

---

### 4.2 Discovery Server

#### 4.2.1 Discovery-server application
- ID: 4.2.1
- Title: Implement Eureka discovery server (8761) for dev
- Description: Create `microservices/discovery-server` running Eureka for local/dev service
  registration and lookup. Document that production uses Kubernetes-native discovery (ADR-010).
- Business Purpose: Dynamic service location for gateway routing and inter-service calls (dev).
- Inputs: ADR-010, analysis Section 7.
- Outputs: discovery-server app, Dockerfile.
- Acceptance Criteria:
  - The Eureka dashboard is reachable on 8761; a registered sample client appears in the registry.
- Dependencies: 4.1.1
- Complexity: S

#### 4.2.2 Service registration defaults in the template
- ID: 4.2.2
- Title: Add discovery-client registration to the service template
- Description: Update the service template and config-server defaults so every domain service
  registers with discovery in dev (Eureka client) and is discoverable by name.
- Business Purpose: New services auto-join the registry without per-service wiring.
- Inputs: 4.2.1, 3.4.1.
- Outputs: Template + config updates enabling discovery client.
- Acceptance Criteria:
  - reference-service (or template instance) registers and is listed in Eureka by its service id.
- Dependencies: 4.2.1
- Complexity: S

---

### 4.3 API Gateway

#### 4.3.1 Gateway application and routing
- ID: 4.3.1
- Title: Implement API gateway (8080) with discovery-based routing
- Description: Create `microservices/api-gateway` (Spring Cloud Gateway) routing `/api/v1/**` to
  domain services by discovery service id (identity, customer, product-catalog, order, subscription,
  usage, billing, payment, notification, ticket). Centralize per-route path config.
- Business Purpose: Single external entry point for all client traffic (analysis Section 13).
- Inputs: analysis Sections 7.2, 13; ADR-005.
- Outputs: api-gateway app with route definitions, Dockerfile.
- Acceptance Criteria:
  - A request to `/api/v1/customers/**` routes to customer-service via discovery (verified against a
    stub/registered service).
- Dependencies: 4.2.1
- Complexity: M

#### 4.3.2 JWT validation filter
- ID: 4.3.2
- Title: Implement gateway JWT validation global filter
- Description: Global filter validating the Bearer JWT on every request, rejecting missing/invalid/
  expired tokens with `ApiResult.failure` (401). Allowlist auth endpoints (`/api/v1/auth/login`,
  `/api/v1/auth/refresh`) and actuator health. Validation uses the same signing key/issuer as
  identity-service (FR-IAM-02).
- Business Purpose: Enforce authentication at the edge so services trust the gateway (NFR-05).
- Inputs: analysis Section 13, FR-IAM-02, ADR-011.
- Outputs: JWT validation filter + allowlist config.
- Acceptance Criteria:
  - A request without a token to a protected route returns 401 `ApiResult.failure`; a valid token
    passes; `/api/v1/auth/login` is reachable unauthenticated.
- Dependencies: 4.3.1
- Complexity: M

#### 4.3.3 Identity header propagation
- ID: 4.3.3
- Title: Propagate X-User-Id and X-User-Roles downstream
- Description: After JWT validation, extract userId and roles from claims and inject `X-User-Id` and
  `X-User-Roles` headers on the downstream request; strip any client-supplied values to prevent
  spoofing (FR-IAM-03).
- Business Purpose: Gateway-behind-trust identity propagation consumed by starter-security (NFR-05).
- Inputs: FR-IAM-03, PLATFORM-SPEC Section 9.3.
- Outputs: Header-propagation filter.
- Acceptance Criteria:
  - Downstream receives `X-User-Id`/`X-User-Roles` derived from the token; client-supplied identity
    headers are overwritten, not trusted.
- Dependencies: 4.3.2
- Complexity: M

#### 4.3.4 CorrelationId injection
- ID: 4.3.4
- Title: Inject X-Correlation-Id at the gateway
- Description: Generate `X-Correlation-Id` when absent and propagate it downstream and back to the
  client; align with starter-observability's `CorrelationFilter` key (NFR-13).
- Business Purpose: End-to-end request correlation across all services (NFR-13).
- Inputs: analysis Section 12, PLATFORM-SPEC Section 9.6.
- Outputs: Correlation-injection filter.
- Acceptance Criteria:
  - Every routed request carries an `X-Correlation-Id`; an inbound value is preserved, an absent one
    is generated and echoed on the response.
- Dependencies: 4.3.1
- Complexity: S

#### 4.3.5 Redis-backed rate limiting
- ID: 4.3.5
- Title: Implement per-user rate limiting (100 req/min)
- Description: Configure the gateway Redis rate limiter keyed by `X-User-Id` (fallback to client IP
  for unauthenticated routes), default 100 req/min, returning 429 with `ApiResult.failure` when
  exceeded (NFR-18).
- Business Purpose: Protect downstream services from abuse and overload (NFR-18).
- Inputs: analysis Section 13, NFR-18.
- Outputs: Rate-limiter filter + config.
- Acceptance Criteria:
  - Exceeding 100 req/min for a user yields 429 `ApiResult.failure`; the limit resets per window;
    counters are stored in Redis.
- Dependencies: 4.3.3, Sprint 01 Redis (1.3.4)
- Complexity: M

#### 4.3.6 Gateway OpenAPI aggregation
- ID: 4.3.6
- Title: Aggregate per-service Swagger UIs at the gateway
- Description: Configure Springdoc aggregation so each service's OpenAPI is reachable through the
  gateway under a documented path (ARC-08).
- Business Purpose: Single discovery point for all API documentation.
- Inputs: ADR-015, ARC-08.
- Outputs: Gateway OpenAPI aggregation config.
- Acceptance Criteria:
  - The gateway Swagger UI lists and loads each registered service's API definition.
- Dependencies: 4.3.1
- Complexity: S

---

## Sprint Deliverables

- config-server (8888) with encrypted secrets, discovery-server (8761), and api-gateway (8080) with
  routing, JWT validation, identity header propagation, correlationId injection, Redis rate limiting,
  and OpenAPI aggregation.
- Service template registers with discovery and pulls config from config-server.

## Exit Criteria

- All three infrastructure services start via `make infra-up` plus their own run, and a routed
  request flows: gateway -> JWT check -> header propagation -> discovery-resolved service.
- Unauthenticated access to protected routes returns 401; rate limit returns 429 past 100 req/min;
  every request carries a correlationId.
- FR-IAM-02 and FR-IAM-03 are satisfied at the gateway (identity issuance itself lands in Sprint 05).
</content>
