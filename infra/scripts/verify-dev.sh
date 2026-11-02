#!/usr/bin/env bash
set -euo pipefail

# Buscamos la raiz del proyecto
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

# Revisamos si hay un arrchivo .env creado y lo cargamos
if [[ -f .env ]]; then
  set -a # exporta las variables
  . ./.env
  set +a
fi

# Checkea si los contenedores estan ejecutandose
RUNTIME=false
if [[ "${1:-}" == "--runtime" ]]; then
  RUNTIME=true
fi

# Funciones auxiliares
failures=0
pass() { printf 'OK   %s\n' "$1"; }
fail() { printf 'FAIL %s\n' "$1"; failures=$((failures + 1)); }

# Recibe una descripcion y un comando como texto, y segun el resultado ejecuta fail o pass
check() { if eval "$2"; then pass "$1"; else fail "$1"; fi; }

# Hace un chequeo a la web
check_flowable_ui() {
  local url="http://127.0.0.1:8080/flowable-ui/"
  local deadline=$((SECONDS + 60))
  local response="sin respuesta"

  # Golpea por 60 segundos a la web hasta que responda un codigo de estado correcto
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

# Chequeos de archivos
check "existe .env" "test -f .env"
check ".env usa Institucion como base" "grep -q '^POSTGRES_DB=Institucion$' .env"
check ".env define POSTGRES_PASSWORD" "grep -q '^POSTGRES_PASSWORD=' .env"
check "existe esquema de institucion" "test -f infra/db/sql/institucion-schema.sql"
check "existe seed de desarrollo" "test -f infra/db/sql/seed.sql"
check "existe Makefile" "test -f Makefile"
check "existe target dev-up" "grep -q '^dev-up:' Makefile"

# Verificacion del compose
if ! docker compose --profile dev config >/dev/null 2>&1; then
  fail "docker compose config es válido"
else
  pass "docker compose config es válido"
  # Guardamos la salida para inspeccionarla 
  config="$(docker compose --profile dev config)"

  # Enviamos como variable de entrada a grep el config para buscar las variables
  check "Postgres usa POSTGRES_PASSWORD" "grep -q 'POSTGRES_PASSWORD:' <<<\"\$config\""

  # Verificamos que los contenedores no tienen nombre fijo para no tener problemas con ejecuciones que no son del compose
  check "no hay container_name fijos" "! grep -q 'container_name' <<<\"\$config\""

  # Verificar que se ejecuto el compose con perfil dev
  check "pgAdmin está bajo el profile dev" "grep -q 'dev' <<<\"\$config\""
fi

if $RUNTIME; then
  # Verifica que los contenedores se estan ejecutando
  check "contenedor postgres corriendo" "docker compose --profile dev ps --status running --services | grep -qx postgres"
  check "contenedor flowable corriendo" "docker compose --profile dev ps --status running --services | grep -qx flowable"
  check "contenedor pgadmin corriendo" "docker compose --profile dev ps --status running --services | grep -qx pgadmin"
  
  # Verifica existencia, inicializacion de esquema y pertenencia del esquema a la base de datos
  check "base de datos principal existe" "docker compose exec -T postgres psql -U \"\${POSTGRES_USER}\" -d postgres -tAc \"SELECT 1 FROM pg_database WHERE datname = '\${POSTGRES_DB}'\" | grep -qx 1"
  check "Flowable inicializó su esquema" "docker compose exec -T postgres psql -U \"\${POSTGRES_USER}\" -d \"\${POSTGRES_DB}\" -tAc \"SELECT to_regclass('public.act_re_procdef')\" | grep -q act_re_procdef"
  check "esquema de institucion inicializado en POSTGRES_DB" "docker compose exec -T postgres psql -U \"\${POSTGRES_USER}\" -d \"\${POSTGRES_DB}\" -tAc \"SELECT to_regclass('public.schema_version')\" | grep -q schema_version"
  check_flowable_ui
fi

# Resultado final
if (( failures > 0 )); then
  printf '\n%d verificación(es) fallaron.\n' "$failures"
  exit 1
fi

printf '\nTodas las verificaciones pasaron.\n'
