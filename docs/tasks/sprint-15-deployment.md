# Sprint 15 - Deployment

## Objective

Make the platform deployable to Kubernetes with full CI/CD: per-service container images,
Kubernetes manifests/Helm charts with config and secrets, horizontal pod autoscaling, a complete
build-test-scan-push-deploy pipeline, and a verified rollback path. This is the final MVP sprint.

Covers NFR-03 (HPA), NFR-04 (uptime), and the CI/CD/deployment requirements (ADR-014).

## Included Epics

- Epic 15: Containerization, Kubernetes, and CI/CD

## Tasks

---

### 15.1 Containerization

#### 15.1.1 Production Dockerfiles for all services
- ID: 15.1.1
- Title: Finalize multi-stage Dockerfiles for every service
- Description: Ensure each of the 13 services (3 infra + 10 domain) has a multi-stage, layered,
  non-root, JRE-21 image with a healthcheck and the actuator health endpoint exposed.
- Business Purpose: Reproducible, secure runtime artifacts (ADR-014).
- Inputs: ADR-014, analysis Section 11.
- Outputs: Dockerfiles per service.
- Acceptance Criteria:
  - Each image builds, runs as non-root, starts, and reports healthy via its actuator probe.
- Dependencies: Sprints 04-12
- Complexity: M

#### 15.1.2 Image build and registry push in CI
- ID: 15.1.2
- Title: Build and push images on merge
- Description: CI stage building each changed service image, tagging by version+commit, and pushing to
  the container registry (ADR-014).
- Business Purpose: Continuous delivery of deployable artifacts (ADR-014).
- Inputs: ADR-014, 1.4.1.
- Outputs: CI image build/push stage.
- Acceptance Criteria:
  - A merge produces versioned images in the registry for changed services.
- Dependencies: 15.1.1, 1.4.1
- Complexity: M

---

### 15.2 Kubernetes Manifests

#### 15.2.1 Base manifests/Helm charts per service
- ID: 15.2.1
- Title: Author Kubernetes Deployment/Service/Ingress per service
- Description: Helm charts (or Kustomize bases) for every service: Deployment (probes, resource
  requests/limits), Service, and Ingress/Gateway route for externally exposed paths. Stateless,
  HPA-ready (NFR-03).
- Business Purpose: Declarative, scalable deployment (NFR-03, ADR-014).
- Inputs: analysis Section 7.2, NFR-03, ADR-010.
- Outputs: Helm charts/manifests per service.
- Acceptance Criteria:
  - Each service deploys to a local cluster (Minikube/Kind); liveness/readiness probes pass; the
    gateway is reachable via Ingress.
- Dependencies: 15.1.1
- Complexity: L

#### 15.2.2 ConfigMaps and Secrets
- ID: 15.2.2
- Title: Externalize config via ConfigMaps and Secrets
- Description: Move per-service configuration to ConfigMaps and sensitive values (DB creds, JWT
  signing key, PII encryption key) to Secrets, replacing config-server for prod (ADR-010). PII/JWT
  keys are mounted from Secrets (NFR-06).
- Business Purpose: Production configuration and secret management (ADR-010, NFR-06).
- Inputs: ADR-010, NFR-06, 4.1.x.
- Outputs: ConfigMap/Secret manifests + key wiring.
- Acceptance Criteria:
  - Services read config from ConfigMaps and secrets from Secrets in-cluster; no plaintext secret is
    committed.
- Dependencies: 15.2.1
- Complexity: M

#### 15.2.3 Stateful dependencies in-cluster
- ID: 15.2.3
- Title: Provision Postgres, Kafka, Redis, Schema Registry, observability in-cluster
- Description: Provide manifests/Helm values (or documented managed-service hooks) for the stateful
  dependencies the services need, mirroring the local compose stack (ADR-006, ADR-009, ADR-012).
- Business Purpose: A complete runnable cluster environment.
- Inputs: analysis Section 7.1, Sprint 01 infra.
- Outputs: Dependency manifests/Helm values.
- Acceptance Criteria:
  - The dependency stack runs in-cluster and services connect to it; the observability stack receives
    traces/logs/metrics.
- Dependencies: 15.2.1
- Complexity: L

---

### 15.3 Autoscaling and Resilience

