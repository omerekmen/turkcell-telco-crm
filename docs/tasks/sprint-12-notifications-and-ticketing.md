# Sprint 12 - Notifications and Ticketing

## Objective

Build notification-service (9009) and ticket-service (9010): multi-channel templated notifications
that consume domain events and respect user preferences, and customer ticketing with SLA-based
auto-assignment. Notification-service closes the loops opened by earlier sprints (welcome SMS,
invoice email, quota-threshold SMS).

Covers FR-28, FR-29, FR-30 (notification) and FR-31, FR-32, FR-33 (ticket).

## Included Epics

- Epic 12: Engagement and Support (notification-service, ticket-service)

## Tasks

---

### 12.1 Notification Service - Scaffold and Schema

#### 12.1.1 Scaffold notification-service from template
- ID: 12.1.1
- Title: Create notification-service from the service template
- Description: Instantiate `microservices/notification-service` (port 9009, base package
  `com.telco.notification`) from the template; depend on starter-api, starter-security,
  starter-observability, starter-inbox, starter-outbox; own the `notification` database. Architecture mode: Simple
  Service Layer (ADR-004) - Controller -> Service -> Repository over template CRUD plus channel
  adapters; no mediator/CQRS. Idempotent event consumption uses `InboxService.firstSeen(...)`
  directly in the Kafka consumers (not the mediator InboxBehavior); the single emitted event uses the
  outbox. Declare the mode in CLAUDE.md/README.
- Business Purpose: Standardized notification-domain service skeleton.
- Inputs: ADR-017.
- Outputs: notification-service skeleton building and registering.
- Acceptance Criteria:
  - Service starts, registers, exposes Swagger UI.
- Dependencies: 3.4.1, Sprint 04
- Complexity: S

#### 12.1.2 Notification schema migration
- ID: 12.1.2
- Title: Create Flyway migration for templates, notifications, preferences
- Description: `V1__notification.sql` creating `notification_templates` (id, code, channel, locale,
  subject, body_template), `notifications` (id, user_id, template_code, channel, payload_json, status,
  sent_at), and `communication_preferences` (user_id, channel, opted_in), plus inbox table.
- Business Purpose: Persist templates, sent notifications, and opt-in/out preferences (FR-29, FR-30).
- Inputs: analysis Section 10.8, FR-29, FR-30.
- Outputs: Flyway migration.
- Acceptance Criteria:
  - Migration applies; templates keyed by (code, channel, locale); preferences capture per-channel
    opt-in/out.
- Dependencies: 12.1.1
- Complexity: M

---

### 12.2 Notification Channels and Templates

#### 12.2.1 Channel adapters (SMS, email, push - mock)
- ID: 12.2.1
- Title: Implement mock SMS/email/push channel adapters
- Description: A `NotificationChannel` port with mock SMS, email, and push implementations that log
  the dispatch (mock channel per MVP scope) and return a delivery result; resilience-wrapped (NFR-10).
- Business Purpose: Multi-channel delivery abstraction (FR-28).
- Inputs: FR-28, analysis Section 6.1 (mock channel).
- Outputs: Channel port + mock adapters.
- Acceptance Criteria:
  - Each channel dispatches via its mock adapter and records a `notifications` row with status SENT;
    a failing adapter records FAILED.
- Dependencies: 12.1.2
- Complexity: M

#### 12.2.2 Template rendering
- ID: 12.2.2
- Title: Implement template lookup and rendering
- Description: Render a notification from a stored template by (code, channel, locale) with variable
  substitution from the event payload (FR-29). Seed templates for welcome, KYC result, invoice issued,
  quota 80%, quota 100% (addon recommendation), ticket opened.
- Business Purpose: Consistent, localizable messaging (FR-29).
- Inputs: FR-29, AC-01/02/03 messages.
- Outputs: Template engine + seeded templates.
- Acceptance Criteria:
  - A template renders with substituted variables; a missing template/locale falls back per a defined
    rule; seeded templates exist for the listed events.
- Dependencies: 12.1.2
- Complexity: M

#### 12.2.3 Preference enforcement
- ID: 12.2.3
- Title: Enforce opt-in/opt-out before dispatch
- Description: Before sending, check the user's per-channel preference and suppress dispatch when
  opted out, recording the suppression (FR-30). Provide endpoints to read/update preferences.
