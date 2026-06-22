# Sprint 05 - Security and Identity

## Objective

Build identity-service (9001): user/role/permission management, login issuing JWT access and refresh
tokens, refresh-token rotation with Redis blacklist and reuse detection, RBAC enforced on admin
endpoints, and an audit log. With the gateway (Sprint 04) already validating JWT and propagating
identity headers, this sprint completes authenticated, authorized access for every later service.

Covers FR-IAM-01, FR-IAM-04, FR-IAM-05 (FR-IAM-02/03 delivered at the gateway in Sprint 04).

## Included Epics

- Epic 5: Identity and Access Management (identity-service)

## Service setup note

identity-service is a CQRS + MEDIATOR service created from the service template (ADR-017), depending
only on starters. Its CLAUDE.md declares the mode; it owns its `identity` database; audit logging is
mandatory. Tasks below assume the template instance already builds and registers with discovery.

## Tasks

---

### 5.1 Service Scaffold and Schema

#### 5.1.1 Scaffold identity-service from template
- ID: 5.1.1
- Title: Create identity-service from the service template
- Description: Instantiate `microservices/identity-service` (port 9001, base package
  `com.telco.identity`) from the template; depend on starter-api, starter-security, starter-mediator,
  starter-observability, starter-outbox; point at the `identity` database; declare CQRS+Mediator mode
  in CLAUDE.md.
- Business Purpose: Standardized, platform-integrated identity service skeleton.
- Inputs: ADR-017, Sprint 03/04 outputs.
- Outputs: identity-service skeleton that builds and registers with discovery.
- Acceptance Criteria:
  - Service starts, registers in Eureka, exposes Swagger UI, and serves config from config-server.
- Dependencies: 3.4.1, 4.1.1, 4.2.1
- Complexity: S

#### 5.1.2 Identity schema migration
- ID: 5.1.2
- Title: Create Flyway migration for users, roles, permissions, audit
- Description: `V1__identity.sql` creating `users` (id, username, email, password_hash, status,
  created_at), `roles` (id, name), `permissions` (id, code), `user_roles`, `role_permissions`,
  `refresh_tokens` (id, user_id, token_hash, status, expires_at, created_at), and `audit_log`
  (id, actor_id, action, entity, entity_id, details jsonb, created_at).
- Business Purpose: Persistent identity, RBAC, token, and audit storage.
- Inputs: analysis Section 13, service-catalog identity-service.
- Outputs: Flyway migration.
- Acceptance Criteria:
  - Migration applies cleanly on Testcontainers Postgres; all tables and join tables exist with FKs.
- Dependencies: 5.1.1
- Complexity: M

---

### 5.2 Domain and Persistence

#### 5.2.1 User, Role, Permission domain and entities
- ID: 5.2.1
- Title: Implement User/Role/Permission domain model and JPA entities
- Description: Domain aggregate `User` (status PENDING/ACTIVE/LOCKED) with role assignment behavior;
  `Role`, `Permission` value/entity types; JPA entities mapping to the schema; password hashing via
  BCrypt. Keep domain logic framework-independent (ARC-02).
- Business Purpose: RBAC foundation (FR-IAM-04).
- Inputs: 5.1.2.
- Outputs: Domain types, JPA entities, password encoder.
- Acceptance Criteria:
  - A user can be assigned roles and resolve effective permissions; passwords are stored only as
    BCrypt hashes.
- Dependencies: 5.1.2
- Complexity: M

#### 5.2.2 Repositories
- ID: 5.2.2
- Title: Implement user/role/permission/refresh-token repositories
- Description: Spring Data repositories for users (by username/email), roles, permissions, and
  refresh tokens (by hash and by user).
- Business Purpose: Data access for authentication and RBAC.
- Inputs: 5.2.1.
- Outputs: Repository interfaces.
- Acceptance Criteria:
  - Lookups by username, email, and token hash return expected results in a slice/integration test.
- Dependencies: 5.2.1
- Complexity: S

---

### 5.3 Authentication

#### 5.3.1 Login command and JWT issuance
- ID: 5.3.1
- Title: Implement LoginCommand/handler issuing access + refresh tokens
- Description: `LoginCommand(username, password)` -> handler verifying the BCrypt hash and issuing a
  signed JWT access token (claims: sub=userId, roles, permissions, issuer, expiry) and a refresh
  token (persisted hashed). Use the shared `JwtService`/signing key consistent with the gateway.
- Business Purpose: Authenticate users and issue tokens (FR-IAM-01).
- Inputs: FR-IAM-01, analysis Section 13, starter-security JwtService.
- Outputs: Login command, handler, token-issuance service.
- Acceptance Criteria:
  - Valid credentials return an access + refresh token pair; invalid credentials return 401
    `ApiResult.failure`; the access token validates at the gateway.
- Dependencies: 5.2.2, 4.3.2
- Complexity: M

#### 5.3.2 Login endpoint
- ID: 5.3.2
- Title: Implement POST /api/v1/auth/login
- Description: Controller delegating to the mediator, returning `ApiResult<TokenResponse>`. Request
  `LoginRequest(username, password)`; response `TokenResponse(accessToken, refreshToken, expiresIn)`.
- Business Purpose: Public authentication entry point (gateway-allowlisted).
- Inputs: 5.3.1.
- Outputs: Auth controller, request/response DTOs.
- Acceptance Criteria:
  - `POST /api/v1/auth/login` returns 200 `ApiResult` with a token pair for valid credentials.
- Dependencies: 5.3.1
- Complexity: S

---

### 5.4 Refresh-Token Rotation and Reuse Detection

