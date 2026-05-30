#!/usr/bin/env bash
# Brings up the infra, loads secrets, starts the app. Written mostly because I
# kept forgetting the `set -a` around sourcing .env and then wondering why
# clipping was silently off.
set -euo pipefail

cd "$(dirname "$0")"

echo "==> starting redis / postgres / kafka"
docker compose up -d

echo "==> waiting for kafka to accept connections"
for _ in $(seq 1 30); do
  if nc -z localhost 9092 2>/dev/null; then break; fi
  sleep 1
done

if [ -f .env ]; then
  echo "==> loading .env"
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
else
  echo "==> no .env found, running in alert-only mode (see .env.example)"
fi

exec ./gradlew bootRun "$@"