- Business Purpose: Respect customer communication consent (FR-30, KVKK/GDPR).
- Inputs: FR-30.
- Outputs: Preference check + endpoints.
- Acceptance Criteria:
  - An opted-out user on a channel is not sent on that channel (suppression recorded); preference
    updates take effect on the next dispatch.
- Dependencies: 12.2.1
- Complexity: M

---

### 12.3 Notification Eventing and API

#### 12.3.1 Domain-event-to-notification consumers
- ID: 12.3.1
- Title: Consume domain events and dispatch templated notifications
- Description: Idempotent (inbox) consumers mapping domain events to templates/channels:
  `subscription.activated.v1` -> welcome SMS; `customer.kyc-approved.v1`/`kyc-rejected.v1` -> result;
  `invoice.generated.v1` -> email; `quota.threshold-reached.v1` -> 80% SMS; `quota.exceeded.v1` ->
  addon-recommendation SMS; `ticket.opened.v1` -> ticket SMS. Emit `notification.dispatched.v1`.
- Business Purpose: Close all engagement loops opened by earlier sprints (FR-28, AC-01/02/03).
- Inputs: event-catalog consumers, AC-01/02/03.
- Outputs: Event-to-notification consumers + `notification.dispatched.v1`.
- Acceptance Criteria:
  - Each listed event triggers exactly one preference-respecting dispatch (inbox-deduplicated) and a
    `notification.dispatched.v1` event; AC-01 welcome SMS, AC-02 invoice email, and AC-03 80%/100%
    SMS now flow through this service.
- Dependencies: 12.2.2, 12.2.3
- Complexity: L

#### 12.3.2 Notification API
- ID: 12.3.2
- Title: Implement internal send and history endpoints
- Description: `POST /api/v1/notifications` (internal, ad-hoc send) and
  `GET /api/v1/notifications/users/{id}/history` returning `ApiResult` with pagination.
- Business Purpose: Direct send and audit/history visibility (FR-28).
- Inputs: FR-28, analysis Section 8.8.
- Outputs: Endpoints + DTOs.
- Acceptance Criteria:
  - Internal send dispatches respecting preferences; history returns a user's notifications paginated.
- Dependencies: 12.2.1
- Complexity: S

---

### 12.4 Ticket Service - Scaffold and Schema

#### 12.4.1 Scaffold ticket-service from template
- ID: 12.4.1
- Title: Create ticket-service from the service template
- Description: Instantiate `microservices/ticket-service` (port 9010, base package `com.telco.ticket`)
  from the template; depend on starter-api, starter-security, starter-mediator, starter-observability,
  starter-outbox, starter-inbox; own the `ticket` database; CQRS+Mediator.
- Business Purpose: Standardized ticket-domain service skeleton.
- Inputs: ADR-017.
- Outputs: ticket-service skeleton building and registering.
- Acceptance Criteria:
  - Service starts, registers, exposes Swagger UI.
- Dependencies: 3.4.1, Sprint 04
- Complexity: S

#### 12.4.2 Ticket schema migration
- ID: 12.4.2
- Title: Create Flyway migration for tickets, comments, SLA
- Description: `V1__ticket.sql` creating `tickets` (id, customer_id, category, priority, status,
  assigned_team, sla_due_at, created_at, resolved_at), `ticket_comments` (id, ticket_id, author_id,
  body, created_at), and an `sla_policies` table (category, priority, resolution_minutes, team), plus
  outbox/inbox tables.
- Business Purpose: Persist tickets, comments, and SLA policies (FR-31, FR-32).
- Inputs: analysis Section 10.9, FR-31, FR-32.
- Outputs: Flyway migration.
- Acceptance Criteria:
  - Migration applies; ticket status supports OPEN/ASSIGNED/RESOLVED; SLA policies map category/
    priority to a team and resolution target.
- Dependencies: 12.4.1
- Complexity: M

---

### 12.5 Ticket Domain and API

#### 12.5.1 Open ticket with SLA assignment
- ID: 12.5.1
- Title: Implement POST /api/v1/tickets with SLA-based auto-assignment
- Description: `OpenTicketCommand` creating a ticket, computing `sla_due_at` and `assigned_team` from
  the SLA policy for its category/priority (FR-32), and publishing `ticket.opened.v1` and
  `ticket.assigned.v1`.
