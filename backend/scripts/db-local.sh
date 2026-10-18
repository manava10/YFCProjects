#!/usr/bin/env bash
set -euo pipefail
YFC_BACKEND_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$YFC_BACKEND_DIR"
if [[ ! -f .env ]]; then
  echo 'Missing backend/.env. Copy .env.example and set DB_PASSWORD first.' >&2
  exit 1
fi
command -v docker >/dev/null || {
  echo 'Docker is not installed. Install/start Docker Desktop, or use an existing PostgreSQL server and update DB_URL, DB_USER and DB_PASSWORD in backend/.env.' >&2
  exit 1
}
exec docker compose --env-file .env up -d --wait postgres
