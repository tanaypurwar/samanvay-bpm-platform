# Samanvay Tasklist

A modern, mobile-friendly task application for the Samanvay BPM Platform. It runs **beside** the engine
as a separate application and talks to it only through the engine REST API (`/engine-rest`), so the engine
fork can be customized freely without coupling the UI to engine internals.

Requirements: [`../design/Tasklist_Web_Technical_Requirements_Document.md`](../design/Tasklist_Web_Technical_Requirements_Document.md)

```text
 Browser (React + MUI + RJSF)
        │  same origin: session cookie + CSRF token
        ▼
 Samanvay Tasklist  :8081   Spring Boot BFF ── own DB (form definitions, versions, bindings, audit)
        │  service account, HTTP Basic
        ▼
 Samanvay BPM engine :8080  /engine-rest (Camunda 7 REST API)
```

## Features

- **Sign-in with engine users and groups.** Passwords are checked with `POST /identity/verify` and never stored. Sessions use an HTTP-only cookie with CSRF protection.
- **Task inbox.** Tabs for My tasks / Available / All, search, sort and pagination. The filters live in the URL. Wide screens get a table and phones get cards.
- **Task actions.** Claim, release and complete.
- **Visibility enforced in the backend.** A user only sees tasks assigned to them, or unassigned tasks where they are a candidate directly or through a group. Anything else returns 404.
- **Dynamic forms with [RJSF](https://rjsf-team.github.io/react-jsonschema-form/)** (JSON Schema draft-07):
  - Conditional rules (`if`/`then`) work.
  - Form data is validated again on the server, and fields the form doesn't declare are rejected.
  - Values map to typed Camunda variables: String, Long, Double, Boolean, Date and Json. Set `x-camunda-type` on a field to override the type.
  - `readOnly` fields are displayed but never written back.
- **Form administration** for members of `camunda-admin` / `tasklist-admin`:
  - JSON editors with a live preview.
  - Draft, then publish. Each publish creates a new immutable version.
  - Task bindings and an audit trail.

## Linking a form to a user task

In order of precedence:

1. **In the BPMN:** `camunda:formKey="samanvay:<form key>"` on the user task (see `examples/invoice-approval.bpmn`).
2. **Binding in the app:** map a task definition key, optionally restricted to a process definition key, to a form on the form's admin page. The highest priority wins.

Tasks always get the latest published version of their form. If a new version is published while someone is filling in a form, their submission is still validated against the version they opened.

## Running locally

Prerequisites:
- Java 17
- An engine with the REST API on `:8080`

The Maven build downloads Node 20.14.0 by itself. For frontend development you need a local Node 20+.

```bash
# 1. Engine: Camunda Run from this repo (distro/run), REST API with auth enabled.
#    Merge examples/engine-application.yml into configuration/default.yml, then
./start.sh --rest            # Windows: start.bat --rest

# 2. Tasklist backend (dev profile = file-based H2 + the example form)
cd samanvay-tasklist
../mvnw spring-boot:run -DskipFrontend=true

# 3. Frontend with hot reload on http://localhost:5173 (proxies /api to :8081)
cd frontend && npm install && npm run dev
```

Try it with the example process:

```bash
curl -u demo:demo -F deployment-name=invoice-approval -F data=@examples/invoice-approval.bpmn \
  http://localhost:8080/engine-rest/deployment/create
curl -u demo:demo -H 'Content-Type: application/json' \
  -d '{"variables":{"invoiceNumber":{"value":"INV-1001"},"vendor":{"value":"Acme"},"amount":{"value":48250.5,"type":"Double"},"invoiceDate":{"value":"2026-10-20T00:00:00.000+0000","type":"Date"}}}' \
  http://localhost:8080/engine-rest/process-definition/key/invoice-approval/start
```

Then sign in as `demo` / `demo`, open **Available**, claim the task and complete it.

The dev H2 database lives in `target/tasklist-db`, so `mvn clean` resets it.

## Building and deploying

```bash
../mvnw package             # tests + React build → target/samanvay-tasklist-<version>.jar
java -jar target/samanvay-tasklist-*.jar --spring.profiles.active=prod
```

This is one jar serving both the UI and the API on port 8081. Put it behind Nginx or a load balancer that terminates TLS.

| Setting | Env var | Default |
| --- | --- | --- |
| Engine REST URL | `SAMANVAY_ENGINE_BASE_URL` | `http://localhost:8080/engine-rest` |
| Engine service account | `SAMANVAY_ENGINE_USERNAME` / `SAMANVAY_ENGINE_PASSWORD` | `demo` / `demo` (change in prod) |
| Database (prod profile) | `SAMANVAY_TASKLIST_DB_URL` / `_USERNAME` / `_PASSWORD` | `jdbc:postgresql://localhost:5432/samanvay`, schema `tasklist` |
| Form admin groups | `SAMANVAY_TASKLIST_ADMIN_GROUPS` | `camunda-admin,tasklist-admin` |

**Production notes:**
- Load the secrets from AWS Secrets Manager or SSM Parameter Store.
- Give the service account only the authorizations it needs: READ/UPDATE/TASK_WORK on tasks, and READ on process definitions, users and groups.
- Don't expose `/engine-rest` to browsers.
- Flyway manages the tasklist schema (`src/main/resources/db/migration`). The `docker-compose.dev.yml` file runs PostgreSQL if you want to try the prod profile locally.

## Project layout

```text
src/main/java/com/samanvay/tasklist/
  auth/      sign-in against engine identity, session user
  config/    security (session, CSRF, JSON 401/403), engine RestClient, SPA routing
  engine/    CamundaClient: every engine REST call goes through here
  task/      task API and the visibility guard
  form/      form entities, resolver, JSON Schema validator, variable mapper, admin API
  common/    error model, correlation ids
frontend/src/
  api/       typed fetch client (CSRF header, error normalization, session-expiry hook)
  pages/     inbox, task detail, login, admin form list/editor
  components/DynamicForm (RJSF + MUI), TaskList, AppShell, JsonEditor
```

## Tests

```bash
../mvnw test -DskipFrontend=true      # backend: unit tests + full-context API tests on H2
cd frontend && npm test               # frontend: Vitest + Testing Library
```
