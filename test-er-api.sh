#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: ./test-er-api.sh <nickname>"
  exit 1
fi

nickname="$1"

curl --get "http://localhost:8080/api/er/users/search" \
  --data-urlencode "nickname=${nickname}"
