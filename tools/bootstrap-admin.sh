#!/usr/bin/env bash
set -euo pipefail

if [[ $# -gt 1 ]]; then
  echo "Usage: $0 [--container <name>]"
  echo "Required env vars:"
  echo "  APP_ADMIN_BOOTSTRAP_EMAIL"
  echo "  APP_ADMIN_BOOTSTRAP_PASSWORD"
  echo "  APP_ADMIN_BOOTSTRAP_NAME"
  echo "Optional env vars for test user:"
  echo "  APP_ADMIN_BOOTSTRAP_TEST_USER_ENABLED (true/false)"
  echo "  APP_ADMIN_BOOTSTRAP_TEST_USER_EMAIL"
  echo "  APP_ADMIN_BOOTSTRAP_TEST_USER_PASSWORD"
  echo "  APP_ADMIN_BOOTSTRAP_TEST_USER_NAME"
  exit 1
fi

CONTAINER_NAME="lh-assist-backend"
if [[ $# -eq 1 ]]; then
  if [[ "$1" == "--container" ]]; then
    echo "Usage: $0 [--container <name>]"
    exit 1
  fi
fi
if [[ $# -eq 2 ]]; then
  if [[ "$1" != "--container" ]]; then
    echo "Usage: $0 [--container <name>]"
    exit 1
  fi
  CONTAINER_NAME="$2"
fi

if [[ -z "${APP_ADMIN_BOOTSTRAP_EMAIL:-}" || -z "${APP_ADMIN_BOOTSTRAP_PASSWORD:-}" || -z "${APP_ADMIN_BOOTSTRAP_NAME:-}" ]]; then
  echo "Error: APP_ADMIN_BOOTSTRAP_EMAIL/PASSWORD/NAME must be set"
  exit 1
fi

docker exec -i \
  -e APP_ADMIN_BOOTSTRAP_EMAIL \
  -e APP_ADMIN_BOOTSTRAP_PASSWORD \
  -e APP_ADMIN_BOOTSTRAP_NAME \
  -e APP_ADMIN_BOOTSTRAP_TEST_USER_ENABLED \
  -e APP_ADMIN_BOOTSTRAP_TEST_USER_EMAIL \
  -e APP_ADMIN_BOOTSTRAP_TEST_USER_PASSWORD \
  -e APP_ADMIN_BOOTSTRAP_TEST_USER_NAME \
  "$CONTAINER_NAME" java -jar /app.jar \
  --spring.main.web-application-type=none \
  --app.admin.bootstrap.cli.enabled=true
