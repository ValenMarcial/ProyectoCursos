#!/usr/bin/env bash
set -euo pipefail
shopt -s nullglob

cd "$(dirname "$0")/../.."

migration_files=(infra/db/migrations/[0-9][0-9][0-9]-*.sql)
if ((${#migration_files[@]} == 0)); then
  printf 'No hay migraciones en infra/db/migrations\n'
  exit 0
fi

database_version=$(docker compose exec -T postgres sh -c \
  'psql -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT COALESCE(MAX(version), 0) FROM schema_version"')
if [[ ! "$database_version" =~ ^[0-9]+$ ]]; then
  printf 'La versión del schema devuelta por PostgreSQL no es válida: %s\n' "$database_version" >&2
  exit 1
fi

applied_count=0
for migration_file in "${migration_files[@]}"; do
  filename=${migration_file##*/}
  version=${filename%%-*}
  if [[ ! "$version" =~ ^[0-9]{3}$ ]]; then
    printf 'Nombre de migración inválido: %s (se esperaba NNN-nombre.sql)\n' "$filename" >&2
    exit 1
  fi

  version_number=$((10#$version))
  if ((version_number <= database_version)); then
    continue
  fi
  if ((version_number != database_version + 1)); then
    printf 'Falta la migración %03d antes de %s\n' "$((database_version + 1))" "$filename" >&2
    exit 1
  fi

  printf 'Aplicando migración %s\n' "$filename"
  docker compose exec -T postgres sh -c \
    'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f "/db-migrations/$1"' \
    sh "$filename"

  database_version=$(docker compose exec -T postgres sh -c \
    'psql -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT COALESCE(MAX(version), 0) FROM schema_version"')
  if [[ "$database_version" != "$version_number" ]]; then
    printf 'La migración %s no registró la versión esperada (%s)\n' \
      "$filename" "$version_number" >&2
    exit 1
  fi
  applied_count=$((applied_count + 1))
done

if ((applied_count == 0)); then
  printf 'El schema ya está actualizado (versión %s)\n' "$database_version"
else
  printf 'Migraciones aplicadas: %s (versión actual %s)\n' "$applied_count" "$database_version"
fi