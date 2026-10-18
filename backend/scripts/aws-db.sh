#!/usr/bin/env bash
set -euo pipefail
YFC_BACKEND_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$YFC_BACKEND_DIR"
if [[ ! -f .env.aws ]]; then
  echo 'Configure backend/.env.aws first.' >&2
  exit 1
fi
set -a
source .env.aws
set +a
if [[ "${DB_AUTH_MODE:-}" != iam ]]; then
  echo 'This maintenance helper requires DB_AUTH_MODE=iam.' >&2
  exit 1
fi
YFC_APP_JAR="$YFC_BACKEND_DIR/target/yfc-backend-0.1.0-SNAPSHOT.jar"
if [[ ! -f "$YFC_APP_JAR" ]]; then
  echo 'Build the backend with mvn package from backend first.' >&2
  exit 1
fi
exec java -Dloader.main=com.yfctransport.database.AwsDatabaseTool -cp "$YFC_APP_JAR" org.springframework.boot.loader.launch.PropertiesLauncher "${1:-check}"
