# Sprint 06 - Customer Domain

## Objective

Build customer-service (9002): individual customer registration with TCKN validation, KYC workflow
(PENDING -> ACTIVE/REJECTED), address and document management, soft-delete (KVKK/GDPR), PII
encryption at rest, and domain events. This is the first business domain and a prerequisite for
ordering (Sprint 08) and onboarding (Sprint 09).

Covers FR-01, FR-02, FR-03, FR-04.

## Included Epics

- Epic 6: Customer Management (customer-service)

## Tasks

---

### 6.1 Scaffold and Schema

#### 6.1.1 Scaffold customer-service from template
- ID: 6.1.1
- Title: Create customer-service from the service template
- Description: Instantiate `microservices/customer-service` (port 9002, base package
  `com.telco.customer`) from the template; depend on starter-api, starter-security, starter-mediator,
  starter-observability, starter-outbox; own the `customer` database; declare CQRS+Mediator mode.
- Business Purpose: Standardized customer-domain service skeleton.
- Inputs: ADR-017.
- Outputs: customer-service skeleton building and registering with discovery.
- Acceptance Criteria:
  - Service starts, registers, exposes Swagger UI, pulls config from config-server.
- Dependencies: 3.4.1, Sprint 04
- Complexity: S

#### 6.1.2 Customer schema migration
- ID: 6.1.2
- Title: Create Flyway migration for customer, address, document, audit
- Description: `V1__customer.sql` creating `customers` (id, type, first_name, last_name,
  identity_number_enc, date_of_birth, status, created_at, deleted_at), `addresses` (id, customer_id,
  line1, city, district, postal_code, is_default), `documents` (id, customer_id, type, file_ref,
  verified_at), and `audit_log`. `identity_number_enc` stores the AES-GCM ciphertext (NFR-06).
- Business Purpose: Persistent customer master data with PII encrypted and soft-delete support.
- Inputs: analysis Section 10.1, FR-04, NFR-06.
- Outputs: Flyway migration.
- Acceptance Criteria:
  - Migration applies on Testcontainers Postgres; `deleted_at` enables soft-delete; identity number
    column stores ciphertext, not plaintext.
- Dependencies: 6.1.1
- Complexity: M

---

### 6.2 Domain and Persistence

#### 6.2.1 Customer domain model and state machine
- ID: 6.2.1
- Title: Implement Customer aggregate with KYC state transitions
- Description: `Customer` aggregate (type INDIVIDUAL/CORPORATE) enforcing status transitions
  PENDING -> ACTIVE / REJECTED (FR-02), with `Address` and `Document` entities. Reject illegal
  transitions with `BusinessRuleException`. Framework-independent domain (ARC-02).
- Business Purpose: Enforce the KYC lifecycle as domain invariants (FR-02).
- Inputs: FR-02, analysis Section 10.1.
- Outputs: Domain aggregate + child entities.
- Acceptance Criteria:
  - PENDING->ACTIVE and PENDING->REJECTED succeed; ACTIVE->PENDING throws `BusinessRuleException`.
- Dependencies: 6.1.2
- Complexity: M

#### 6.2.2 TCKN validation
- ID: 6.2.2
- Title: Implement TCKN (and VKN) validation
- Description: A reusable validator implementing the TCKN checksum algorithm (and VKN for corporate),
  surfaced as a Jakarta Bean Validation constraint used on registration input (FR-01).
- Business Purpose: Reject invalid national identity numbers at the boundary (FR-01).
- Inputs: FR-01.
- Outputs: `@ValidTckn` constraint + validator + unit tests.
- Acceptance Criteria:
  - Known-valid TCKNs pass; checksum-invalid and wrong-length values fail with a 400 validation error.
- Dependencies: 6.1.1
- Complexity: M

#### 6.2.3 PII encryption converter
- ID: 6.2.3
- Title: Implement AES-GCM JPA attribute converter for identity number
- Description: A JPA `AttributeConverter` encrypting the identity number with AES-GCM on write and
  decrypting on read, key sourced from config/secret (NFR-06). Ensure the value is masked in logs
  (ADR-021) and never returned in full by APIs.
- Business Purpose: PII-at-rest protection mandated by NFR-06 and KVKK/GDPR.
- Inputs: NFR-06, ADR-021, analysis Section 13.
- Outputs: AES-GCM converter + key wiring + tests.
- Acceptance Criteria:
  - Persisted identity number is ciphertext in the DB and plaintext in the domain; API responses
    return only a masked form; logs never contain the raw value.
- Dependencies: 6.1.2, 3.2.7
- Complexity: M

#### 6.2.4 Repositories with soft-delete
- ID: 6.2.4
- Title: Implement customer/address/document repositories honoring soft-delete
- Description: Spring Data repositories; default queries exclude soft-deleted customers
  (`deleted_at is null`); a delete operation sets `deleted_at` rather than removing the row (FR-04).
- Business Purpose: KVKK/GDPR-compliant soft-delete (FR-04).
- Inputs: FR-04.
- Outputs: Repositories + soft-delete filter.
- Acceptance Criteria:
  - A deleted customer is excluded from default reads but the row persists with `deleted_at` set.
- Dependencies: 6.2.1
- Complexity: S

---

### 6.3 Application (Commands, Queries, Endpoints)

#### 6.3.1 Register customer command and endpoint
- ID: 6.3.1
- Title: Implement RegisterCustomerCommand and POST /api/v1/customers
- Description: `RegisterCustomerCommand` (validated, TCKN-checked) creating a PENDING customer,
  publishing `customer.registered.v1` via the outbox. `RegisterCustomerRequest` /
  `CustomerResponse` DTOs; controller returns `ApiResult<CustomerResponse>` (201).
