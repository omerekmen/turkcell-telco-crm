# Sprint 01 - Foundation

## Objective

Establish the empty-repository foundation: monorepo structure, Maven multi-module build with a
shared BOM, the local infrastructure stack (PostgreSQL, Kafka/KRaft, Schema Registry, Debezium,
Redis, observability), and a CI skeleton. Nothing business-facing ships here; everything downstream
depends on it.

## Included Epics

- Epic 1: Foundation and Build Infrastructure

## Tasks

---

### 1.1 Repository and Monorepo Structure

#### 1.1.1 Initialize repository and top-level layout
- ID: 1.1.1
- Title: Initialize Git repository and monorepo directory structure
- Description: Create the Git repo and the top-level directories: `platform/`, `microservices/`,
  `infra/`, `architecture/adr/`, `docs/`, `.github/`. Add `.gitignore` (Java/Maven/IDE/target),
  `.editorconfig`, and a root `README.md` describing the monorepo (ADR-002).
- Business Purpose: A single versioned source of truth for platform and services (ADR-001/002).
- Inputs: ADR-001 (repository strategy), ADR-002 (monorepo strategy).
- Outputs: Git repo with directory skeleton, `.gitignore`, `.editorconfig`, root `README.md`.
- Acceptance Criteria:
  - `git status` is clean after initial commit; `target/` and IDE files are ignored.
  - All listed top-level directories exist and are tracked (with `.gitkeep` where empty).
- Dependencies: none
- Complexity: S

#### 1.1.2 Define code-ownership and contribution rules
- ID: 1.1.2
- Title: Add CODEOWNERS, PR template, and no-emoji contribution rule
- Description: Add `.github/CODEOWNERS`, a PR template enforcing ADR references, and a
  `CONTRIBUTING.md` codifying ARC-09 (no emojis in code, comments, commits, docs).
- Business Purpose: Enforce governance and review discipline from commit one.
- Inputs: CLAUDE.md governance rules.
- Outputs: `CODEOWNERS`, PR template, `CONTRIBUTING.md`.
- Acceptance Criteria:
  - Opening a PR auto-populates the template.
  - CONTRIBUTING explicitly forbids emojis and references the ADR index.
- Dependencies: 1.1.1
- Complexity: S

---

### 1.2 Maven Multi-Module Build

#### 1.2.1 Create the platform BOM
- ID: 1.2.1
- Title: Create platform-bom with pinned dependency versions
- Description: Create `platform/platform-bom` (packaging `pom`) managing versions for Spring Boot,
  Spring Cloud, PostgreSQL driver, Kafka/Avro, Redis, MapStruct, jjwt, Resilience4j, Springdoc,
  Micrometer/OpenTelemetry, Testcontainers, RestAssured. All services and platform modules import
  this BOM; no module hardcodes a version (ADR-003, ADR-020).
- Business Purpose: One place controls the dependency matrix; eliminates version drift across
  13 services.
- Inputs: ADR-003 (technology stack), ADR-020 (platform Maven architecture), analysis Section 11.
- Outputs: `platform/platform-bom/pom.xml` with `<dependencyManagement>`.
- Acceptance Criteria:
  - `mvn -q -pl platform/platform-bom validate` succeeds.
  - Every dependency version referenced elsewhere resolves from the BOM, not inline.
- Dependencies: 1.1.1
- Complexity: M

#### 1.2.2 Create the root reactor POM
- ID: 1.2.2
- Title: Create root aggregator POM with build plugins
- Description: Root `pom.xml` (packaging `pom`) listing `platform` and `microservices` reactors as
  modules. Configure `maven-compiler-plugin` (release 21), `maven-surefire`/`failsafe`,
  `flatten-maven-plugin` for SNAPSHOT resolution if used, and a properties block referencing the BOM.
- Business Purpose: Single-command build of the whole platform.
- Inputs: ADR-020.
- Outputs: Root `pom.xml`, `platform/pom.xml`, `microservices/pom.xml` aggregators.
- Acceptance Criteria:
  - `mvn -q -DskipTests validate` from repo root resolves all declared modules.
  - Java release level is 21.
