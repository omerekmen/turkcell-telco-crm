# Sprint 07 - Product Catalog Domain

## Objective

Build product-catalog-service (9003): hierarchical management of tariffs, addons, and VAS products
with validity dates and target segments, postpaid/prepaid/hybrid classification, versioned tariff
changes that preserve existing subscribers' tariff, and a Redis cache-aside read path (this is a
read-heavy service). Provides the priced products that order-service (Sprint 08) snapshots.

Covers FR-05, FR-06, FR-07, FR-08.

## Included Epics

- Epic 7: Product and Tariff Catalog (product-catalog-service)

## Tasks

---

### 7.1 Scaffold and Schema

#### 7.1.1 Scaffold product-catalog-service from template
- ID: 7.1.1
- Title: Create product-catalog-service from the service template
- Description: Instantiate `microservices/product-catalog-service` (port 9003, base package
  `com.telco.catalog`) from the template; depend on starter-api, starter-security, starter-mediator,
  starter-observability, starter-outbox; own the `product` database; declare CQRS+Mediator mode;
  enable Redis cache support.
- Business Purpose: Standardized catalog-domain service skeleton.
- Inputs: ADR-017.
- Outputs: product-catalog-service skeleton building and registering.
- Acceptance Criteria:
  - Service starts, registers, exposes Swagger UI, connects to Redis.
- Dependencies: 3.4.1, Sprint 04, Redis (1.3.4)
- Complexity: S

#### 7.1.2 Catalog schema migration
- ID: 7.1.2
- Title: Create Flyway migration for tariffs, addons, versioning
- Description: `V1__catalog.sql` creating `tariffs` (id, code, name, type, monthly_fee,
  minutes_included, sms_included, data_mb_included, status, effective_from, effective_to, version),
  `addons` (id, code, name, price, type, validity_days), `tariff_addons` (tariff_id, addon_id), and
  `tariff_versions` capturing historical tariff snapshots by `(code, version)` (FR-08).
- Business Purpose: Versioned, time-bounded catalog storage (FR-06, FR-08).
- Inputs: analysis Section 10.2, FR-06, FR-08.
- Outputs: Flyway migration.
- Acceptance Criteria:
  - Migration applies on Testcontainers Postgres; tariff/addon many-to-many and version history
    tables exist; money columns are numeric with a currency column.
- Dependencies: 7.1.1
- Complexity: M

---

### 7.2 Domain and Persistence

#### 7.2.1 Tariff and Addon domain model
- ID: 7.2.1
- Title: Implement Tariff/Addon aggregates with classification and validity
- Description: `Tariff` aggregate (type POSTPAID/PREPAID/HYBRID, status DRAFT/ACTIVE/RETIRED,
  effectiveFrom/effectiveTo, targetSegment) and `Addon` (type DATA/SMS/MINUTES/VAS, validityDays).
  Enforce that `effectiveTo` is after `effectiveFrom` and validity windows are coherent (FR-06, FR-07).
- Business Purpose: Model the product catalog with telecom classification (FR-06, FR-07).
- Inputs: FR-06, FR-07, analysis Section 10.2.
- Outputs: Domain aggregates + entities.
- Acceptance Criteria:
  - Creating a tariff with `effectiveTo <= effectiveFrom` throws `BusinessRuleException`; type and
    segment are persisted.
- Dependencies: 7.1.2
- Complexity: M

#### 7.2.2 Tariff versioning logic
- ID: 7.2.2
- Title: Implement tariff version-on-change preserving existing subscribers
- Description: On a price/attribute change, create a new tariff version (incremented `version`) and
  retain prior versions immutably so existing subscribers keep their assigned version (FR-08). Provide
  resolution of "current active version" by `effectiveFrom`/`effectiveTo`.
- Business Purpose: Tariff changes must not alter existing subscribers' terms (FR-08).
- Inputs: FR-08.
- Outputs: Versioning service + tests.
- Acceptance Criteria:
  - Changing a tariff produces a new version; the prior version remains queryable; resolving by an
    earlier date returns the earlier version.
- Dependencies: 7.2.1
- Complexity: M

#### 7.2.3 Repositories
- ID: 7.2.3
- Title: Implement tariff/addon/version repositories
- Description: Spring Data repositories for tariffs (by code, by active window), addons (by code and
  by tariff), and tariff versions.
- Business Purpose: Catalog data access.
- Inputs: 7.2.1, 7.2.2.
- Outputs: Repositories.
- Acceptance Criteria:
  - Lookups by code and active-window queries return expected rows in a slice/integration test.
- Dependencies: 7.2.2
- Complexity: S

---

### 7.3 Caching

#### 7.3.1 Redis cache-aside for reads
- ID: 7.3.1
- Title: Implement cache-aside for tariff/addon reads with invalidation
- Description: Cache `GET /tariffs/{code}` and `GET /addons` results in Redis with a TTL; invalidate
  on tariff/addon create/update/version change. Read-heavy path per the analysis (Section 8.2).
