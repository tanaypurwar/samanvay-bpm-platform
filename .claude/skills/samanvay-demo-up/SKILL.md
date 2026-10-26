---
name: samanvay-demo-up
description: Recreate the Samanvay demo on AWS from nothing (dev-samanvay.ulesolutions.com, free-tier EC2 with Docker) and hand back a working URL and admin login.
disable-model-invocation: true
---

# Recreate the Samanvay demo

The user typed `/samanvay-demo-up`, which is their go-ahead to create AWS resources (free tier: one `t3.micro`,
Elastic IP, security group, key pair, DNS record). Background, troubleshooting and teardown are in the
`samanvay-demo` skill and in `deploy/aws-demo/README.md`.

Run everything with the Bash tool (Git Bash) from `deploy/aws-demo/` in the repo root.

## 1. Preflight (stop at the first failure and tell the user)

```bash
cd deploy/aws-demo
aws sts get-caller-identity --query Arn --output text
ls .state 2>/dev/null && cat .state
ls ../../distro/run/distro/target/camunda-bpm-run-*.tar.gz
```

- **AWS session expired** ("session has expired" or similar): ask the user to run `! aws login`, then continue. Don't
  run `aws login` yourself; it's interactive.
- **`.state` exists:** a demo may already be running. Check it with
  `curl -s -o /dev/null -w '%{http_code}' https://dev-samanvay.ulesolutions.com/actuator/health`. If it returns 200,
  tell the user it's already up and stop. Otherwise continue: `provision.sh` reuses whatever still exists.
- **No engine tarball:** the branded engine has to be built first with `./mvnw install -DskipTests -pl distro/run/distro -am`
  from the repo root. It takes a long time, so ask the user before starting it.

## 2. Build, provision, deploy

```bash
SKIP_TESTS=1 bash scripts/build.sh     # foreground, a few minutes
bash scripts/provision.sh              # foreground, about 1 minute; writes .state
```

Then run `bash scripts/deploy.sh` with `run_in_background: true`. It uploads about 160 MB and waits for the first
start, which often takes more than 10 minutes. Wait for the completion notification; don't poll.

If `deploy.sh` fails with "REMOTE HOST IDENTIFICATION HAS CHANGED" (AWS reused an old IP), run
`ssh-keygen -R <PUBLIC_IP from .state>` and run deploy again. If SSH times out, re-run `provision.sh`, which adds the
current public IP to the security group.

## 3. Verify from the internet

```bash
source .state
B=https://$DOMAIN
for p in / /camunda/app/welcome/default/ /engine-rest/engine /actuator/health; do
  echo "$p -> $(curl -s -o /dev/null -w '%{http_code}' $B$p)"; done
curl -sv -o /dev/null $B/ 2>&1 | grep -E "issuer:"
PW=$(ssh -i "$KEY_FILE" ubuntu@$PUBLIC_IP 'grep ADMIN_PASSWORD samanvay/.env | cut -d= -f2')
echo "deploy api: $(curl -s -o /dev/null -w '%{http_code}' -u admin:$PW $B/engine-rest/version)"
J=$(mktemp); C="curl -s -b $J -c $J"; $C $B/api/auth/csrf -o /dev/null
X=$(awk '$6=="XSRF-TOKEN"{print $7}' $J | tail -1)
echo "login: $($C -o /dev/null -w '%{http_code}' -H "X-XSRF-TOKEN: $X" -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$PW\"}" $B/api/auth/login)"
```

Expected results:
- the four paths: `200`, `200`, `404`, `200`;
- issuer: Let's Encrypt;
- `deploy api: 200` and `login: 200`.

If the certificate isn't issued yet, DNS may still be propagating: wait a minute and check again. If something fails,
look at `ssh -i "$KEY_FILE" ubuntu@$PUBLIC_IP 'cd samanvay && docker compose ps && docker compose logs --tail 80 <service>'`.

## 4. Report to the user

Keep it short. Include:
- the URL `https://dev-samanvay.ulesolutions.com` and the engine webapps at `/camunda/`;
- the login: user `admin` and the new password (the user asked for it every time, so include it);
- the deploy endpoint for Camunda Modeler: `https://dev-samanvay.ulesolutions.com/engine-rest`, HTTP Basic;
- that this is a fresh server: new IP and password, and demo users and processes must be added again (the invoice
  example is preloaded);
- how to take it down again: ask Claude to destroy the demo (`samanvay-demo` skill), which costs nothing once done.