- Dependencies: 1.2.1
- Complexity: M

#### 1.2.3 Wire static analysis into the build
- ID: 1.2.3
- Title: Configure Checkstyle and SpotBugs gates bound to verify
- Description: Add Checkstyle (ruleset enforcing no-wildcard-imports, Javadoc on public types) and
  SpotBugs to the platform reactor POMs, bound to the `verify` phase. Provide a shared config under
  `build/` consumed by all modules (ADR-014).
- Business Purpose: Automated enforcement of code quality before merge (NFR-17).
- Inputs: ADR-014 (CI/CD strategy).
- Outputs: Checkstyle/SpotBugs config files; plugin bindings in reactor POMs.
- Acceptance Criteria:
  - `mvn -q verify` fails on a deliberate Checkstyle violation and passes when clean.
- Dependencies: 1.2.2
- Complexity: M

---

### 1.3 Local Infrastructure Stack (Docker Compose)

#### 1.3.1 PostgreSQL service and per-service databases
- ID: 1.3.1
- Title: Add PostgreSQL to Docker Compose with per-service databases
- Description: Add a PostgreSQL 16 service to `infra/docker/compose.yml` with an init script
  creating one database/schema per domain service (identity, customer, product, order, subscription,
  usage, billing, payment, notification, ticket) and per-service credentials (ADR-006).
- Business Purpose: Database-per-service isolation locally (NFR-15).
- Inputs: ADR-006 (database strategy), analysis Section 7.1.
- Outputs: Postgres service in compose, `infra/docker/postgres/init/*.sql`.
- Acceptance Criteria:
  - `docker compose up postgres` starts; each per-service database is reachable with its own user.
- Dependencies: 1.1.1
- Complexity: M

#### 1.3.2 Kafka (KRaft) and Schema Registry
- ID: 1.3.2
- Title: Add Kafka (KRaft mode) and Schema Registry to Docker Compose
- Description: Add Kafka 3.7+ in KRaft mode (no ZooKeeper) and Confluent/Apicurio Schema Registry.
  Pre-create MVP topics matching the event catalog domains via an init container or documented
  auto-create policy (ADR-009, ADR-019).
- Business Purpose: Event backbone for asynchronous domain integration (NFR-11, NFR-16).
- Inputs: ADR-009, ADR-019, `docs/architecture/event-catalog.md`.
- Outputs: Kafka + Schema Registry services in compose; topic bootstrap script.
- Acceptance Criteria:
  - `docker compose up kafka schema-registry` starts; a test produce/consume round-trips.
  - Schema Registry REST endpoint responds on its configured port.
- Dependencies: 1.1.1
- Complexity: M

#### 1.3.3 Debezium Kafka Connect for outbox CDC
- ID: 1.3.3
- Title: Add Debezium Connect and outbox connector template
- Description: Add Kafka Connect with the Debezium PostgreSQL connector and an example outbox
  connector config that routes outbox-table rows to `domain.event` topics (ADR-005, ADR-009).
- Business Purpose: Atomic DB-write-plus-publish delivery for the transactional outbox (ARC-05).
- Inputs: ADR-005, ADR-009, PLATFORM-SPEC outbox section.
- Outputs: Connect service in compose, `infra/docker/kafka-connect/connectors/outbox-connector.example.json`,
  registration script.
- Acceptance Criteria:
  - Connect starts; the example connector registers against a service outbox table without error
    (validated later when a producing service exists).
- Dependencies: 1.3.1, 1.3.2
- Complexity: M

#### 1.3.4 Redis
- ID: 1.3.4
- Title: Add Redis to Docker Compose
- Description: Add Redis 7 used for cache-aside, gateway rate limiting, idempotency keys, and refresh-
  token blacklist (analysis Section 7.1, NFR-18).
- Business Purpose: Caching, rate limiting, idempotency, token revocation backbone.
- Inputs: analysis Section 7.1, ADR-011.
- Outputs: Redis service in compose.
- Acceptance Criteria:
  - `docker compose up redis` starts; `redis-cli ping` returns `PONG`.
