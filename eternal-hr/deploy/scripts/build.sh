#!/usr/bin/env bash
# Builds the Eternal HR jar on this machine and stages it in deploy/dist/ for the Docker image.
# SKIP_TESTS=1 skips the tests.
set -euo pipefail

DEPLOY="$(cd "$(dirname "$0")/.." && pwd)"
APP="$(cd "$DEPLOY/.." && pwd)"

args=(-q -f "$APP/pom.xml" clean package)
[[ "${SKIP_TESTS:-0}" == "1" ]] && args+=(-DskipTests)
(cd "$APP" && ../mvnw "${args[@]}")
mkdir -p "$DEPLOY/dist"
cp "$APP/target/eternal-hr.jar" "$DEPLOY/dist/eternal-hr.jar"
echo "==> $(du -h "$DEPLOY/dist/eternal-hr.jar" | cut -f1) $DEPLOY/dist/eternal-hr.jar"