- Business Purpose: Low-latency catalog reads supporting NFR-01 (p95 < 300ms).
- Inputs: analysis Section 8.2, NFR-01.
- Outputs: Cache config + invalidation hooks + tests.
- Acceptance Criteria:
  - A repeated read is served from Redis (cache hit observable); a tariff update invalidates the
    cached entry so the next read reflects the change.
- Dependencies: 7.2.3, 7.4.3
- Complexity: M

---

### 7.4 Application (Commands, Queries, Endpoints)

#### 7.4.1 Create tariff (admin)
- ID: 7.4.1
- Title: Implement POST /api/v1/tariffs (admin)
- Description: Admin-guarded `CreateTariffCommand` creating an ACTIVE tariff and publishing
  `tariff.created.v1` via the outbox. `CreateTariffRequest`/`TariffResponse` DTOs.
- Business Purpose: Admin catalog authoring (FR-05).
- Inputs: FR-05, event-catalog `tariff.created.v1`.
- Outputs: Command, handler, DTOs, endpoint, event.
- Acceptance Criteria:
  - Admin creates a tariff (201) and `tariff.created.v1` is written; non-admin gets 403.
- Dependencies: 7.2.3, 5.5.1
- Complexity: M

#### 7.4.2 Change tariff price (admin, versioned)
- ID: 7.4.2
- Title: Implement tariff price change with versioning and event
- Description: Admin-guarded command applying a price change via versioning (7.2.2), publishing
  `tariff.price-changed.v1` (consumed by billing, notification).
- Business Purpose: Controlled, audited price evolution (FR-08).
- Inputs: FR-08, event-catalog `tariff.price-changed.v1`.
- Outputs: Price-change command + endpoint + event.
- Acceptance Criteria:
  - A price change creates a new version and emits `tariff.price-changed.v1`; existing version pricing
    is unchanged.
- Dependencies: 7.2.2, 7.4.1
- Complexity: M

#### 7.4.3 Catalog read endpoints
- ID: 7.4.3
- Title: Implement tariff/addon read queries and endpoints
- Description: `GET /api/v1/tariffs`, `GET /api/v1/tariffs/{code}`, `GET /api/v1/addons?tariffCode=...`,
  returning `ApiResult` with pagination; only active-window products by default.
- Business Purpose: Catalog browsing for customers and order-service (FR-05, FR-06).
- Inputs: FR-05, FR-06, analysis Section 8.2.
- Outputs: Queries + endpoints + DTOs.
- Acceptance Criteria:
  - Listing returns paginated active tariffs; `GET /tariffs/{code}` returns the current active version;
    addons can be filtered by tariff code.
- Dependencies: 7.2.3
- Complexity: M

#### 7.4.4 Internal price-snapshot endpoint
- ID: 7.4.4
- Title: Implement internal price-quote endpoint for order-service
- Description: An endpoint returning a priced snapshot for a product code at a point in time (current
  active version) that order-service calls synchronously and stores as a snapshot (analysis Section 9.1).
- Business Purpose: Orders must capture an immutable price snapshot at order time (FR-09 dependency).
- Inputs: analysis Section 9.1 ("Snapshot alınmalı").
- Outputs: Price-quote query + endpoint.
- Acceptance Criteria:
  - Given a product code, the endpoint returns code, price, currency, and version usable as an order
    snapshot; an unknown/inactive code returns 404.
- Dependencies: 7.4.3
- Complexity: S

---

### 7.5 Tests

#### 7.5.1 Catalog integration tests
- ID: 7.5.1
- Title: Add product-catalog-service integration tests (Testcontainers)
- Description: RestAssured + Testcontainers (Postgres, Redis, Kafka) covering tariff create/list/get,
  versioned price change with event, addon filtering, cache hit/invalidation, and admin authorization.
- Business Purpose: Verify the catalog domain end to end (NFR-17).
- Inputs: 7.4.x.
- Outputs: Integration test suite.
- Acceptance Criteria:
  - All FR-05..08 flows pass; tests assert version preservation and cache invalidation; admin
    authorization enforced.
- Dependencies: 7.4.2, 7.4.3, 7.3.1
- Complexity: M

---

## Sprint Deliverables

- product-catalog-service (9003): tariff/addon CRUD, classification, validity windows, versioned
  price changes preserving existing subscribers, Redis cache-aside reads, price-snapshot endpoint,
  catalog events, and integration tests.

## Exit Criteria

- Admins can author tariffs/addons; customers and order-service can browse and snapshot prices.
- A tariff price change creates a new version while preserving prior versions; reads are cache-served
  and invalidated correctly.
- FR-05, FR-06, FR-07, FR-08 pass; `tariff.created.v1` and `tariff.price-changed.v1` are published.
</content>