#### 15.3.1 Horizontal Pod Autoscalers
- ID: 15.3.1
- Title: Configure HPA for stateless services
- Description: HPA manifests scaling services on CPU/throughput targets, validating the stateless,
  horizontally scalable design (NFR-03).
- Business Purpose: Elastic capacity under load (NFR-03).
- Inputs: NFR-03, analysis Section 5.
- Outputs: HPA manifests.
- Acceptance Criteria:
  - Under synthetic load a service scales out and back in per its HPA policy.
- Dependencies: 15.2.1
- Complexity: M

#### 15.3.2 Pod disruption budgets and readiness gating
- ID: 15.3.2
- Title: Add PDBs and rollout readiness gating
- Description: PodDisruptionBudgets and rollout strategies (maxUnavailable/maxSurge) preserving
  availability during deploys, supporting the 99.5% uptime objective (NFR-04).
- Business Purpose: Maintain availability during disruptions and rollouts (NFR-04).
- Inputs: NFR-04.
- Outputs: PDB + rollout config.
- Acceptance Criteria:
  - A rolling deploy keeps the service available (no full outage) and respects the PDB.
- Dependencies: 15.2.1
- Complexity: S

---

### 15.4 CI/CD Pipeline and Rollback

#### 15.4.1 Deploy stage
- ID: 15.4.1
- Title: Add deploy-to-cluster CI/CD stage
- Description: Pipeline stage applying manifests/Helm to the target cluster after build-test-scan-push,
  gated by environment approval (ADR-014: build -> test -> docker push -> kubectl/helm apply).
- Business Purpose: Automated, gated delivery to the cluster (ADR-014).
- Inputs: ADR-014, 15.1.2.
- Outputs: Deploy stage in the pipeline.
- Acceptance Criteria:
  - A successful pipeline deploys the updated services to the target environment.
- Dependencies: 15.1.2, 15.2.2
- Complexity: M

#### 15.4.2 Rollback procedure
- ID: 15.4.2
- Title: Implement and verify rollback
- Description: A rollback path (Helm rollback / previous-revision redeploy) and a documented runbook;
  verify by deploying a deliberately broken revision and rolling back (ADR-014).
- Business Purpose: Fast recovery from a bad deploy (NFR-04, ADR-014).
- Inputs: ADR-014.
- Outputs: Rollback runbook + verification.
- Acceptance Criteria:
  - A broken deploy is detected (failing readiness) and rolled back to the last good revision with
    service restored.
- Dependencies: 15.4.1
- Complexity: M

#### 15.4.3 Smoke tests post-deploy
- ID: 15.4.3
- Title: Run post-deploy smoke tests
- Description: A smoke-test stage hitting health endpoints and one happy-path flow (login + a read)
  through the gateway after deploy, failing the pipeline (and triggering rollback) on failure.
- Business Purpose: Catch broken deploys before they take traffic (NFR-04).
- Inputs: 15.4.1.
- Outputs: Smoke-test stage.
- Acceptance Criteria:
  - The smoke stage passes on a healthy deploy and fails on a broken one, triggering rollback.
- Dependencies: 15.4.1, 15.4.2
- Complexity: S

---

### 15.5 Release Documentation

#### 15.5.1 Deployment and operations runbook
- ID: 15.5.1
- Title: Write deployment, configuration, and operations runbook
- Description: Document cluster prerequisites, configuration/secret management, deploy/rollback
  procedures, scaling, and observability access so the MVP can be operated from the docs alone.
- Business Purpose: Operability and handover (ADR-014).
- Inputs: Sprints 13-15.
- Outputs: Operations runbook.
- Acceptance Criteria:
  - A clean environment can be brought up, deployed, scaled, and rolled back following only the
    runbook.
- Dependencies: 15.4.3
- Complexity: S

---

## Sprint Deliverables

- Production container images for all 13 services pushed by CI.
- Kubernetes manifests/Helm charts with ConfigMaps/Secrets, in-cluster dependencies, HPA, PDBs, and
  a complete build-test-scan-push-deploy pipeline with verified rollback and post-deploy smoke tests.
- An operations runbook.

## Exit Criteria

- The full platform deploys to a Kubernetes cluster via CI/CD; services are stateless and autoscale
  (NFR-03); rolling deploys preserve availability (NFR-04).
- A bad deploy is caught by smoke tests and rolled back automatically.
- All MVP acceptance criteria (validated in Sprint 14) hold in the deployed environment; the platform
  is operable from the runbook.
</content>
