---
name: samanvay-demo
description: Bring up, redeploy, check or destroy the Samanvay demo on AWS (dev-samanvay.ulesolutions.com, single free-tier EC2 with Docker). Use when the user asks to deploy, start, recreate, update, check, stop or tear down the demo/dev environment.
---

# Samanvay demo on AWS

Everything lives in `deploy/aws-demo/` (read its `README.md` for the architecture). Run all commands from that
directory in the Bash tool (Git Bash).

| Fact | Value |
| --- | --- |
| URL | https://dev-samanvay.ulesolutions.com (engine webapps under `/camunda/`) |
| AWS region | `ap-south-1` (Mumbai); the account is whichever `aws login` signs into |
| DNS | Route 53 public zone `ulesolutions.com`, same account |
| Resources (all named/tagged `samanvay-demo`) | EC2 `t3.micro`, Elastic IP, security group, key pair, A record, IAM role + instance profile, S3 bucket `samanvay-demo-documents-<account>` |
| Documents | Uploaded files (tasklist and the Eternal HR joining form) go to that bucket through the instance role; no keys on the box |
| SSH key | `~/.ssh/samanvay-demo.pem`, user `ubuntu` |
| State file | `deploy/aws-demo/.state` (instance id, IP, key path); written by provision, needed by deploy/destroy |
| Stack on the box | `~/samanvay`: Docker Compose with caddy, engine (Samanvay Run), tasklist; H2 databases on volumes |
| Secrets | `~/samanvay/.env` on the server only; generated on first deploy. Admin user `admin` |

## Before anything

1. `aws sts get-caller-identity`. If it fails with "session has expired", stop and ask the user to run `! aws login`.
   Don't try to log in yourself; it's interactive.
2. Confirm with the user before **destroy** (it deletes all demo data) and before **creating** resources.

## Bring it up (from nothing)

```bash
cd deploy/aws-demo
SKIP_TESTS=1 bash scripts/build.sh   # drop SKIP_TESTS to also run the tasklist tests (slower)
bash scripts/provision.sh            # idempotent: reuses whatever already exists
bash scripts/deploy.sh               # run with run_in_background: true; it often takes longer than 10 minutes
```

- `build.sh` needs `distro/run/distro/target/camunda-bpm-run-*.tar.gz`. If it's missing, the engine distro must be
  built first (`./mvnw install -DskipTests -pl distro/run/distro -am` from the repo root, long). Ask before starting that.
- First start on the micro instance takes 3–5 minutes. Engine and tasklist report `(healthy)` when ready.
- If the IP is reused and SSH fails with "REMOTE HOST IDENTIFICATION HAS CHANGED", run `ssh-keygen -R <ip>` and retry.

## Redeploy code changes (instance already running)

```bash
cd deploy/aws-demo && SKIP_TESTS=1 bash scripts/build.sh && bash scripts/deploy.sh   # deploy in background
```

Data and passwords are kept. Only engine or webapp changes need a rebuilt engine distro.

## Destroy

```bash
cd deploy/aws-demo
echo dev-samanvay.ulesolutions.com | bash scripts/destroy.sh   # the script asks for the domain as confirmation
```

It deletes the DNS record, releases the Elastic IP, terminates the instance (all data and the `.env` passwords are
lost), and deletes the security group, key pair, instance role and the documents bucket with every stored document.
The local `.pem` is kept, and `provision.sh` overwrites it next time.

If `.state` is missing, clean up by hand: look for the resources named or tagged `samanvay-demo` in `ap-south-1`
(instance, Elastic IP, security group, key pair, IAM role and instance profile, documents bucket) and the
`dev-samanvay` A record in the `ulesolutions.com` zone.

If uploaded files don't show up: `DOCUMENTS_BUCKET` must be set in `~/samanvay/.env` and `~/eternal-hr/.env` (deploy
scripts copy it from `.state`), the instance needs the `samanvay-demo` instance profile, and its metadata hop limit must
be 2 so containers can reach the role's credentials. Re-running `provision.sh` fixes the last two.

After a destroy and re-create, the server gets a **new IP and a new admin password**, and demo users and processes
must be set up again. The example invoice process and its form are preloaded automatically.

## Deploy a BPMN/DMN to the running engine

```bash
curl -u admin:<password> -F deployment-name=<name> -F deploy-changed-only=true   -F data=@<file.bpmn> https://dev-samanvay.ulesolutions.com/engine-rest/deployment/create
```

Camunda Modeler works too: REST endpoint `https://dev-samanvay.ulesolutions.com/engine-rest`, HTTP Basic auth.

## Changing only the Caddyfile

Copy it with `scp` to `~/samanvay/Caddyfile`, then run
`docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile`. This has no downtime and needs no full deploy.

## Stop / start without destroying (saves the instance charge only)

```bash
source deploy/aws-demo/.state
aws --region "$REGION" ec2 stop-instances --instance-ids "$INSTANCE_ID"    # or start-instances
```

The Elastic IP keeps the DNS valid. Containers restart by themselves on boot (`restart: unless-stopped`).

## Verify (after up or redeploy)

```bash
B=https://dev-samanvay.ulesolutions.com
for p in / /camunda/app/welcome/default/ /engine-rest/engine /actuator/health; do
  echo "$p -> $(curl -s -o /dev/null -w '%{http_code}' $B$p)"; done
```

Expected: `200, 200, 404, 200`. A 404 on `/engine-rest/engine` is intended. Only deployment endpoints are public:
`POST /engine-rest/deployment/create`, `GET /engine-rest/deployment` and `GET /engine-rest/version`, all with Basic auth.
The rest of `/engine-rest` must stay internal (see the `Caddyfile`). Also do a sign-in through
`/api/auth/csrf` and then `/api/auth/login`. Read the password with
`ssh -i ~/.ssh/samanvay-demo.pem ubuntu@<ip> grep ADMIN_PASSWORD samanvay/.env` and don't print it in chat unless asked.

Status and logs on the box: `ssh -i ~/.ssh/samanvay-demo.pem ubuntu@<ip> 'cd samanvay && docker compose ps && docker compose logs --tail 50 engine'`.

## Gotchas

- In Git Bash, set `MSYS_NO_PATHCONV=1` for commands that pass paths starting with `/` into containers or AWS
  (the scripts already do).
- `aws.exe` on Windows prints CRLF. The scripts strip it; do the same in ad-hoc `$(aws ...)` captures.
- SSH is allowed only from the IP that last ran `provision.sh`. If SSH times out, re-run `provision.sh`; it adds the
  current IP.
- Rejecting the example invoice needs a comment of at least 10 characters. That 422 is form validation, not a bug.
