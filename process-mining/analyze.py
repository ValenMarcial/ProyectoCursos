import os

import pandas as pd
import pm4py
import argparse

parser = argparse.ArgumentParser()
parser.add_argument(
    "--solo-terminadas", action="store_true",
    help="Excluye las instancias que todavía no terminaron",
)
argumentos = parser.parse_args()

# Carpeta donde se guardan los gráficos y el modelo.
SALIDA = "resultados"
os.makedirs(SALIDA, exist_ok=True)

# Leer el CSV. Mantener los identificadores como texto.
df = pd.read_csv("eventos.csv", dtype=str)

sin_terminar = df.loc[df["instancia_terminada"] == "false", "instancia"].nunique()
if argumentos.solo_terminadas:
    df = df[df["instancia_terminada"] == "true"]
    print(f"Se excluyeron {sin_terminar} instancias sin terminar.")
    if df.empty:
        raise SystemExit("No quedan instancias terminadas para analizar.")
elif sin_terminar:
    print(f"Aviso: {sin_terminar} instancias no terminaron y se incluyen "
          "(con --solo-terminadas se excluyen).")

# Conservar las tareas y eventos de la negociación.
TIPOS = [
    "userTask", "serviceTask", "scriptTask",
    "businessRuleTask", "intermediateCatchEvent",
]
df = df[df["tipo"].isin(TIPOS)].copy()


# Convertir fechas y conservar el orden del CSV ante empates.
df["fecha_hora"] = pd.to_datetime(df["fecha_hora"], utc=True)
df = df.sort_values(
    ["instancia", "fecha_hora"], kind="stable"
)

# Adaptar las columnas al formato utilizado por PM4Py.
log = pm4py.format_dataframe(
    df,
    case_id="instancia",
    activity_key="actividad",
    timestamp_key="fecha_hora",
)

# 1. Recorridos o variantes más frecuentes.
variantes = pm4py.get_variants_as_tuples(log)
total = log["case:concept:name"].nunique()

print(f"\nInstancias analizadas: {total}")
print(f"Recorridos diferentes: {len(variantes)}")

ordenadas = sorted(
    variantes.items(),
    key=lambda elemento: elemento[1],
    reverse=True,
)

# Los tres más frecuentes. Si varios recorridos empatan en el tercer lugar,
# se muestran todos los empatados.
if len(ordenadas) > 3:
    minimo = ordenadas[2][1]
    mostrados = [e for e in ordenadas if e[1] >= minimo]
else:
    mostrados = ordenadas

print(f"\nLos tres recorridos más frecuentes:")
for recorrido, cantidad in mostrados:
    print(f"\n{cantidad} casos ({100 * cantidad / total:.1f} %)")
    print(" → ".join(recorrido))

if len(mostrados) > 3:
    empatados = [e for e in mostrados if e[1] == mostrados[-1][1]]
    print(
        f"\nNota: {len(empatados)} recorridos empatan en el tercer lugar "
        f"con {mostrados[-1][1]} casos; se muestran todos."
    )

print("\nTodos los recorridos:")
for recorrido, cantidad in ordenadas:
    print(f"{cantidad} casos ({100 * cantidad / total:.1f} %): " + " → ".join(recorrido))

# 2. Grafo de sucesión directa:
# cada flecha indica cuántas veces una tarea siguió a otra.
dfg, inicios, finales = pm4py.discover_dfg(log)
pm4py.save_vis_dfg(
    dfg, inicios, finales, f"{SALIDA}/recorridos.png"
)

# 3. Modelo BPMN descubierto a partir de las ejecuciones.
modelo = pm4py.discover_bpmn_inductive(log)
pm4py.save_vis_bpmn(modelo, f"{SALIDA}/modelo_descubierto.png")
pm4py.write_bpmn(modelo, f"{SALIDA}/modelo_descubierto.bpmn")

print("\nArchivos generados:")
print(f"- {SALIDA}/recorridos.png")
print(f"- {SALIDA}/modelo_descubierto.png")
print(f"- {SALIDA}/modelo_descubierto.bpmn")