#!/usr/bin/env bash
set -euo pipefail

base_url="${ERON_BASE_URL:-http://localhost:8080}"

usage() {
  cat <<EOF
Usage:
  ./test-er-api.sh search <nickname>
  ./test-er-api.sh overview <nickname> [seasonId] [matchingTeamMode]
  ./test-er-api.sh games <gameId>
  ./test-er-api.sh data <metaType>

Shortcuts:
  ./test-er-api.sh <nickname>

Defaults:
  seasonId=39
  matchingTeamMode=3
  ERON_BASE_URL=${base_url}
EOF
}

if [ "$#" -lt 1 ]; then
  usage
  exit 1
fi

command="$1"
shift

case "${command}" in
  search)
    if [ "$#" -ne 1 ]; then
      usage
      exit 1
    fi

    curl --get "${base_url}/api/er/users/search" \
      --data-urlencode "nickname=$1"
    ;;
  overview)
    if [ "$#" -lt 1 ] || [ "$#" -gt 3 ]; then
      usage
      exit 1
    fi

    nickname="$1"
    season_id="${2:-39}"
    matching_team_mode="${3:-3}"

    curl --get "${base_url}/api/er/users/overview" \
      --data-urlencode "nickname=${nickname}" \
      --data-urlencode "seasonId=${season_id}" \
      --data-urlencode "matchingTeamMode=${matching_team_mode}"
    ;;
  games)
    if [ "$#" -ne 1 ]; then
      usage
      exit 1
    fi

    curl "${base_url}/api/er/games/$1"
    ;;
  data)
    if [ "$#" -ne 1 ]; then
      usage
      exit 1
    fi

    curl "${base_url}/api/er/data/$1"
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    if [ "$#" -eq 0 ]; then
      curl --get "${base_url}/api/er/users/search" \
        --data-urlencode "nickname=${command}"
    else
      usage
      exit 1
    fi
    ;;
esac
