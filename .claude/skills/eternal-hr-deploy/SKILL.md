---
name: eternal-hr-deploy
description: Run the Eternal HR process application (joining & documentation workflow, external task workers, joining form) locally or deploy it to the Samanvay AWS demo server at dev-samanvay.ulesolutions.com, then verify it. Use when the user asks to run, deploy, redeploy, update or check Eternal HR.
---

# Eternal HR: run locally or deploy to AWS

The app is in `eternal-hr/`; read its `README.md` for the process and architecture. It runs as its own Docker
Compose project next to the Samanvay platform, on the platform's `samanvay` network, and adds its public routes
(`/joining/`, `/mail/`) to the platform's Caddy with `eternal-hr/deploy/eternal-hr.caddy`. Deploying it never
redeploys the platform.

Run commands with the Bash tool (Git Bash) from the repo root. `deploy-aws.sh` and the local first start take
several minutes, so run them with `run_in_background: true` and wait for the notification.

## Local (do this first for any change)

```bash
bash deploy/local/up.sh                         # platform at https://localhost:18443; SKIP_BUILD=1 to reuse builds
bash eternal-hr/deploy/scripts/up-local.sh      # Eternal HR + Mailpit; SKIP_BUILD=1 to reuse the jar
```

- `deploy/local/up.sh` needs Docker Desktop running. If `docker info` fails, start
  `C:\Program Files\Docker\Docker\Docker Desktop.exe` and wait until it responds.
- Passwords: `eternal-hr/deploy/.env` (`DEMO_PASSWORD` for tm.demo, rm.demo, ch.demo, pm.demo and hr.demo;
  `MAILPIT_PASSWORD` for user `mail`) and `deploy/local/.env` (platform `admin`).
- Test before any deploy: `cd eternal-hr && ../mvnw test` (the process on an in-memory engine, workers, forms, startup).

## AWS

1. **The server must exist.** `deploy/aws-demo/.state` must be there and
   `curl -s -o /dev/null -w '%{http_code}' https://dev-samanvay.ulesolutions.com/actuator/health` must return 200.
   If not, the demo is down: tell the user to run `/samanvay-demo-up` (it creates billable resources, so it is
   theirs to start).
2. **The platform must support process apps.** It needs the `samanvay` network, Caddy `apps/` import, tasklist
   start forms and branding, and the engine id pattern. `deploy-aws.sh` checks this and stops with instructions if
   the platform is older. Then redeploy the platform:
   `bash deploy/aws-demo/scripts/build.sh && bash deploy/aws-demo/scripts/deploy.sh` (in the background). This
   keeps the platform's data and passwords. Tell the user that it restarts the platform for a few minutes.
3. **Deploy:** `bash eternal-hr/deploy/scripts/deploy-aws.sh` (in the background). It builds and tests the jar,
   uploads it, and creates `~/eternal-hr/.env` on the server on first deploy. It brands the tasklist
   *Eternal HR Onboarding*, starts the containers, and installs the Caddy routes.
4. **Verify:**

```bash
source deploy/aws-demo/.state; B=https://$DOMAIN
curl -s $B/api/branding                                              # name "Eternal HR"
for p in /joining/not-a-token /mail/; do echo "$p -> $(curl -s -o /dev/null -w '%{http_code}' $B$p)"; done   # 404, 401
ssh -i "$KEY_FILE" ubuntu@$PUBLIC_IP 'cd eternal-hr && docker compose -p eternal-hr ps && docker compose -p eternal-hr logs --tail 30 eternal-hr | grep -E "Deployed|unchanged|Created demo|ERROR"'
```

Expected: branding is Eternal HR; 404 and 401; eternal-hr `healthy`; the log shows the processes deployed (or
unchanged) and no errors. For a full check, adapt the local end-to-end steps: start a joining as tm.demo through
`/api/processes/eternal-hr-joining/start`, approve as rm.demo, ch.demo and pm.demo, then find the link email in
Mailpit (`/mail/api/v1/messages`, basic auth `mail`).

5. **Report:** the URL, the demo users and the shared password, the mailbox at `/mail/` (user `mail`), and that
   Cockpit at `/camunda/` shows the process. Read passwords with
   `ssh -i "$KEY_FILE" ubuntu@$PUBLIC_IP grep -E 'DEMO_PASSWORD|MAILPIT_PASSWORD' eternal-hr/.env`.

## Operating on the server

- Logs: `ssh ... 'cd eternal-hr && docker compose -p eternal-hr logs -f eternal-hr'`
- A failed worker step becomes an incident in Cockpit (`/camunda/` → Cockpit → the process instance). Fix the
  cause, then retry it there.
- Remove Eternal HR only: `ssh ... 'cd eternal-hr && docker compose -p eternal-hr down && rm ~/samanvay/apps/eternal-hr.caddy && cd ~/samanvay && docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile'`.
  Running process instances stay in the engine.
- Memory: the t3.micro is tight with Eternal HR added (about 750 MB in use plus swap). It is fine for a demo; if
  pages get slow, check `free -m`.

## Gotchas

- Git Bash rewrites container paths such as `/etc/caddy/...` in local `docker` commands: prefix that one command
  with `MSYS_NO_PATHCONV=1`, never the whole script (that breaks host paths).
- Changing the BPMN or any form makes a new process version on the next start. Running instances keep their
  version and its forms.
- The BPMN diagram was generated with lanes per role. Edit it in Camunda Modeler; keep the form keys and topics
  as they are.