- Business Purpose: Capture and route customer requests (FR-31, FR-32).
- Inputs: FR-31, FR-32, event-catalog ticket events.
- Outputs: Open command, endpoint, SLA assignment, events.
- Acceptance Criteria:
  - Opening a ticket sets `sla_due_at` and a team per policy, emits `ticket.opened.v1` and
    `ticket.assigned.v1`; `ticket.opened.v1` ultimately notifies the customer (FR-33 via 12.3.1).
- Dependencies: 12.4.2
- Complexity: M

#### 12.5.2 Comment, assign, resolve
- ID: 12.5.2
- Title: Implement comment/assign/resolve endpoints
- Description: `POST /{id}/comments`, `POST /{id}/assign`, `POST /{id}/resolve` commands; resolve sets
  `resolved_at` and emits `ticket.resolved.v1`; manual assign emits `ticket.assigned.v1`.
- Business Purpose: Ticket workflow management (FR-31).
- Inputs: FR-31, analysis Section 8.9.
- Outputs: Commands + endpoints + events.
- Acceptance Criteria:
  - Comments append; assign updates the team and emits the event; resolve transitions to RESOLVED and
    emits `ticket.resolved.v1`.
- Dependencies: 12.5.1
- Complexity: M

#### 12.5.3 SLA breach detection
- ID: 12.5.3
- Title: Detect SLA breaches and emit ticket.sla-breached.v1
- Description: A scheduled check emitting `ticket.sla-breached.v1` for unresolved tickets past
  `sla_due_at` (consumed by notification).
- Business Purpose: Surface SLA violations for escalation (FR-32).
- Inputs: FR-32, event-catalog `ticket.sla-breached.v1`.
- Outputs: SLA-breach scheduler + event.
- Acceptance Criteria:
  - An unresolved ticket past its SLA emits `ticket.sla-breached.v1` once.
- Dependencies: 12.5.1
- Complexity: S

#### 12.5.4 Ticket read endpoint
- ID: 12.5.4
- Title: Implement GET /api/v1/tickets/{id}
- Description: `GetTicketQuery` returning the ticket with comments and SLA status as `ApiResult`.
- Business Purpose: Ticket visibility (FR-31).
- Inputs: FR-31.
- Outputs: Query + endpoint.
- Acceptance Criteria:
  - GET returns the ticket and its comments; unknown id returns 404.
- Dependencies: 12.5.1
- Complexity: S

---

### 12.6 Tests

#### 12.6.1 Notification and ticket integration tests
- ID: 12.6.1
- Title: Add notification/ticket integration tests (Testcontainers)
- Description: Testcontainers (Postgres, Kafka) tests covering event-to-notification dispatch with
  preference suppression and idempotency, template rendering, ticket open/assign/resolve with events,
  and SLA breach. Verify AC-01 welcome SMS, AC-02 invoice email, and AC-03 threshold SMS now dispatch.
- Business Purpose: Verify engagement and support domains (NFR-17).
- Inputs: 12.3, 12.5.
- Outputs: Integration test suites for both services.
- Acceptance Criteria:
  - FR-28..33 flows pass; opted-out users are suppressed; ticket open notifies the customer; the
    AC-01/02/03 notification steps are confirmed dispatched.
- Dependencies: 12.3.1, 12.5.3, 12.5.4
- Complexity: L

---

## Sprint Deliverables

- notification-service (9009): mock SMS/email/push channels, templates, preference enforcement,
  domain-event consumers closing AC-01/02/03 messaging, and notification API.
- ticket-service (9010): SLA-based ticket open/assign/resolve, comments, SLA-breach detection, and
  ticket events.
- Integration tests for both.

## Exit Criteria

- Domain events trigger preference-respecting templated notifications; the AC-01 welcome SMS, AC-02
  invoice email, and AC-03 quota SMS dispatch through notification-service.
- Tickets are SLA-auto-assigned on open, notify the customer, and progress through resolve with
  breach detection.
- FR-28, FR-29, FR-30, FR-31, FR-32, FR-33 pass.
</content>
