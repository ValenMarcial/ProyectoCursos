#!/usr/bin/env bash
# Prepara el entorno (si hace falta) y corre el análisis de minería de procesos.
#
# Uso:
#   ./run.sh              exporta el historial y corre el análisis
#   ./run.sh exportar     solo genera eventos.csv desde Flowable
#   ./run.sh analizar     solo corre el análisis sobre el eventos.csv existente
#
# El script de exportación pide el usuario y la contraseña de Flowable por la terminal.
set -euo pipefail

cd "$(dirname "$0")"
MODO="${1:-todo}"
VENV=".venv"
MARCA="$VENV/.requirements.cksum"

# 1. Entorno virtual: se crea solo si no existe.
if [ ! -d "$VENV" ]; then
    echo ">> Creando entorno virtual en $VENV"
    python3 -m venv "$VENV"
fi

# shellcheck disable=SC1091
source "$VENV/bin/activate"

# 2. Dependencias: se instalan solo si no están o si cambió requirements.txt.
ACTUAL="$(cksum requirements.txt)"
if [ ! -f "$MARCA" ] || [ "$(cat "$MARCA")" != "$ACTUAL" ]; then
    echo ">> Instalando dependencias (requirements.txt)"
    pip install --quiet --upgrade pip
    pip install --quiet -r requirements.txt
    echo "$ACTUAL" > "$MARCA"
fi

# 3. Graphviz es un programa del sistema (no de pip); sin él no se dibujan los grafos.
if ! command -v dot >/dev/null 2>&1; then
    echo "AVISO: no se encontró Graphviz ('dot'). Los gráficos no se van a generar."
    echo "  macOS:  brew install graphviz"
    echo "  Ubuntu: sudo apt install graphviz"
fi

# 4. Pasos.
if [ "$MODO" = "todo" ] || [ "$MODO" = "exportar" ]; then
    echo ">> Exportando el historial de Flowable"
    python export_history.py
fi

if [ "$MODO" = "todo" ] || [ "$MODO" = "analizar" ]; then
    if [ ! -f eventos.csv ]; then
        echo "ERROR: falta eventos.csv. Corré primero: ./run.sh exportar" >&2
        exit 1
    fi
    if [ ! -f analyze.py ]; then
        echo "AVISO: todavía no existe analyze.py; se omite el análisis."
        exit 0
    fi
    echo ">> Analizando eventos.csv"
    python analyze.py "${@:2}"
fi
