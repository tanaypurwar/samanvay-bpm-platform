#!/usr/bin/env bash
# Deploys Eternal HR onto the running Samanvay AWS demo server (created by deploy/aws-demo/scripts/provision.sh),
# as its own Compose project next to the platform. Re-run to ship a new version; settings and mail are kept.
#   SKIP_BUILD=1  reuse the last jar in deploy/dist/
set -euo pipefail

DEPLOY="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$DEPLOY/../.." && pwd)"
STATE="$REPO/deploy/aws-demo/.state"

[[ -f "$STATE" ]] || { echo "No platform server: $STATE is missing. Bring the demo up first (/samanvay-demo-up)." >&2; exit 1; }
# shellcheck disable=SC1090
source "$STATE"
ssh_cmd=(ssh -i "$KEY_FILE" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 "ubuntu@$PUBLIC_IP")

echo "==> Checking the platform on $PUBLIC_IP"
if ! "${ssh_cmd[@]}" 'docker network inspect samanvay >/dev/null 2>&1 && grep -q "import /etc/caddy/apps" ~/samanvay/Caddyfile'; then
  echo "The platform on $PUBLIC_IP predates process-application support (network 'samanvay', Caddy apps/)." >&2
  echo "Redeploy it first: bash deploy/aws-demo/scripts/build.sh && bash deploy/aws-demo/scripts/deploy.sh" >&2
  exit 1
fi

[[ "${SKIP_BUILD:-0}" == "1" ]] || bash "$DEPLOY/scripts/build.sh"
[[ -f "$DEPLOY/dist/eternal-hr.jar" ]] || { echo "Missing dist/eternal-hr.jar; run scripts/build.sh" >&2; exit 1; }

echo "==> Uploading"
tar -C "$DEPLOY" -czf - Dockerfile docker-compose.yml eternal-hr.caddy dist \
  | "${ssh_cmd[@]}" 'mkdir -p ~/eternal-hr && tar xzf - -C ~/eternal-hr'

echo "==> Starting"
"${ssh_cmd[@]}" "DOMAIN='$DOMAIN' bash -s" <<'REMOTE'
set -euo pipefail
cd ~/eternal-hr
find . -maxdepth 1 -type f \( -name '*.yml' -o -name '*.caddy' -o -name Dockerfile \) -exec sed -i 's/\r$//' {} +
platform_env=~/samanvay/.env
get() { grep "^$1=" "$platform_env" | cut -d= -f2-; }

if [ ! -f .env ]; then
  umask 077
  cat > .env <<EOF
ENGINE_USERNAME=$(get SAMANVAY_ADMIN_USER)
ENGINE_PASSWORD=$(get SAMANVAY_ADMIN_PASSWORD)
PUBLIC_URL=https://$DOMAIN
LINK_SECRET=$(openssl rand -hex 32)
DEMO_USERS=true
DEMO_PASSWORD=$(openssl rand -hex 6)
MAILPIT_PASSWORD=$(openssl rand -hex 6)
EOF
  echo "    generated ~/eternal-hr/.env"
fi
# employee documents go to the platform's document bucket (the instance role grants access); empty: uploads off
# (copied on every deploy, so a bucket added to the platform later is picked up)
sed -i -e '/^DOCUMENTS_BUCKET=/d' -e '/^DOCUMENTS_REGION=/d' .env
echo "DOCUMENTS_BUCKET=$(get DOCUMENTS_BUCKET || true)" >> .env
echo "DOCUMENTS_REGION=$(get DOCUMENTS_REGION || true)" >> .env

# brand the shared tasklist as Eternal HR (once), then apply it. The logo is served by this app at /joining/assets/.
brand_before=$(grep '^TASKLIST_BRAND_' "$platform_env" || true)
if ! grep -q '^TASKLIST_BRAND_NAME=' "$platform_env"; then
  cat >> "$platform_env" <<'EOF'
TASKLIST_BRAND_NAME=Eternal HR
TASKLIST_BRAND_PRODUCT=Onboarding
TASKLIST_BRAND_PRIMARY_COLOR=#9b211c
TASKLIST_BRAND_SECONDARY_COLOR=#68692d
EOF
fi
# servers branded before the logo: move from the old purple to the logo's colors and add the logo
sed -i -e 's/^TASKLIST_BRAND_PRIMARY_COLOR=#4527a0$/TASKLIST_BRAND_PRIMARY_COLOR=#9b211c/' \
  -e 's/^TASKLIST_BRAND_SECONDARY_COLOR=#00897b$/TASKLIST_BRAND_SECONDARY_COLOR=#68692d/' "$platform_env"
grep -q '^TASKLIST_BRAND_LOGO_URL=' "$platform_env" ||
  echo 'TASKLIST_BRAND_LOGO_URL=/joining/assets/logo.webp' >> "$platform_env"
if [ "$brand_before" != "$(grep '^TASKLIST_BRAND_' "$platform_env")" ]; then
  (cd ~/samanvay && docker compose up -d tasklist)
  echo "    tasklist branded as Eternal HR"
fi

docker compose -p eternal-hr up -d --build --remove-orphans
docker image prune -f >/dev/null

# public routes (/joining/, /mail/) on the platform's Caddy
cp eternal-hr.caddy ~/samanvay/apps/eternal-hr.caddy
(cd ~/samanvay && docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile >/dev/null)

echo "    waiting for Eternal HR (first start takes a few minutes on a micro instance)"
for i in $(seq 1 60); do
  health=$(docker inspect -f '{{.State.Health.Status}}' "$(docker compose -p eternal-hr ps -q eternal-hr)")
  [ "$health" = healthy ] && break
  sleep 10
done
docker compose -p eternal-hr ps --format '{{.Service}} {{.Status}}'
REMOTE

echo "==> https://$DOMAIN             tasklist: tm.demo rm.demo ch.demo pm.demo hr.demo"
echo "    https://$DOMAIN/mail/       demo mailbox (user mail)"
echo "    Passwords: ${ssh_cmd[*]} grep -E 'DEMO_PASSWORD|MAILPIT_PASSWORD' eternal-hr/.env"