- Dependencies: 1.1.1
- Complexity: S

#### 1.3.5 Observability stack
- ID: 1.3.5
- Title: Add OpenTelemetry Collector, Tempo, Loki, Prometheus, Grafana
- Description: Add the observability stack with an OTel Collector receiving OTLP and exporting traces
  to Tempo, logs to Loki, metrics to Prometheus; Grafana with provisioned datasources and a starter
  dashboard (ADR-012, NFR-07/08/09).
- Business Purpose: Distributed tracing, centralized structured logging, and metrics from day one.
- Inputs: ADR-012.
- Outputs: Collector/Tempo/Loki/Prometheus/Grafana services in compose with provisioning files.
- Acceptance Criteria:
  - `docker compose up` brings the stack healthy; Grafana lists Tempo, Loki, and Prometheus
    datasources as reachable.
- Dependencies: 1.1.1
- Complexity: M

#### 1.3.6 Compose orchestration, healthchecks, and Makefile
- ID: 1.3.6
- Title: Unify infra stack with healthchecks and Make targets
- Description: Add healthchecks and start-order dependencies to every compose service and provide
  `infra/Makefile` / root `Makefile` targets (`infra-up`, `infra-down`, `infra-logs`, `infra-reset`).
- Business Purpose: One-command, reproducible local environment for all later sprints.
- Inputs: 1.3.1-1.3.5.
- Outputs: Finalized `infra/docker/compose.yml`, Makefile targets, `infra/README.md`.
- Acceptance Criteria:
  - `make infra-up` brings the full stack to healthy; `make infra-down` removes it cleanly.
- Dependencies: 1.3.1, 1.3.2, 1.3.3, 1.3.4, 1.3.5
- Complexity: M

---

### 1.4 CI Skeleton

#### 1.4.1 Build-and-test CI pipeline
- ID: 1.4.1
- Title: Create GitHub Actions build/test workflow
- Description: Workflow on push/PR running `mvn -q verify` on JDK 21 with Maven caching and
  Testcontainers-compatible runner. Upload surefire/failsafe reports as artifacts (ADR-014).
- Business Purpose: Every change is built, tested, and statically analyzed (NFR-17).
- Inputs: ADR-014, 1.2.3.
- Outputs: `.github/workflows/build.yml`.
- Acceptance Criteria:
  - Workflow runs on PR and fails on a failing test or Checkstyle violation.
- Dependencies: 1.2.3
- Complexity: M

#### 1.4.2 Dependency and security scanning
- ID: 1.4.2
- Title: Add dependency update and vulnerability scanning to CI
- Description: Configure Dependabot for Maven and GitHub Actions, and an OWASP/Trivy dependency scan
  job that fails on high-severity findings.
- Business Purpose: Keep the dependency matrix current and free of known vulnerabilities.
- Inputs: ADR-014.
- Outputs: `.github/dependabot.yml`, scan job in CI.
- Acceptance Criteria:
  - Dependabot opens update PRs; the scan job runs and reports findings.
- Dependencies: 1.4.1
- Complexity: S

---

## Sprint Deliverables

- Initialized monorepo with `.gitignore`, governance files, and ADR-aligned structure.
- `platform-bom` and root reactor build compiling on JDK 21 with Checkstyle/SpotBugs gates.
- Full local infrastructure stack via `make infra-up` (Postgres per-service DBs, Kafka/KRaft, Schema
  Registry, Debezium Connect, Redis, OTel/Tempo/Loki/Prometheus/Grafana).
- CI skeleton running build, test, static analysis, and dependency scanning.

## Exit Criteria

- `mvn -q -DskipTests validate` passes from repo root.
- `make infra-up` brings every infrastructure service to a healthy state and `make infra-down`
  tears it down cleanly.
- CI runs green on an empty-but-structured commit and fails on a seeded Checkstyle violation.
- No business or platform Java code exists yet; this sprint is foundation only.
</content>
