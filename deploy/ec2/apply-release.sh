#!/usr/bin/env bash
# ==============================================================================
# YFC Transport ERP — Atomic EC2 Release Script (invoked via AWS SSM)
#
# Usage:
#   sudo bash apply-release.sh <s3-bucket> <release-tag>
#
# What this script does:
#   1. Downloads yfc-backend.jar and frontend-dist.tar.gz from S3 into a temp dir.
#   2. Atomically activates the new backend JAR via /opt/yfc/current.jar symlink
#      and restarts yfc-backend.service.
#   3. Waits up to 90s for http://127.0.0.1:8080/actuator/health to report UP.
#      - If unhealthy: prints recent journalctl logs, automatically rolls back
#        to the previous JAR (if one existed), and exits non-zero.
#   4. Once backend is verified UP, stages and atomically syncs frontend/dist
#      into /var/www/yfc/ with proper Nginx read permissions.
#   5. Prunes old backend JARs in /opt/yfc/releases/, retaining the newest 5.
# ==============================================================================
set -euo pipefail

export PATH="/snap/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <s3-bucket> <release-tag>" >&2
  exit 1
fi

BUCKET="$1"
RELEASE_TAG="$2"

# Guard against unexpected characters in release tag / bucket name
if [[ ! "$BUCKET" =~ ^[a-z0-9.-]+$ ]]; then
  echo "Invalid S3 bucket name: $BUCKET" >&2
  exit 1
fi
if [[ ! "$RELEASE_TAG" =~ ^[a-zA-Z0-9._-]+$ ]]; then
  echo "Invalid release tag: $RELEASE_TAG" >&2
  exit 1
fi

if ! command -v aws >/dev/null 2>&1; then
  echo "ERROR: AWS CLI ('aws') is not installed on EC2. Install it with: sudo snap install aws-cli --classic" >&2
  exit 1
fi

WORK_DIR="$(mktemp -d "/tmp/yfc-deploy-${RELEASE_TAG}.XXXXXX")"
cleanup() {
  rm -rf "$WORK_DIR"
  rm -f "/tmp/apply-release-${RELEASE_TAG}.sh"
}
trap cleanup EXIT

S3_PREFIX="s3://${BUCKET}/deployments/${RELEASE_TAG}"
NEW_JAR="/opt/yfc/releases/yfc-backend-${RELEASE_TAG}.jar"

echo "==> [1/5] Downloading release '${RELEASE_TAG}' from ${S3_PREFIX}..."
aws s3 cp "${S3_PREFIX}/yfc-backend.jar" "${WORK_DIR}/yfc-backend.jar" --only-show-errors
aws s3 cp "${S3_PREFIX}/frontend-dist.tar.gz" "${WORK_DIR}/frontend-dist.tar.gz" --only-show-errors

# Verify both downloads are non-empty before touching the live server
if [ ! -s "${WORK_DIR}/yfc-backend.jar" ] || [ ! -s "${WORK_DIR}/frontend-dist.tar.gz" ]; then
  echo "ERROR: Downloaded artifact is empty." >&2
  exit 1
fi

# Remember the previously active JAR so we can auto-rollback if health check fails
PREV_JAR=""
if [ -L /opt/yfc/current.jar ]; then
  PREV_JAR="$(readlink -f /opt/yfc/current.jar || true)"
fi

echo "==> [2/5] Installing backend JAR to ${NEW_JAR}..."
mkdir -p /opt/yfc/releases
install -o yfc -g yfc -m 500 "${WORK_DIR}/yfc-backend.jar" "${NEW_JAR}"
ln -sfn "${NEW_JAR}" /opt/yfc/current.jar

echo "==> [3/5] Restarting yfc-backend.service..."
systemctl restart yfc-backend

echo "==> [4/5] Waiting for backend health check (http://127.0.0.1:8080/actuator/health)..."
HEALTHY=0
for attempt in $(seq 1 45); do
  if curl -fsS --max-time 3 http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    HEALTHY=1
    echo "    Backend reported UP on attempt ${attempt}."
    break
  fi
  sleep 2
done

if [ "$HEALTHY" -ne 1 ]; then
  echo "ERROR: Backend failed to become healthy within 90 seconds!" >&2
  echo "--- Last 80 lines of yfc-backend logs ---" >&2
  journalctl -u yfc-backend -n 80 --no-pager >&2 || true

  if [ -n "$PREV_JAR" ] && [ -f "$PREV_JAR" ] && [ "$PREV_JAR" != "$NEW_JAR" ]; then
    echo "--- Automatically rolling back /opt/yfc/current.jar to ${PREV_JAR} ---" >&2
    ln -sfn "$PREV_JAR" /opt/yfc/current.jar
    systemctl restart yfc-backend || true
    rm -f "${NEW_JAR}"
  fi
  exit 1
fi

echo "==> [5/5] Deploying frontend static bundle to /var/www/yfc/..."
mkdir -p "${WORK_DIR}/frontend-dist" /var/www/yfc
tar -xzf "${WORK_DIR}/frontend-dist.tar.gz" -C "${WORK_DIR}/frontend-dist"

if [ ! -f "${WORK_DIR}/frontend-dist/index.html" ]; then
  echo "ERROR: Extracted frontend archive does not contain index.html!" >&2
  exit 1
fi

rsync -a --delete "${WORK_DIR}/frontend-dist/" /var/www/yfc/
chown -R ubuntu:www-data /var/www/yfc
chmod -R u=rwX,g=rX,o=rX /var/www/yfc

# Retain the 5 most recent backend JARs for quick manual rollback; prune older ones
ls -1t /opt/yfc/releases/yfc-backend-*.jar | tail -n +6 | xargs -r rm -f

echo "==> Deployment '${RELEASE_TAG}' succeeded! Backend UP and frontend synced."
