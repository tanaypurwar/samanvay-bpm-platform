#!/usr/bin/env bash
# Runs Eternal HR next to the local platform (deploy/local/up.sh must be running).
#   https://localhost:18443          tasklist (sign in as tm.demo, rm.demo, ch.demo, pm.demo, hr.demo)
#   https://localhost:18443/mail/    every email sent (user "mail")
# SKIP_BUILD=1 reuses the last jar.
set -euo pipefail

DEPLOY="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$DEPLOY/../.." && pwd)"
PLATFORM_ENV="$REPO/deploy/local/.env"
APPS_DIR="$REPO/deploy/aws-demo/apps"

[[ -f "$PLATFORM_ENV" ]] || { echo "Start the platform first: deploy/local/up.sh" >&2; exit 1; }
[[ "${SKIP_BUILD:-0}" == "1" ]] || SKIP_TESTS=1 bash "$DEPLOY/scripts/build.sh"

# settings for this machine: generated once, kept in eternal-hr/deploy/.env (not committed)
if [[ ! -f "$DEPLOY/.env" ]]; then
  cat >"$DEPLOY/.env" <<EOF
ENGINE_USERNAME=$(grep '^SAMANVAY_ADMIN_USER=' "$PLATFORM_ENV" | cut -d= -f2-)
ENGINE_PASSWORD=$(grep '^SAMANVAY_ADMIN_PASSWORD=' "$PLATFORM_ENV" | cut -d= -f2-)
PUBLIC_URL=https://localhost:18443
LINK_SECRET=$(openssl rand -hex 32)
DEMO_USERS=true
DEMO_PASSWORD=$(openssl rand -hex 6)
MAILPIT_PASSWORD=$(openssl rand -hex 6)
EOF
  echo "==> Generated $DEPLOY/.env"
fi
# employee documents go to the platform's document bucket (its local S3 stand-in)
if ! grep -q '^DOCUMENTS_BUCKET=' "$DEPLOY/.env"; then
  grep -E '^DOCUMENTS_(BUCKET|ENDPOINT|ACCESS_KEY|SECRET_KEY|CREATE_BUCKET)=' "$PLATFORM_ENV" >>"$DEPLOY/.env" || true
fi

docker compose -p eternal-hr --env-file "$DEPLOY/.env" -f "$DEPLOY/docker-compose.yml" up -d --build --remove-orphans

# public routes: add the snippet to the platform's Caddy and reload it
cp "$DEPLOY/eternal-hr.caddy" "$APPS_DIR/eternal-hr.caddy"
# MSYS_NO_PATHCONV: Git Bash must not rewrite the container path
MSYS_NO_PATHCONV=1 docker compose -p samanvay-local exec -T caddy caddy reload --config /etc/caddy/Caddyfile >/dev/null

echo "==> Waiting for Eternal HR"
for i in $(seq 1 40); do
  health=$(docker inspect -f '{{.State.Health.Status}}' "$(docker compose -p eternal-hr -f "$DEPLOY/docker-compose.yml" ps -q eternal-hr)")
  [[ "$health" == "healthy" ]] && break
  sleep 5
done
echo "    eternal-hr: $health"
# shellcheck disable=SC1090
source "$DEPLOY/.env"
echo "==> https://localhost:18443   users tm.demo rm.demo ch.demo pm.demo hr.demo, password $DEMO_PASSWORD"
echo "    https://localhost:18443/mail/   user mail, password $MAILPIT_PASSWORD"
