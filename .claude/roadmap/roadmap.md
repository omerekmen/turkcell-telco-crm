# Platform Roadmap

## System Model

Epic → Sprint → Tasks (Hybrid Agile Execution Model)

---

## Current Phase

Phase 1 — Platform Foundation

---

## Active Sprint

Sprint 001 — Platform Core Bootstrap

---

## Epics

### EPIC-001: Platform Core Foundation

Goal: Build internal platform framework

Includes:

* CQRS Engine
* Mediator Engine
* Outbox System
* Inbox System

---

### EPIC-002: Spring Boot Starter System

Goal: Expose platform-core as Spring Boot starters

Includes:

* starter-mediator
* starter-security
* starter-outbox
* starter-observability

---

### EPIC-003: Event-Driven System

Goal: Kafka + Avro ecosystem

Includes:

* Schema registry integration
* Event versioning system
* Outbox integration

---

### EPIC-004: Microservice Architecture Standardization

Goal: enforce service templates

Includes:

* ADR-017 enforcement
* service generator agent support
* standardized project structure
