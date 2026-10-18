#!/usr/bin/env bash
set -euo pipefail
YFC_BACKEND_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
YFC_ENV_FILE="${1:-$YFC_BACKEND_DIR/.env}"
if [[ "$YFC_ENV_FILE" != /* ]]; then
  YFC_ENV_FILE="$PWD/$YFC_ENV_FILE"
fi
cd "$YFC_BACKEND_DIR"
if [[ ! -f "$YFC_ENV_FILE" ]]; then
  echo 'Missing environment file. Copy .env.example and fill your database and initial admin settings.' >&2
  exit 1
fi
set -a
source "$YFC_ENV_FILE"
set +a
case "${DB_AUTH_MODE:-password}" in
  password) : "${DB_PASSWORD:?Set DB_PASSWORD in the selected environment file for password authentication}" ;;
  iam) : "${AWS_REGION:?Set AWS_REGION for IAM database authentication}" ;;
  *) echo 'DB_AUTH_MODE must be password or iam.' >&2; exit 1 ;;
esac
command -v java >/dev/null || { echo 'Java 21+ is required.' >&2; exit 1; }
command -v mvn >/dev/null || { echo 'Maven 3.9+ is required.' >&2; exit 1; }
exec mvn spring-boot:run
