#!/usr/bin/env bash
set -euo pipefail
YFC_BACKEND_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$YFC_BACKEND_DIR"
set -a
source .env.aws
set +a
case "$DB_URL" in
  */yfc\?*)
    YFC_DB_BASE="${DB_URL%%\?*}"
    YFC_DB_BASE="${YFC_DB_BASE%/yfc}"
    YFC_DB_QUERY="${DB_URL#*\?}"
    export YFC_TEST_POSTGRES_URL="$YFC_DB_BASE/yfc_test?$YFC_DB_QUERY"
    ;;
  *) echo 'This helper requires DB_URL to name the yfc application database; tests must use yfc_test.' >&2; exit 1 ;;
esac
export YFC_TEST_DB_AUTH_MODE=iam
export YFC_TEST_POSTGRES_USER="$DB_USER"
export YFC_TEST_POSTGRES_PASSWORD=''
exec mvn -B "$@" '-Dtest=Postgres*JourneyTest' test
