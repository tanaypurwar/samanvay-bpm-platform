# Technical Requirements Document

## Tasklist Web — Modern React Interface for Camunda

*Version 0.1 | Draft for engineering review | 23 October 2026*

| Document field | Value |
| --- | --- |
| Product name | Tasklist Web |
| Document type | Technical Requirements Document (TRD) |
| Status | Draft — assumptions and open decisions are explicitly identified |
| Primary users | Business users and operational teams completing assigned workflow tasks |
| Source | Handwritten project notes supplied by the requester |

---

## Contents

1. [Purpose and scope](#1-purpose-and-scope)
2. [Requirements from source notes](#2-requirements-from-source-notes)
3. [Proposed solution architecture](#3-proposed-solution-architecture)
4. [Functional requirements](#4-functional-requirements)
5. [Dynamic form requirements](#5-dynamic-form-requirements)
6. [Camunda API integration](#6-camunda-api-integration)
7. [Non-functional requirements](#7-non-functional-requirements)
8. [Security requirements](#8-security-requirements)
9. [Deployment and operations](#9-deployment-and-operations)
10. [Testing and acceptance](#10-testing-and-acceptance)
11. [Delivery plan](#11-delivery-plan)
12. [Open decisions and assumptions](#12-open-decisions-and-assumptions)
13. [Key implementation recommendations](#13-key-implementation-recommendations)
- [Appendix A — Suggested repository structure](#appendix-a--suggested-repository-structure)
- [Appendix B — Definition of done](#appendix-b--definition-of-done)

---

## 1. Purpose and scope

Tasklist Web is a modern, mobile-friendly web application intended to provide an improved user experience over the default Camunda Tasklist interface. It will use Camunda REST APIs for workflow-task operations, provide configurable/dynamic forms, and be deployed alongside the existing Camunda environment.

### 1.1 Objectives

- Deliver a clean, responsive, accessible interface for viewing and completing assigned tasks.
- Use Camunda REST APIs as the workflow integration boundary rather than reimplementing workflow-engine behavior.
- Support configurable forms, with form definitions and configuration stored in additional application database tables where required.
- Use a reusable UI component library to accelerate consistent form rendering and future enhancements.
- Deploy the React application in the same AWS environment as Camunda for the initial release, subject to security and capacity review.

### 1.2 In scope

- Task list and task details views.
- Task search/filter/sort/pagination, subject to API support.
- Dynamic form rendering and validation.
- Task claim/assign, complete, and other task actions exposed by the selected Camunda version and permitted to the user.
- Authentication/session integration with the existing environment.
- Application configuration storage, logging, deployment, backup and operational checks.

### 1.3 Out of scope for the initial release

- Replacing or modifying Camunda process-engine execution semantics.
- A new workflow-modeling tool or BPMN editor.
- Native iOS/Android applications; the first release is a responsive web app that can be used on mobile browsers.
- A full redesign of Camunda Cockpit/Admin or unrelated workflow administration features.

---

## 2. Requirements from source notes

| ID | Requirement captured from notes | Status / interpretation |
| --- | --- | --- |
| SRC-01 | The default Camunda Tasklist is basic; a modern look and feel is required. | Confirmed intent |
| SRC-02 | The UI should be mobile-friendly and potentially usable as a mobile app. | Responsive web is baseline; PWA/native packaging needs a decision |
| SRC-03 | The application will use Camunda REST APIs. | Confirmed intent; API details depend on Camunda version |
| SRC-04 | Build a React-based application. | Confirmed intent |
| SRC-05 | Deploy in the same AWS environment/server as Camunda. | Proposed deployment; validate isolation, sizing and security |
| SRC-06 | Fork Camunda and customize it, adding database tables for form configuration. | Needs clarification: recommended approach is a separate UI repo; avoid modifying engine core unless essential |
| SRC-07 | Use a library for dynamic forms. | Library not yet selected |
| SRC-08 | Deploy on EC2 with a database. | EC2 target stated; database placement/type remains open |

---

## 3. Proposed solution architecture

**Recommended baseline:** a separately versioned React application served as static assets by Nginx (or an equivalent web server), with a small backend-for-frontend (BFF) only if needed for authentication, secrets, API normalization, or server-side form configuration. The browser must not contain database credentials or privileged Camunda credentials.

| Layer | Responsibility | Notes |
| --- | --- | --- |
| Web UI | React + TypeScript; responsive task list, task detail and dynamic forms | Build as static assets; route/API errors handled consistently |
| UI/component library | Accessible inputs, layout, dialogs, tables and feedback | Select after a short proof of concept; avoid locking into an unverified library |
| Form renderer | Render field definitions and validation rules from approved configuration | Schema versioning and supported field types required |
| Integration | Camunda REST API calls | Confirm Camunda 7 vs Camunda 8 and exact API/auth model before implementation |
| Optional BFF/API | Session handling, authorization enforcement, config APIs, audit/logging | Required if browser-to-Camunda auth cannot be secured cleanly |
| Database | Store application-owned form definitions/configuration and metadata | Keep application tables logically separate from Camunda engine tables |
| Hosting | AWS EC2, TLS termination, reverse proxy, deployment and monitoring | Co-location may be acceptable for a pilot; production should assess blast radius and scaling |

### 3.1 High-level request flow

1. User opens Tasklist Web over HTTPS and signs in through the approved identity provider or existing Camunda authentication flow.
2. React loads the task list and calls the approved API endpoint.
3. The API integration layer requests task data from Camunda and returns only data the user is authorized to access.
4. When a task is opened, the application loads its form configuration and current task variables.
5. The form renderer validates input; the application submits permitted variable updates and the task-completion action to Camunda.
6. The UI displays success or actionable error feedback and refreshes the task state.

---

## 4. Functional requirements

| ID | Requirement | Priority | Acceptance criteria |
| --- | --- | --- | --- |
| FR-01 | Task inbox: show tasks available to the current user, with key fields such as task name, assignee, creation date and process context where supported. | Must | Authorized user sees relevant tasks; empty and error states are clear. |
| FR-02 | Task search and filtering by supported attributes. | Should | Filters are applied consistently; pagination does not lose active filters. |
| FR-03 | Task details: show task metadata and relevant process/business context. | Must | Selected task details match the Camunda API response. |
| FR-04 | Task claim/assign/release actions where supported and authorized. | Must | Action is reflected after server confirmation; duplicate clicks are prevented. |
| FR-05 | Dynamic form rendering from stored configuration. | Must | Supported schema renders expected fields and initial values. |
| FR-06 | Form validation on client and server/API boundary as appropriate. | Must | Invalid required fields and configured rules block submission and explain the issue. |
| FR-07 | Submit form variables and complete task. | Must | Task completes only after successful API response; failures preserve user-entered values. |
| FR-08 | Handle API errors, expired sessions, unavailable engine and concurrency conflicts. | Must | User receives safe, actionable messages; technical details go to logs. |
| FR-09 | Responsive layouts for desktop, tablet and mobile browsers. | Must | Core task flows work at narrow mobile widths without horizontal page scrolling. |
| FR-10 | Form configuration administration. | Should | Authorized administrators can create/edit/publish configurations, or a documented controlled process is used for v1. |
| FR-11 | Audit-relevant application events. | Should | Configuration changes and sensitive actions are logged with actor/time/correlation ID, subject to policy. |
| FR-12 | Save draft locally or server-side. | Could | Only implement after security/privacy and workflow requirements are confirmed. |

---

## 5. Dynamic form requirements

### 5.1 Configuration model

Form definitions should be stored as versioned JSON documents in application-owned tables. A form should be associated with a task definition or another stable identifier agreed for the selected Camunda version. Do not rely on display names alone if they can change or collide.

| Entity / table (proposed) | Purpose | Important fields (illustrative) |
| --- | --- | --- |
| `form_definition` | Logical form identity | `id`, `form_key`, `name`, `description`, `status`, `created_at`, `updated_at` |
| `form_version` | Immutable published schema version | `id`, `form_definition_id`, `version`, `schema_json`, `ui_json`, `published_at`, `published_by` |
| `form_binding` | Maps a task/process identifier to a form version or selection rule | `id`, `task_definition_key`, `process_definition_key` (optional), `form_version_id`, `priority` |
| `form_audit_event` | Tracks configuration lifecycle events | `id`, `form_version_id`, `action`, `actor`, `timestamp`, `correlation_id` |

> These are conceptual names, not a finalized migration. Use database-specific naming conventions and foreign keys. Published versions should be immutable; edits should create a new version to protect tasks already in progress.

### 5.2 Initial supported field types

- Text, textarea, number, date, checkbox/boolean, single-select, multi-select and read-only display fields.
- Required/optional rules, min/max, length limits, allowed values, and clear validation messages.
- Default values and initial values mapped from task variables.
- Conditional visibility and simple field dependencies only if required by initial use cases.
- Accessible labels, keyboard navigation, error associations and mobile-friendly controls.

### 5.3 Variable mapping

Each configured field must map to a defined Camunda process variable name and type. Define explicit conversion rules for dates, numbers, booleans, arrays and null values. Reject unknown or disallowed variable names rather than blindly accepting arbitrary client payloads.

---

## 6. Camunda API integration

The exact endpoint set must be confirmed after identifying the engine version and deployment topology. Camunda 7 and Camunda 8 have materially different API and task-management models; implementation must not assume interchangeability.

| Requirement | Specification |
| --- | --- |
| API boundary | All task operations must go through supported APIs; do not write directly to Camunda engine tables. |
| Version compatibility | Record engine version, API base URL, authentication mode and required endpoints in environment configuration. |
| Authorization | Enforce user identity and task permissions; do not trust client-supplied user IDs or roles. |
| Timeout/retry | Use bounded timeouts. Retry only safe/idempotent operations; do not blindly retry task completion. |
| Error handling | Normalize API errors for the UI while retaining sanitized diagnostics in server logs. |
| API evolution | Keep API calls in a dedicated service layer, not spread throughout React components. |

---

## 7. Non-functional requirements

| Area | Requirement / target for initial release |
| --- | --- |
| Usability | Consistent navigation, clear task status, loading/empty/error states and minimal clicks for common task completion. |
| Responsive design | Core workflows usable at 360 px viewport width and above; verify supported browsers. |
| Accessibility | Aim for WCAG 2.2 AA for key task flows; keyboard operation, visible focus and screen-reader labels. |
| Performance | Target p95 UI/API response under 2 seconds for common list/detail requests under agreed pilot load, excluding slow engine operations. |
| Security | HTTPS, secure session handling, least privilege, input validation, CSRF protection where cookie auth is used, and no secrets in frontend assets. |
| Privacy | Avoid logging form values or sensitive variables unless explicitly approved; define retention and access controls. |
| Reliability | Clear degraded-state behavior when Camunda or the database is unavailable; health checks for deployed services. |
| Maintainability | TypeScript, linting, automated tests, environment-specific configuration and documented API contracts. |
| Observability | Structured logs, correlation IDs, basic metrics and alerts for application/API failures. |

---

## 8. Security requirements

- Use the organization's approved identity provider and session model; do not create a separate password store without an explicit requirement.
- Terminate TLS at an approved load balancer/reverse proxy and secure traffic between application components where appropriate.
- Store secrets in AWS Secrets Manager or Systems Manager Parameter Store, not source control or frontend environment bundles.
- Restrict EC2 security groups to required ingress; do not expose the database publicly.
- Use separate database credentials with least privilege; application schema must not have unnecessary privileges over Camunda engine schema.
- Validate authorization on the server/API boundary for every task and form-configuration operation.
- Protect against XSS, CSRF (where applicable), injection, unsafe file uploads (if later introduced), and leakage of task variables.
- Keep dependencies patched and scan dependencies/container or AMI artifacts as part of CI/CD.

---

## 9. Deployment and operations

### 9.1 Initial deployment proposal

- Build the React application in CI and publish versioned static assets.
- Serve assets through Nginx or an equivalent reverse proxy/web server on EC2.
- Route API calls to Camunda and, if needed, a separate BFF endpoint; avoid exposing privileged engine endpoints directly to the public internet.
- Use a database instance/schema for Tasklist Web configuration. For a pilot, a same-host database may be evaluated, but production separation is preferred for resilience and maintenance.
- Use HTTPS, a managed DNS name, access-controlled administration, automated backups and documented restore steps.
- Deploy dev/test/prod with separate configuration and secrets; promote tested artifacts rather than building manually on production.

### 9.2 Operational requirements

- Health endpoint(s) for the web app and any BFF; avoid exposing sensitive environment details.
- Centralized logs with timestamps, severity, request/correlation IDs and redaction of sensitive data.
- Database backup schedule, retention, encryption and periodic restore test.
- Deployment rollback procedure and versioned database migrations.
- Monitor CPU, memory, disk, HTTP errors, API latency, database connectivity and certificate expiry.

---

## 10. Testing and acceptance

| Test level | Coverage |
| --- | --- |
| Unit | Form-schema parsing, validation, variable mapping, API service methods, error normalization and UI components. |
| Integration | Camunda API behavior, authentication, task actions, variable serialization, database migrations and form-version lookup. |
| End-to-end | Sign in → open task list → open task → complete valid form; reject invalid form; handle API failure/session expiry. |
| Responsive/accessibility | Mobile viewport, keyboard-only operation, focus order, accessible labels and error announcements. |
| Security | Authorization bypass attempts, XSS/injection checks, CSRF where applicable, secret exposure and dependency scan. |
| Performance | Representative task-list size, concurrent users and p95 response target agreed before pilot. |

### 10.1 Release acceptance criteria

- [ ] A permitted user can view and open assigned/available tasks supported by the agreed API.
- [ ] A configured form renders correctly, loads task variables, validates input and submits values successfully.
- [ ] A task cannot be marked complete in the UI unless the engine confirms completion.
- [ ] Unauthorized users cannot access tasks or change form configuration by manipulating requests.
- [ ] The UI remains usable on mobile browsers and communicates loading, empty, success and failure states.
- [ ] Deployment, backup, restore and rollback procedures are documented and tested.

---

## 11. Delivery plan

| Phase | Deliverables | Exit condition |
| --- | --- | --- |
| 0. Discovery | Confirm Camunda version, authentication, task use cases, form examples, hosting topology and expected load. | Architecture decisions recorded |
| 1. Foundation | React/TypeScript app, CI build, routing, design system, API client and authentication flow. | User can sign in and load a basic task list |
| 2. Task workflows | Task detail, supported task actions, API error handling and task variables. | Core task lifecycle works in integration test |
| 3. Dynamic forms | Schema model, database migration, renderer, validation and variable mapping. | Representative forms pass acceptance tests |
| 4. Hardening | Security review, responsive/accessibility checks, logging, monitoring and performance tests. | Release criteria met |
| 5. Pilot | Deploy to agreed EC2 environment, run user acceptance testing and document operations. | Pilot sign-off |

---

## 12. Open decisions and assumptions

| ID | Question / decision | Why it matters | Suggested default |
| --- | --- | --- | --- |
| OD-01 | Which engine is in use: Camunda 7 or Camunda 8, and which exact version? | Determines API, task model, authentication and compatibility. | Confirm before selecting endpoints. |
| OD-02 | Does "fork Camunda" mean fork the Tasklist UI only or modify engine source code? | Engine forks increase upgrade and support cost. | Keep a separate Tasklist Web repository; avoid engine-core changes. |
| OD-03 | Which dynamic form library should be used? | Impacts schema format, licensing, accessibility and conditional logic. | Run a small proof of concept against 2–3 real forms. |
| OD-04 | Which database engine and where will it run? | Affects migrations, cost, backup and availability. | Reuse an approved relational database if appropriate; keep app tables separate. |
| OD-05 | What identity provider and authentication mechanism are already used? | Security architecture depends on SSO/session/token approach. | Integrate with existing organizational SSO. |
| OD-06 | Should mobile support be responsive web only, installable PWA, or native app? | Changes offline, push-notification and packaging scope. | Responsive web first; consider PWA later. |
| OD-07 | Who can create/publish form configurations? | Determines administration UI and governance. | Restrict publishing to named admin roles. |
| OD-08 | What is the expected number of users, task volume and availability target? | Needed for sizing and production architecture. | Set pilot targets before performance testing. |
| OD-09 | Which task actions, filters and form types are required for v1? | Prevents scope ambiguity. | Prioritize the most common operational workflow. |
| OD-10 | Will the app share an EC2 host with Camunda? | Co-location can create resource contention and common failure modes. | Allowed for a small pilot only after capacity/security review. |

### 12.1 Decisions recorded (24 October 2026)

| ID | Decision |
| --- | --- |
| OD-01 | Camunda 7.24, as the Samanvay BPM Platform fork in this repository. The tasklist uses the Camunda 7 REST API (`/engine-rest`). |
| OD-02 | The engine is forked and will be heavily customized. The tasklist is a **separate application** in the same repository (`samanvay-tasklist/`) and depends on the engine only through REST. |
| OD-03 | Forms use **RJSF** (react-jsonschema-form): JSON Schema draft-07 plus a UI schema, rendered with the MUI theme and validated again on the server. |
| OD-04 | PostgreSQL, with the tasklist tables in their own `tasklist` schema managed by Flyway. H2 is used for local development. |
| OD-05 | v1 signs users in with **Camunda users and groups** (engine identity service). An OIDC/SSO provider can replace this later. |
| OD-06 | Responsive web first. |
| OD-07 | Only members of `camunda-admin` / `tasklist-admin` can publish forms (configurable). |
| — | Backend: a Spring Boot BFF holds the session and the engine service account. Browsers never call `/engine-rest` directly. |

---

## 13. Key implementation recommendations

- Do not alter Camunda database tables or write workflow state directly to the engine database; use supported APIs.
- Treat form configuration as application-owned data with versioning and audit history.
- Use a dedicated API integration layer and keep authentication/authorization rules out of presentation components.
- Do not finalize the UI/form library until a representative form and mobile flow have been prototyped.
- If the application is intended for production, assess whether sharing the Camunda EC2 instance meets availability, security and scaling requirements.

---

## Appendix A — Suggested repository structure

> Implemented as `samanvay-tasklist/` in this repository: a single Spring Boot module with the React app in `frontend/`. See its README.

```text
tasklist-web/
  apps/
    web/                  # React + TypeScript frontend
    api/                  # Optional BFF/configuration API
  packages/
    ui/                   # Shared UI components
    form-schema/          # Form schema types and validation
    camunda-client/       # Typed API client and response mapping
  database/
    migrations/
    seeds/
  infra/
    ec2/
    nginx/
    monitoring/
  tests/
    integration/
    e2e/
  docs/
    architecture.md
    operations.md
    api-contracts.md
```

## Appendix B — Definition of done

- [ ] Requirements and acceptance criteria are met.
- [ ] Code review completed; lint, unit and integration tests pass.
- [ ] Security and accessibility checks completed for key flows.
- [ ] Database migration and rollback are documented.
- [ ] Environment configuration and secrets are externalized.
- [ ] Logs, health checks, backup and restore instructions are verified.
- [ ] Release notes and known limitations are documented.
