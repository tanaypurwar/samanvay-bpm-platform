#!/usr/bin/env bash
# Copies the stack to the instance and (re)starts it. Run build.sh first.
# On the first deploy it generates ~/samanvay/.env on the server with random passwords; they never leave the server.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
[[ -f "$HERE/.state" ]] || { echo "Run scripts/provision.sh first." >&2; exit 1; }
# shellcheck disable=SC1091
source "$HERE/.state"
for f in tasklist/dist/app.jar engine/dist/samanvay-run.tar.gz; do
  [[ -f "$HERE/$f" ]] || { echo "Missing $f. Run scripts/build.sh first." >&2; exit 1; }
done

ssh_cmd=(ssh -i "$KEY_FILE" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 "ubuntu@$PUBLIC_IP")

echo "==> Waiting for $PUBLIC_IP to finish first-boot setup"
for i in $(seq 1 30); do
  "${ssh_cmd[@]}" true 2>/dev/null && break
  [[ $i == 30 ]] && { echo "SSH to $PUBLIC_IP failed. Is your current IP allowed? Re-run provision.sh." >&2; exit 1; }
  sleep 10
done
"${ssh_cmd[@]}" 'cloud-init status --wait >/dev/null; command -v docker >/dev/null'

echo "==> Uploading"
tar -C "$HERE" -czf - --exclude=./.env --exclude=./.state --exclude=./scripts --exclude=./apps/*.caddy . \
  | "${ssh_cmd[@]}" 'mkdir -p ~/samanvay && tar xzf - -C ~/samanvay'

echo "==> Starting"
"${ssh_cmd[@]}" "DOMAIN='$DOMAIN' DOCUMENTS_BUCKET='${DOCUMENTS_BUCKET:-}' REGION='$REGION' bash -s" <<'REMOTE'
set -euo pipefail
cd ~/samanvay
# files may have been checked out with Windows line endings
find . -maxdepth 2 -type f \( -name '*.sh' -o -name '*.yml' -o -name Caddyfile \) -exec sed -i 's/\r$//' {} +

if [ ! -f .env ]; then
  umask 077
  cat > .env <<EOF
SAMANVAY_DOMAIN=$DOMAIN
SAMANVAY_ADMIN_USER=admin
SAMANVAY_ADMIN_PASSWORD=$(openssl rand -hex 16)
ENGINE_DB_PASSWORD=$(openssl rand -hex 16)
TASKLIST_DB_PASSWORD=$(openssl rand -hex 16)
EOF
  echo "    generated ~/samanvay/.env"
fi
# documents bucket from provision.sh (the instance role grants access); kept in sync on every deploy
if [ -n "$DOCUMENTS_BUCKET" ]; then
  sed -i -e '/^DOCUMENTS_BUCKET=/d' -e '/^DOCUMENTS_REGION=/d' .env
  printf 'DOCUMENTS_BUCKET=%s\nDOCUMENTS_REGION=%s\n' "$DOCUMENTS_BUCKET" "$REGION" >> .env
fi

docker compose up -d --build --remove-orphans
docker image prune -f >/dev/null

echo "    waiting for the applications to become healthy (first start takes a few minutes)"
for i in $(seq 1 60); do
  unhealthy=$(docker compose ps --format '{{.Service}} {{.Health}}' | grep -Ev ' (healthy)?$' || true)
  [ -z "$unhealthy" ] && break
  sleep 10
done
docker compose ps
REMOTE

echo "==> https://$DOMAIN  (engine webapps: https://$DOMAIN/camunda/)"
echo "    Admin password: ${ssh_cmd[*]} grep ADMIN_PASSWORD samanvay/.env"
