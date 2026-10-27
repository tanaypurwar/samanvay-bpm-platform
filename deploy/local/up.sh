#!/usr/bin/env bash
# Runs the Samanvay platform locally in Docker: https://localhost:18443 (tasklist), /camunda/ (engine webapps).
#   SKIP_BUILD=1   reuse the last build of the tasklist jar and engine bundle
#   BRAND=samanvay use the Samanvay branding instead of Eternal HR
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
STACK="$HERE/../aws-demo"

if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  SKIP_TESTS=1 bash "$STACK/scripts/build.sh"
fi

# local secrets: generated once, kept in deploy/local/.env (not committed)
if [[ ! -f "$HERE/.env" ]]; then
  gen() { openssl rand -hex 12; }
  cat >"$HERE/.env" <<EOF
SAMANVAY_DOMAIN=localhost
SAMANVAY_ADMIN_USER=admin
SAMANVAY_ADMIN_PASSWORD=$(gen)
ENGINE_DB_PASSWORD=$(gen)
TASKLIST_DB_PASSWORD=$(gen)
EOF
  echo "==> Generated $HERE/.env"
fi
# process documents go to the local S3 stand-in (service "s3" in compose.override.yml)
if ! grep -q '^DOCUMENTS_BUCKET=' "$HERE/.env"; then
  cat >>"$HERE/.env" <<EOF
DOCUMENTS_BUCKET=samanvay-documents
DOCUMENTS_ENDPOINT=http://s3:9000
DOCUMENTS_ACCESS_KEY=samanvay-$(openssl rand -hex 4)
DOCUMENTS_SECRET_KEY=$(openssl rand -hex 16)
DOCUMENTS_CREATE_BUCKET=true
EOF
fi
if [[ "${BRAND:-eternal-hr}" == "eternal-hr" ]] && ! grep -q TASKLIST_BRAND_NAME "$HERE/.env"; then
  cat >>"$HERE/.env" <<'EOF'
TASKLIST_BRAND_NAME=Eternal HR
TASKLIST_BRAND_PRODUCT=Onboarding
TASKLIST_BRAND_PRIMARY_COLOR=#4527a0
TASKLIST_BRAND_SECONDARY_COLOR=#00897b
EOF
fi

docker compose -p samanvay-local --env-file "$HERE/.env" \
  -f "$STACK/docker-compose.yml" -f "$HERE/compose.override.yml" up -d --build --remove-orphans

echo "==> Waiting for the engine and tasklist"
for i in $(seq 1 60); do
  status=$(docker compose -p samanvay-local --env-file "$HERE/.env" -f "$STACK/docker-compose.yml" \
    -f "$HERE/compose.override.yml" ps --format '{{.Service}} {{.Health}}')
  grep -Eq 'starting|unhealthy' <<<"$status" || break
  sleep 5
done
echo "$status"
echo "==> https://localhost:18443  (admin password: grep ADMIN_PASSWORD $HERE/.env)"
echo "    documents: S3 console http://127.0.0.1:19001/rustfs/console/  (keys: grep DOCUMENTS_ $HERE/.env)"
