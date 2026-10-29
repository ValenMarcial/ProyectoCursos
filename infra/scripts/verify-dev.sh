#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if [[ -f .env ]]; then
  set -a
  . ./.env
  set +a
fi

RUNTIME=false
if [[ "${1:-}" == "--runtime" ]]; then
  RUNTIME=true
fi

failures=0
pass() { printf 'OK   %s\n' "$1"; }
fail() { printf 'FAIL %s\n' "$1"; failures=$((failures + 1)); }
check() { if eval "$2"; then pass "$1"; else fail "$1"; fi; }
check_flowable_ui() {
  local url="http://127.0.0.1:8080/flowable-ui/"
  local deadline=$((SECONDS + 60))
  local response="sin respuesta"

  while (( SECONDS < deadline )); do
    response="$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 2 --max-time 5 "$url" 2>&1)" || true
    if [[ "$response" =~ ^(200|302|401)$ ]]; then
      pass "Flowable UI responde"
      return 0
    fi
    sleep 2
  done

  fail "Flowable UI responde (última respuesta: $response)"
}

check "existe .env" "test -f .env"
check ".env usa Institucion como base" "grep -q '^POSTGRES_DB=Institucion$' .env"
check ".env define POSTGRES_PASSWORD" "grep -q '^POSTGRES_PASSWORD=' .env"
check "existe esquema de institucion" "test -f infra/db/sql/institucion-schema.sql"
check "existe seed de desarrollo" "test -f infra/db/sql/seed.sql"
check "existe Makefile" "test -f Makefile"
check "existe target dev-up" "grep -q '^dev-up:' Makefile"

if ! docker compose --profile dev config >/dev/null 2>&1; then
  fail "docker compose config es válido"
else
  pass "docker compose config es válido"
  config="$(docker compose --profile dev config)"
  check "Postgres usa POSTGRES_PASSWORD" "grep -q 'POSTGRES_PASSWORD:' <<<\"\$config\""
  check "no hay container_name fijos" "! grep -q 'container_name' <<<\"\$config\""
  check "pgAdmin está bajo el profile dev" "grep -q 'dev' <<<\"\$config\""
fi

if $RUNTIME; then
  check "contenedor postgres corriendo" "docker compose --profile dev ps --status running --services | grep -qx postgres"
  check "contenedor flowable corriendo" "docker compose --profile dev ps --status running --services | grep -qx flowable"
  check "contenedor pgadmin corriendo" "docker compose --profile dev ps --status running --services | grep -qx pgadmin"
  check "base de datos principal existe" "docker compose exec -T postgres psql -U \"\${POSTGRES_USER}\" -d postgres -tAc \"SELECT 1 FROM pg_database WHERE datname = '\${POSTGRES_DB}'\" | grep -qx 1"
  check "Flowable inicializó su esquema" "docker compose exec -T postgres psql -U \"\${POSTGRES_USER}\" -d \"\${POSTGRES_DB}\" -tAc \"SELECT to_regclass('public.act_re_procdef')\" | grep -q act_re_procdef"
  check "esquema de institucion inicializado en POSTGRES_DB" "docker compose exec -T postgres psql -U \"\${POSTGRES_USER}\" -d \"\${POSTGRES_DB}\" -tAc \"SELECT to_regclass('public.schema_version')\" | grep -q schema_version"
  check_flowable_ui
fi

if (( failures > 0 )); then
  printf '\n%d verificación(es) fallaron.\n' "$failures"
  exit 1
fi

printf '\nTodas las verificaciones pasaron.\n'
