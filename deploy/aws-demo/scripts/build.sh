#!/usr/bin/env bash
# Builds the artifacts on this machine and stages them for the Docker images:
#   tasklist/dist/app.jar                  Samanvay Tasklist (backend + React app)
#   engine/dist/samanvay-run.tar.gz        Samanvay Run from distro/run (built separately, see README)
#   engine/dist/resources/*.bpmn           processes deployed on engine startup
#
# SKIP_TESTS=1 skips the tasklist tests.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"

echo "==> Building Samanvay Tasklist"
mvn_args=(-q -f "$REPO/samanvay-tasklist/pom.xml" clean package)
[[ "${SKIP_TESTS:-0}" == "1" ]] && mvn_args+=(-DskipTests)
(cd "$REPO/samanvay-tasklist" && ../mvnw "${mvn_args[@]}")
mkdir -p "$HERE/tasklist/dist"
cp "$REPO"/samanvay-tasklist/target/samanvay-tasklist-*.jar "$HERE/tasklist/dist/app.jar"

echo "==> Staging Samanvay Run"
run_tar=$(ls -t "$REPO"/distro/run/distro/target/camunda-bpm-run-*.tar.gz 2>/dev/null | head -1 || true)
if [[ -z "$run_tar" ]]; then
  echo "Samanvay Run is not built. Build it first (takes a while):" >&2
  echo "  ./mvnw install -DskipTests -pl distro/run/distro -am" >&2
  exit 1
fi
echo "    using $run_tar ($(date -r "$run_tar" '+%Y-%m-%d %H:%M'))"
mkdir -p "$HERE/engine/dist/resources"
cp "$run_tar" "$HERE/engine/dist/samanvay-run.tar.gz"

cp "$REPO/samanvay-tasklist/examples/invoice-approval.bpmn" "$HERE/engine/dist/resources/"

echo "==> Done"
du -sh "$HERE/tasklist/dist/app.jar" "$HERE/engine/dist/samanvay-run.tar.gz"
