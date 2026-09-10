#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker build -f backstage/Dockerfile --target validation -t bookrush/portal-validation:local .
docker build -f backstage/Dockerfile --target runtime -t bookrush/portal:local .
