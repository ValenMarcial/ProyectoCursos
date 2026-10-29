#!/usr/bin/env bash
set -euo pipefail

if [[ "${LOAD_SEED_DATA:-false}" != "true" ]]; then
  printf 'Seed omitido (LOAD_SEED_DATA=%s)\n' "${LOAD_SEED_DATA:-false}"
  exit 0
fi

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -f /db-sql/seed.sql
