# Samanvay demo on AWS (single EC2, free tier)

Runs the whole platform with Docker Compose on one small EC2 instance at **https://dev-samanvay.ulesolutions.com**.

```text
Internet ──443──▶ Caddy (automatic Let's Encrypt TLS)
                   ├─ /          ▶ Samanvay Tasklist :8081  (web app + /api for web and mobile)
                   ├─ /camunda/  ▶ Samanvay Run      :8080  (Cockpit, Admin, Tasklist webapps)
                   ├─ /engine-rest/deployment/create (POST), /engine-rest/deployment, /version (GET)
                   │              ▶ Samanvay Run  (deploying from Camunda Modeler or curl; Basic auth)
                   └─ rest of /engine-rest  → 404 (internal only: the tasklist backend calls it over the Docker network)
Databases: embedded H2 files on Docker volumes (engine-data, tasklist-data); no database server to run
```

| Piece | Choice | Why |
| --- | --- | --- |
| Instance | `t3.micro` (or `t2.micro`), whichever is free-tier eligible in the region | free tier |
| CPU credits | `standard` | `t3` defaults to `unlimited`, which bills for sustained CPU above baseline |
| Memory | 1 GiB RAM + 2 GiB swap, JVM heaps capped (engine 320 MB, tasklist 224 MB) | fits both JVMs |
| Database | H2 embedded in each app, file per app on a Docker volume | nothing extra to run or pay for |
| Disk | 20 GiB gp3, encrypted | free tier covers 30 GiB |
| OS | Ubuntu 24.04, Docker + Compose from apt | |
| Address | Elastic IP + Route 53 A record | DNS stays valid across stop/start |
| Builds | on your PC; only finished artifacts are copied | a 1 GiB box can't build Maven/npm projects |

## Prerequisites (on your PC)

- AWS CLI v2, logged in (`aws login` or `aws configure sso`) to the account that hosts the `ulesolutions.com` zone
- Java 17, Git Bash
- Samanvay Run built once: `./mvnw install -DskipTests -pl distro/run/distro -am` (long; already done if
  `distro/run/distro/target/camunda-bpm-run-*.tar.gz` exists). Rebuild it when you change the engine or webapps.

## Deploy

From `deploy/aws-demo` in Git Bash:

```bash
scripts/build.sh        # tasklist jar (with tests; SKIP_TESTS=1 to skip) + stage engine bundle
scripts/provision.sh    # key pair, security group, instance, Elastic IP, DNS. Safe to re-run
scripts/deploy.sh       # upload, generate secrets on first run, docker compose up
```

Region defaults to `ap-south-1` (Mumbai); override with `AWS_REGION=...`. Other overrides: `INSTANCE_TYPE`,
`SAMANVAY_DOMAIN`, `ZONE_NAME`, `KEY_FILE`, `VOLUME_GIB`.

The first start takes 3–5 minutes on a micro instance. The certificate is issued as soon as DNS points at the box.

To ship a new version, run `scripts/build.sh` and then `scripts/deploy.sh`. Data and passwords are kept.

## Signing in

The engine creates one admin user (`admin`, member of `camunda-admin`) with a random password, generated on the server
on the first deploy:

```bash
ssh -i ~/.ssh/samanvay-demo.pem ubuntu@<ip> grep ADMIN_PASSWORD samanvay/.env
```

- Tasklist: https://dev-samanvay.ulesolutions.com. The example *Invoice approval* process and its form are already deployed.
- Engine webapps: https://dev-samanvay.ulesolutions.com/camunda/. Use Admin to create the demo users and groups
  (e.g. group `accounting` for the invoice example).
- Mobile app: enter `https://dev-samanvay.ulesolutions.com` as the server address.

The tasklist calls the engine as `admin` too. That is fine for a demo, but use a dedicated service account in production.

## Deploying processes

Deployment is open over HTTPS and needs a user with deployment rights, e.g. `admin`. The rest of the engine REST API
stays internal.

- **Camunda Modeler:** *Deploy* → Camunda 7 → REST endpoint `https://dev-samanvay.ulesolutions.com/engine-rest`,
  authentication *HTTP Basic*.
- **curl:**

  ```bash
  curl -u admin:<password> -F deployment-name=my-process -F deploy-changed-only=true     -F data=@my-process.bpmn https://dev-samanvay.ulesolutions.com/engine-rest/deployment/create
  ```

For a user task with `camunda:formKey="samanvay:<key>"`, publish a form with that key in the tasklist under
*Admin → Forms*.

## Operating

```bash
ssh -i ~/.ssh/samanvay-demo.pem ubuntu@<ip>
cd samanvay
docker compose ps
docker compose logs -f tasklist          # or engine, caddy
docker compose restart engine
```

**Backup:** H2 files must not be copied while the apps write to them, so stop the apps briefly:

```bash
docker compose stop engine tasklist
sudo tar czf ~/samanvay-backup-$(date +%F).tgz -C /var/lib/docker/volumes samanvay_engine-data samanvay_tasklist-data
docker compose start engine tasklist
```

H2 is fine for a demo with a handful of users. For production, move to PostgreSQL (the tasklist's `prod` profile
and the engine both support it).

SSH is only open to the IP that ran `provision.sh`. If your IP changes, re-run `provision.sh`.

## Cost

Within the 12-month free tier, this costs nothing: 750 instance hours, 30 GiB EBS and 750 hours of public IPv4
per month. After that, or on an account without the classic free tier, expect about **US$ 12–13/month** in
`ap-south-1`: the instance about $8, the public IPv4 $3.65 and the disk about $1.80. Route 53 bills the hosted
zone ($0.50/month), which you already pay.

- **Stopping the instance** stops the instance charge. The Elastic IP and disk are still billed.
- **`scripts/destroy.sh`** removes everything (DNS record, IP, instance and all its data, security group, key pair).
