#!/usr/bin/env bash
set -euo pipefail

if [ ! -f ".env" ]; then
  echo ".env file not found. Create .env with ER_API_KEY first."
  exit 1
fi

set -a
source .env
set +a

if [ -z "${ER_API_KEY:-}" ]; then
  echo "ER_API_KEY is empty. Check your .env file."
  exit 1
fi

./gradlew bootRun