- Business Purpose: Customer onboarding entry point (FR-01).
- Inputs: FR-01, event-catalog `customer.registered.v1`.
- Outputs: Command, handler, DTOs, endpoint, outbox publish.
- Acceptance Criteria:
  - Valid registration returns 201 with a PENDING customer and writes `customer.registered.v1`;
    invalid TCKN returns 400.
- Dependencies: 6.2.2, 6.2.3, 6.2.4
- Complexity: M

#### 6.3.2 Get and update customer
- ID: 6.3.2
- Title: Implement GET and PUT /api/v1/customers/{id}
- Description: `GetCustomerQuery` and `UpdateCustomerCommand` (contact/profile fields). Update
  publishes `customer.updated.v1`. Responses mask PII.
- Business Purpose: View and maintain customer profile (FR-03).
- Inputs: FR-03, event-catalog `customer.updated.v1`.
- Outputs: Query/command, DTOs, endpoints.
- Acceptance Criteria:
  - GET returns the (PII-masked) customer; PUT updates allowed fields and emits `customer.updated.v1`;
    unknown id returns 404.
- Dependencies: 6.3.1
- Complexity: M

#### 6.3.3 Address management
- ID: 6.3.3
- Title: Implement address add/update/list with default handling
- Description: Commands/queries and endpoints to add, update, list, and set-default addresses under
  `/api/v1/customers/{id}/addresses`; exactly one default enforced (FR-03).
- Business Purpose: Customer address management (FR-03).
- Inputs: FR-03, analysis Section 8.1.
- Outputs: Address commands/queries + endpoints.
- Acceptance Criteria:
  - Adding a second default unsets the previous; listing returns all addresses with one default.
- Dependencies: 6.2.4
- Complexity: M

#### 6.3.4 Document upload (KYC)
- ID: 6.3.4
- Title: Implement POST /api/v1/customers/{id}/documents
- Description: Accept a KYC document (ID_CARD/PASSPORT), store the binary in MinIO/local FS and the
  reference in `documents`, returning the document metadata. Validate content type/size (FR-03).
- Business Purpose: Capture KYC evidence (FR-03, AC-01 step 2).
- Inputs: FR-03, analysis Section 7.1 (MinIO), AC-01.
- Outputs: Upload command, storage adapter, endpoint.
- Acceptance Criteria:
  - Uploading a document stores the file, records a `documents` row with `file_ref`, and returns
    metadata; oversized/invalid types are rejected with 400.
- Dependencies: 6.2.4, Sprint 01 (object storage)
- Complexity: M

#### 6.3.5 KYC approval/rejection
- ID: 6.3.5
- Title: Implement POST /api/v1/customers/{id}/kyc/approve and reject
- Description: Admin-guarded commands transitioning the customer to ACTIVE (approve) or REJECTED
  (reject), verifying documents, and publishing `customer.kyc-approved.v1` / `customer.kyc-rejected.v1`
  via the outbox; writes an audit row (FR-02, NFR-12).
- Business Purpose: Complete the KYC decision step (FR-02, AC-01 step 3).
- Inputs: FR-02, event-catalog kyc events, AC-01.
- Outputs: Approve/reject commands, endpoints, events, audit.
- Acceptance Criteria:
  - Approve transitions PENDING->ACTIVE and emits `customer.kyc-approved.v1`; reject transitions to
    REJECTED and emits `customer.kyc-rejected.v1`; both require an admin role and write audit rows.
- Dependencies: 6.3.4, 5.5.1, 5.6.1
- Complexity: M

#### 6.3.6 Soft-delete endpoint
- ID: 6.3.6
- Title: Implement DELETE /api/v1/customers/{id} (soft-delete)
- Description: `DeleteCustomerCommand` setting `deleted_at`; subsequent reads return 404; writes an
  audit row (FR-04).
- Business Purpose: KVKK/GDPR right-to-erasure via soft-delete (FR-04).
- Inputs: FR-04.
- Outputs: Delete command + endpoint.
- Acceptance Criteria:
  - DELETE soft-deletes the customer (row retained, `deleted_at` set); a later GET returns 404; an
    audit row is written.
- Dependencies: 6.2.4, 5.6.1
- Complexity: S

---

### 6.4 Tests

#### 6.4.1 Customer integration tests
- ID: 6.4.1
- Title: Add customer-service integration tests (Testcontainers)
- Description: RestAssured + Testcontainers (Postgres, Kafka) covering registration, KYC
  approve/reject with event emission, address default handling, document upload, soft-delete, PII
  encryption (DB ciphertext), and TCKN validation failure.
- Business Purpose: Verify the customer domain end to end (NFR-17).
- Inputs: 6.3.x.
- Outputs: Integration test suite.
- Acceptance Criteria:
  - All FR-01..04 flows pass; a test asserts the identity number is ciphertext in the DB and masked
    in API/log output; KYC approval emits the outbox event.
- Dependencies: 6.3.1, 6.3.5, 6.3.6
- Complexity: M

---

## Sprint Deliverables

- customer-service (9002): registration with TCKN validation, KYC workflow with events, address and
  document management, PII AES-GCM encryption, soft-delete, audit logging, and integration tests.

## Exit Criteria

- A customer can register (PENDING), upload a KYC document, and be approved (ACTIVE) or rejected,
  with `customer.registered.v1` and `customer.kyc-approved/rejected.v1` published via the outbox.
- Identity numbers are encrypted at rest and masked in responses/logs; soft-delete preserves the row.
- FR-01, FR-02, FR-03, FR-04 pass; AC-01 steps 1-3 are satisfied (full AC-01 validated in Sprint 09).
</content>
