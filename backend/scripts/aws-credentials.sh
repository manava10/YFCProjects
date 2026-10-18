#!/usr/bin/env bash
# Credential-process output is consumed privately by the AWS SDK. Do not run this to display credentials.
set -euo pipefail
exec env -u AWS_CONFIG_FILE -u AWS_PROFILE aws configure export-credentials --profile "${YFC_AWS_LOGIN_PROFILE:-default}" --format process
