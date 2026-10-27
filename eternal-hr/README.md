# Eternal HR

Process application for Eternal HR on the Samanvay BPM Platform: the **joining and documentation workflow** from
`design/Joining & Documentation Workflow Process.pptx`. It is a standalone Spring Boot app that builds and
deploys independently of the platform. It talks to the engine only through the REST API:

- On startup it **deploys its own BPMN and forms** (`src/main/resources/processes`), as one deployment.
- It **works on external tasks** (the external task pattern): every automated step is a worker in `workers/`.
- It hosts the **employee joining form** at `/joining/<signed token>`. New employees have no tasklist account.
- **Documents** (PDF, JPEG, PNG, each with its type) can be attached when the joining is raised, on every task and
  on the employee's form. They are stored in S3 (locally an S3-compatible container). Every step lists them, and
  a click opens a preview beside the form. See [ARCHITECTURE.md](ARCHITECTURE.md#7-documents).

People do their tasks in the Samanvay tasklist, branded *Eternal HR Onboarding*.

## The process (`eternal-hr-joining`)

| Step (deck) | Who | How |
| --- | --- | --- |
| 1. Joining approval | Territory Manager starts it; Reporting Manager, Country Head, Project Manager approve | Start form + 3 user tasks. Each approver can send the request back (it goes to the TM, then to the RM again) or reject it (the TM is emailed) |
| 2. Joining details | HR | *Start joining formalities* |
| 3. Joining form, HRMS record | Employee, system | A signed link is emailed. The process waits for the form, with reminders every 2 days (demo: 10 min). Then the HRMS record is created |
| 4. Induction | HR | In parallel with 5–8 |
| 5–6. Appointment letter | HR prepares, PM reviews, system emails | The PM gets the letter by email and approves it in the tasklist. Changes need the PM's written approval, then a new review. The approved letter is emailed to the employee |
| 7. Document collection | HR | Status of each required document |
| 8. Document review, follow-up | HR, system | Issues are emailed to the employee. With no response in 3 days (demo: 15 min) the PM is emailed, and HR keeps following up |
| 9–11. Correction and verification | HR, system | HR corrects the data against the documents and verifies it. The HRMS record is marked verified and the TM and PM are emailed |

Roles are engine groups: `territory-managers`, `reporting-managers`, `country-heads`, `project-managers`, `hr-team`.
The process records who approved at each level (`reportingManager`, `countryHead`, `projectManager`). The letter
review goes to the PM who approved the joining.

**Forms** are JSON Schema files in `processes/forms/`, referenced from the BPMN as
`camunda:formKey="samanvay:deployment:forms/<name>.form.json"`. The tasklist loads them from the same deployment.
**Workers** (topic → class): `ehr.joining-form.send-link`, `.send-reminder`, `ehr.hrms.create-employee`,
`.mark-verified`, `ehr.letter.send-for-approval`, `.send-to-employee`, `ehr.documents.request-correction`,
`.escalate`, `ehr.notify.joining-rejected`, `.joining-completed`. Failures are retried 3 times, a minute apart,
then raise an incident in Cockpit.

**HRMS:** `StubHrmsClient` issues employee codes (`EHR-yyMM-nnnnn`) and logs the calls. To connect the real HRMS,
add an `HrmsClient` bean.

**Personal data:** the workflow keeps only the last 4 digits of Aadhaar and the bank account. The full numbers
stay on the documents HR collects.

## Run locally

Needs Docker Desktop, Java 17 and Git Bash.

```bash
bash deploy/local/up.sh                     # platform: https://localhost:18443 (engine, tasklist, Caddy)
bash eternal-hr/deploy/scripts/up-local.sh  # Eternal HR + Mailpit next to it
```

The second script prints the demo password, which is the same for all five users, and the mailbox login:

- https://localhost:18443: sign in as `tm.demo`, then use **Start → Employee joining & documentation**. Then sign
  in as `rm.demo`, `ch.demo`, `pm.demo` and `hr.demo` in turn.
- https://localhost:18443/mail/: every email, including the employee's joining form link.
- https://localhost:18443/camunda/: Cockpit, to watch the process (the platform `admin` user).
- http://127.0.0.1:19001/rustfs/console/: the local S3 stand-in (RustFS console), with the stored documents. The keys are the
  `DOCUMENTS_*` values in `deploy/local/.env`.

Your browser warns about the local certificate once; accept it.

To run the app from the IDE instead, against the local engine on `127.0.0.1:8080`, set `ETERNAL_HR_ENGINE_PASSWORD`
to the platform admin password and run `EternalHrApplication`. The joining form is then on http://localhost:8090.

## Tests

```bash
cd eternal-hr && ../mvnw test
```

The tests cover:
- **The process** on an in-memory engine, every path: approval, send back, reject, reminder, letter changes,
  document issues, escalation.
- **The joining form:** signed links and validation.
- **Email templates, workers,** and **form files** checked against the BPMN.
- **Application startup.**

## Deploy to AWS

The app goes onto the running demo server (`deploy/aws-demo`) as its own Compose project. The platform is not
redeployed. Use the `eternal-hr-deploy` Claude skill, or run:

```bash
bash eternal-hr/deploy/scripts/deploy-aws.sh
```

| Setting (env) | Purpose | Default |
| --- | --- | --- |
| `ETERNAL_HR_ENGINE_URL` / `_USERNAME` / `_PASSWORD` | Engine REST API and service account | `http://localhost:8080/engine-rest`, `demo` |
| `ETERNAL_HR_PUBLIC_URL` | Base URL of the joining form links | `http://localhost:8090` |
| `ETERNAL_HR_TASKLIST_URL` | Link in emails to people with tasks | `http://localhost:5173` |
| `ETERNAL_HR_LINK_SECRET` | Signs the joining form links (32+ characters) | development value |
| `ETERNAL_HR_FORM_REMINDER_CYCLE` / `ETERNAL_HR_DOCUMENT_ESCALATION_AFTER` | Reminder and escalation timing (ISO 8601) | `R3/P2D`, `P3D` |
| `ETERNAL_HR_SMTP_*`, `ETERNAL_HR_MAIL_FROM`, `ETERNAL_HR_HR_EMAIL` | Outgoing mail | `localhost:1025` (Mailpit) |
| `ETERNAL_HR_DEMO_USERS` / `_PASSWORD` | Create the role groups and one demo user per role | off |
| `ETERNAL_HR_DOCUMENTS_BUCKET` / `_REGION` / `_ENDPOINT` / `_ACCESS_KEY` / `_SECRET_KEY` | The platform's document bucket, for the employee's uploads | off |
