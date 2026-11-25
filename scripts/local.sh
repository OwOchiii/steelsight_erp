#!/usr/bin/env bash
# Runs a local copy of Steelsight with Docker (see docs/local-run.md).
#
#   scripts/local.sh up      build and start the database and the ERP, wait until it answers
#   scripts/local.sh seed    load the sample people and work (once, after up)
#   scripts/local.sh logs    follow the ERP's log
#   scripts/local.sh psql    open psql on the local database
#   scripts/local.sh stop    stop both, keeping the data
#   scripts/local.sh reset   stop both and delete the data, to start again from nothing
set -euo pipefail
cd "$(dirname "$0")/.."

COMPOSE=(docker compose -f compose.local.yaml)
URL=http://localhost:8080

psql_local() {
    "${COMPOSE[@]}" exec -T db psql -v ON_ERROR_STOP=1 -U postgres -d steelsight_local "$@"
}

wait_for_app() {
    printf 'Waiting for the ERP to start'
    for _ in $(seq 1 90); do
        if curl -fs "$URL/actuator/health" 2>/dev/null | grep -q '"UP"'; then
            printf '\nThe ERP is up at %s\n' "$URL"
            return 0
        fi
        printf '.'
        sleep 2
    done
    printf '\nThe ERP did not start in 3 minutes. Look at: scripts/local.sh logs\n' >&2
    return 1
}

case "${1:-}" in
    up)
        "${COMPOSE[@]}" up -d --build
        wait_for_app
        echo "Sign in as ADMIN with the password ${LOCAL_ADMIN_PASSWORD:-Admin12345}. Load sample data with: scripts/local.sh seed"
        ;;
    seed)
        printf 'Waiting for the ADMIN account'
        for _ in $(seq 1 60); do
            if [ "$(psql_local -tAc "SELECT count(*) FROM app_user WHERE employee_code = 'ADMIN'" 2>/dev/null || echo 0)" = "1" ]; then
                printf '\n'
                psql_local < scripts/dev-sample-data.sql
                echo "Sample data loaded. Every sample account (HSE01, MGR01, WRK01 ...) uses ADMIN's password."
                exit 0
            fi
            printf '.'
            sleep 2
        done
        printf '\nNo ADMIN account yet. Start the app first: scripts/local.sh up\n' >&2
        exit 1
        ;;
    logs)  "${COMPOSE[@]}" logs -f app ;;
    psql)  "${COMPOSE[@]}" exec db psql -U postgres -d steelsight_local ;;
    stop)  "${COMPOSE[@]}" stop ;;
    reset) "${COMPOSE[@]}" down -v ;;
    *)
        sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
        exit 1
        ;;
esac