#### 5.4.1 Refresh command with rotation and blacklist
- ID: 5.4.1
- Title: Implement RefreshTokenCommand with rotation and Redis blacklist
- Description: `RefreshTokenCommand(refreshToken)` -> validate, issue a new access + refresh pair,
  mark the old refresh token used, and add it to a Redis blacklist (FR-IAM-05).
- Business Purpose: Secure session continuation with single-use refresh tokens (FR-IAM-05).
- Inputs: FR-IAM-05, analysis Section 13, Redis (1.3.4).
- Outputs: Refresh command + handler + Redis blacklist store.
- Acceptance Criteria:
  - A valid refresh returns a new pair and blacklists the prior token; a blacklisted token is rejected.
- Dependencies: 5.3.1
- Complexity: M

#### 5.4.2 Reuse detection and mass revocation
- ID: 5.4.2
- Title: Detect refresh-token reuse and revoke all user tokens
- Description: If a blacklisted/used refresh token is presented again, treat it as theft: revoke all
  active refresh tokens for that user and record an audit entry (FR-IAM-05).
- Business Purpose: Contain stolen-token replay (FR-IAM-05).
- Inputs: FR-IAM-05.
- Outputs: Reuse-detection logic + revocation.
- Acceptance Criteria:
  - Presenting an already-used refresh token revokes every active token for the user and writes an
    audit record; subsequent refresh attempts fail until re-login.
- Dependencies: 5.4.1, 5.6.1
- Complexity: M

#### 5.4.3 Refresh endpoint
- ID: 5.4.3
- Title: Implement POST /api/v1/auth/refresh
- Description: Controller delegating to the refresh command, returning `ApiResult<TokenResponse>`;
  gateway-allowlisted.
- Business Purpose: Token renewal entry point.
- Inputs: 5.4.1.
- Outputs: Refresh endpoint.
- Acceptance Criteria:
  - `POST /api/v1/auth/refresh` returns a rotated token pair for a valid token and 401 for an
    invalid/reused one.
- Dependencies: 5.4.1
- Complexity: S

---

### 5.5 User Management and RBAC

#### 5.5.1 User CRUD and role assignment (admin)
- ID: 5.5.1
- Title: Implement user management commands/queries with @PreAuthorize
- Description: Commands/queries for create user, assign roles, get user, list users; admin endpoints
  guarded by `@PreAuthorize`/mediator `AuthorizationRule` requiring an admin role. Publish
  `user.created.v1` via the outbox.
- Business Purpose: Administer accounts and authorization (FR-IAM-04).
- Inputs: FR-IAM-04, service-catalog identity-service.
- Outputs: User commands/queries, controller, authorization rules, `user.created.v1`.
- Acceptance Criteria:
  - `GET /api/v1/users/{id}` returns the user; admin-only endpoints return 403 for non-admin tokens;
    creating a user emits `user.created.v1` to the outbox.
- Dependencies: 5.2.2, 5.6.1
- Complexity: M

#### 5.5.2 Seed roles, permissions, and bootstrap admin
- ID: 5.5.2
- Title: Seed default roles/permissions and a bootstrap admin
- Description: Flyway/data seeding for roles (ADMIN, AGENT, DEALER, BILLING_OPERATOR, CUSTOMER) and
  their permissions, plus an initial admin account (credentials from encrypted config).
- Business Purpose: Make RBAC and admin operations usable from first boot; maps the analysis actors.
- Inputs: analysis Section 3 (actors), 4.1.2.
- Outputs: Seed migration/data.
- Acceptance Criteria:
  - On a fresh DB, default roles/permissions and one admin exist; the admin can authenticate.
- Dependencies: 5.1.2
- Complexity: S

---

### 5.6 Audit Logging

#### 5.6.1 Audit log writer
- ID: 5.6.1
- Title: Implement audit logging for identity changes
- Description: A mediator behavior or application service writing an `audit_log` row for every
  state-changing identity operation (login success/failure, token revocation, user/role changes),
  capturing actor, action, entity, and correlationId. Reusable pattern for customer/payment/
  subscription audit (NFR-12).
- Business Purpose: Mandatory regulatory audit trail (NFR-12, KVKK/GDPR).
- Inputs: analysis Section 13, NFR-12.
- Outputs: Audit writer + tests.
- Acceptance Criteria:
  - Each listed operation produces exactly one audit row with actor, action, entity, and correlationId.
- Dependencies: 5.1.2
- Complexity: M

---

### 5.7 Tests

#### 5.7.1 Identity integration tests
- ID: 5.7.1
- Title: Add identity-service integration tests (Testcontainers)
- Description: RestAssured + Testcontainers (Postgres, Redis) tests covering login, refresh rotation,
  reuse-detection revocation, RBAC 403, and audit-row creation.
- Business Purpose: Verify the authentication backbone end to end (NFR-17).
- Inputs: 5.3-5.6.
- Outputs: Integration test suite.
- Acceptance Criteria:
  - All listed flows pass; coverage includes a 401 path, a 403 path, and a reuse-revocation path.
- Dependencies: 5.3.2, 5.4.3, 5.5.1, 5.6.1
- Complexity: M

---

## Sprint Deliverables

- identity-service (9001) with user/role/permission management, login and refresh endpoints, JWT
  issuance, refresh rotation + Redis blacklist + reuse detection, RBAC on admin endpoints, audit log,
  and seeded roles/admin.
- Integration tests against Testcontainers Postgres + Redis.

## Exit Criteria

- A user can log in through the gateway, receive tokens, refresh them with rotation, and be revoked
  on reuse; admin endpoints reject non-admin tokens.
- FR-IAM-01, FR-IAM-04, FR-IAM-05 pass; combined with Sprint 04, all FR-IAM requirements are met.
- Audit rows are written for identity state changes; the reusable audit pattern is available to
  later services.
</content>
