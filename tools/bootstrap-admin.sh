#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "Usage: $0 <email> <password> <name> [--test-user <email> <password> <name>] [--container <name>]"
  exit 1
fi

CONTAINER_NAME="lh-assist-backend"
ADMIN_EMAIL=""
ADMIN_PASSWORD=""
ADMIN_NAME=""
TEST_USER_EMAIL=""
TEST_USER_PASSWORD=""
TEST_USER_NAME=""
TEST_USER_ENABLED="false"

ADMIN_EMAIL="$1"
ADMIN_PASSWORD="$2"
ADMIN_NAME="$3"
shift 3

while [[ $# -gt 0 ]]; do
  case "$1" in
    --test-user)
      TEST_USER_ENABLED="true"
      TEST_USER_EMAIL="${2:-}"
      TEST_USER_PASSWORD="${3:-}"
      TEST_USER_NAME="${4:-}"
      shift 4
      ;;
    --container)
      CONTAINER_NAME="${2:-lh-assist-backend}"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

if [[ "$TEST_USER_ENABLED" == "true" ]]; then
  if [[ -z "$TEST_USER_EMAIL" || -z "$TEST_USER_PASSWORD" || -z "$TEST_USER_NAME" ]]; then
    echo "Error: --test-user requires <email> <password> <name>"
    exit 1
  fi
fi

docker exec -it "$CONTAINER_NAME" java -jar /app.jar \
  --spring.main.web-application-type=none \
  --app.admin.bootstrap.cli.enabled=true \
  --app.admin.bootstrap.email="$ADMIN_EMAIL" \
  --app.admin.bootstrap.password="$ADMIN_PASSWORD" \
  --app.admin.bootstrap.name="$ADMIN_NAME" \
  --app.admin.bootstrap.test-user.enabled="$TEST_USER_ENABLED" \
  --app.admin.bootstrap.test-user.email="$TEST_USER_EMAIL" \
  --app.admin.bootstrap.test-user.password="$TEST_USER_PASSWORD" \
  --app.admin.bootstrap.test-user.name="$TEST_USER_NAME"
