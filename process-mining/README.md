# Minería de procesos de la negociación (sección 4.4)

Análisis con PM4Py de las ejecuciones de la **etapa de negociación** (la que ocurre antes de "Preparar curso"), a partir del historial de Flowable.

> **Estado:** incluye los datos finales (`eventos.csv` y `resultados/`). La comparación con el BPMN y la conclusión están en [CONCLUSION.md](CONCLUSION.md). Cómo se generaron los datos: ver [Los datos de este repositorio](#los-datos-de-este-repositorio).

## Qué hay en esta carpeta

| Archivo | Para qué sirve |
|---|---|
| `run.sh` | Prepara el entorno (si hace falta) y corre la exportación y el análisis |
| `requirements.txt` | Dependencias de Python: `pm4py`, `pandas`, `requests` |
| `export_history.py` | Exporta el historial de Flowable a `eventos.csv` |
| `analyze.py` | Calcula los recorridos más frecuentes y genera los gráficos |
| `eventos.csv` | Log de eventos de los datos finales (47 instancias, 324 eventos) |
| `resultados/` | Grafo de sucesión directa y modelo descubierto de esos datos |
| `CONCLUSION.md` | Recorridos más frecuentes y comparación con el modelo BPMN |
| `.gitignore` | Evita subir el entorno virtual (`.venv/`) |

## Requisitos

- Python 3 con `venv` y Graphviz (es un programa del sistema, no se instala con `pip`):
  - Ubuntu: `sudo apt install python3-venv graphviz`
  - macOS: `brew install graphviz`
- Flowable en marcha, con el proceso desplegado e instancias ejecutadas (`make dev-up` y `make deploy`).
- Un usuario de Flowable con acceso a la API REST.

## Cómo ejecutarlo

Desde esta carpeta:

```bash
chmod +x run.sh                       # solo la primera vez
./run.sh                              # exporta el historial y lo analiza
./run.sh exportar                     # solo genera eventos.csv
./run.sh analizar                     # solo analiza el eventos.csv existente
./run.sh analizar --solo-terminadas   # analiza solo las instancias terminadas
```

`run.sh` hace lo siguiente:

1. Crea el entorno virtual `.venv` si no existe.
2. Instala `requirements.txt` solo si todavía no está instalado o si el archivo cambió.
3. Avisa si no encuentra Graphviz.
4. Ejecuta los pasos pedidos. En el modo `analizar`, los argumentos que siguen se pasan a `analyze.py`.

La exportación pide por la terminal el usuario y la contraseña de Flowable. La URL de la API está escrita en `export_history.py` (`BASE_URL`, por defecto `http://localhost:8080/flowable-ui/process-api`).

### Instancias terminadas y no terminadas

`export_history.py` exporta todas las instancias, terminadas o no, y las distingue en la columna `instancia_terminada`. El filtro se aplica al analizar:

- Sin opciones, `analyze.py` usa todas las instancias. Si alguna no terminó, avisa cuántas son.
- Con `--solo-terminadas`, excluye las que siguen en curso e informa cuántas descartó.

Los resultados de este repositorio se generaron con `--solo-terminadas`. Como todas las instancias de los datos finales están terminadas, el resultado es el mismo con y sin la opción. Volver a ejecutar el `./run.sh` o `./run`  **sobrescribe** `eventos.csv` y `resultados/`.

### Sin `run.sh`

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python export_history.py
python analyze.py --solo-terminadas
```

## Qué produce

| Salida | Contenido |
|---|---|
| `eventos.csv` | Una fila por actividad ejecutada: `instancia`, `instancia_terminada`, `definicion`, `evento_id`, `actividad_id`, `actividad`, `tipo`, `inicio`, `fecha_hora`, `recurso` |
| Consola | Cantidad de instancias, recorridos distintos, los tres más frecuentes con su cantidad y porcentaje y la lista completa al final|
| `resultados/recorridos.png` | Grafo de sucesión directa: cada caja indica cuántas veces ocurrió la actividad y cada flecha, cuántas veces una actividad siguió a otra |
| `resultados/modelo_descubierto.png` y `.bpmn` | Modelo BPMN reconstruido a partir de las ejecuciones |

## Los datos de este repositorio

Los datos finales son **47 instancias** del proceso, generadas con `make corridas`. No son 40 porque el comando agrega 7 instancias al final:

| Origen | Instancias | Descripción |
|---|---|---|
| Corridas | 40 | El plan sortea el destino de cada una (aceptación, rechazo, falta de respuesta o cancelación), la cantidad de modificaciones y si deja pasar un recordatorio |
| Cupo de cursos | 5 | Con `NO_VIABLES` el script acepta 5 solicitudes para llenar el cupo de 5 cursos en ejecución; después las cancela. Todas recorren la negociación como aceptación directa |
| No viables | 2 | Con el cupo lleno, el motor rechaza estas solicitudes por viabilidad |

El plan de las 40 corridas se fijó con la semilla 10: 17 con destino final, 10 rechazos, 7 faltas de respuesta y 6 cancelaciones; 13 con modificaciones y 8 con recordatorio.

Los datos son simulados y las esperas están acortadas: el evento “5 días” se ejecuta con un plazo de 20 segundos en las corridas sin respuesta y de 30 en las que dejan pasar un recordatorio.

### Cómo se generaron

Desde la raíz del repositorio, con una base vacía:

```bash
make dev-reset          # BORRA la base local
make db-migrate
make seed               # pide las credenciales de Flowable
make deploy
make corridas CANTIDAD=40 PARALELO=4 NO_VIABLES=2 SEMILLA=10
```

Tarda entre 20 y 40 minutos porque hace pausas reales; conviene evitar que el equipo se suspenda durante la ejecución. Después, desde esta carpeta:

```bash
./run.sh exportar
./run.sh analizar --solo-terminadas
```

Los pasos para dejar la base de la demo están en [docs/preparar-demo.md](../docs/preparar-demo.md).

### Cómo repetirlos

- **Mismos datos exactos.** La base se guardó con `make demo-guardar DEMO=data/demo/demo-18-11` y se comparte por Drive (`data/demo/` no se sube al repositorio). Para usarla: copiar los dos archivos a `data/demo/` y ejecutar `make demo-cargar DEMO=data/demo/demo-18-11`. **Reemplaza la base local.** Después, exportar y analizar como arriba.
- **Nuevos datos con la misma estructura.** Repetir los comandos de la sección anterior. La semilla fija el sorteo de destinos, modificaciones y recordatorios, de modo que los recorridos de la negociación son los mismos. No fija las pausas, los usuarios ni los recursos, así que los datos no son idénticos y algunas instancias pueden terminar canceladas por falta de aulas (un camino válido del modelo, que no afecta a la negociación).
- **Con otra semilla o sin semilla**, los porcentajes cambian.

### Problemas conocidos

- `make corridas` no debe interrumpirse: deja instancias sin terminar en la base.

## Decisiones de diseño

Se adapto el script que nos dejo el profe

### 1. Qué se considera "negociación"

Se exportan solo los elementos de la etapa previa a "Preparar curso", identificados por su id BPMN en `ACTIVIDADES`:

- Evaluar Viabilidad
- Notificar Decisión (solicitud no viable)
- Registrar y Elaborar propuesta
- Enviar propuesta
- 5 días (evento de tiempo)
- Aceptación, Rechazo y Modificaciones (eventos de mensaje)
- Revisar propuesta
- Enviar recordatorio
- Archivar solicitud
- Habilitar acceso de la empresa

Quedan afuera las compuertas, el evento de inicio y los eventos de fin: no aportan información propia, y el resultado de cada caso ya se distingue por su última actividad (Notificar Decisión, Archivar solicitud o Habilitar acceso). **Habilitar acceso de la empresa** se incluye como cierre del camino aceptado; es la primera actividad después de la aceptación.

### 2. No se filtra por instancias terminadas al exportar

El script original exporta solo instancias terminadas. Acá se exportan todas, porque una solicitud que aceptó la propuesta y está en preparación ya completó su negociación; excluirla sesgaría el análisis hacia los casos que terminan dentro de la negociación. La columna `instancia_terminada` permite distinguirlas, y el filtro se elige al analizar con `--solo-terminadas`. Esa opción descarta también las instancias en preparación, aunque su negociación esté completa. Las instancias eliminadas administrativamente (`deleteReason`) se siguen descartando, como en el original.

### 3. Filtro de flujos recorridos

Después de "Enviar propuesta", el proceso espera en una compuerta basada en eventos con cuatro salidas (5 días, Aceptación, Rechazo, Modificaciones). Flowable registra como finalizados **todos** esos eventos, aunque solo uno haya ocurrido. Sin corrección, cada caso mostraría las cuatro ramas y los recorridos no se podrían distinguir.

El script conserva solo las actividades de las que salió un flujo realmente recorrido:

1. Descarga el BPMN desplegado de cada versión del proceso, porque el historial registra cada flujo solo con su id y el elemento de origen figura únicamente en el diagrama.
2. Arma el conjunto de flujos recorridos de la instancia, identificados por **(ejecución, elemento de origen)**. La ejecución evita un error en los lazos: un evento que dispara en una ronda y no en otra es el mismo elemento, pero tiene una ejecución distinta en cada ronda.
3. Descarta las actividades sin flujo de salida recorrido.

Sin este filtro, los eventos de la compuerta que no ocurrieron aparecerían en el CSV en cada paso por la compuerta, y los recorridos no se podrían distinguir.

### 4. Tipos de actividad que se analizan

`analyze.py` conserva `userTask`, `serviceTask`, `scriptTask`, `businessRuleTask` e `intermediateCatchEvent`. El script original conservaba solo los dos primeros, lo que descartaba la evaluación de viabilidad, el envío de propuesta y recordatorios, y los eventos de espera: justamente los que distinguen un recorrido de otro.

### 5. Empates en los tres recorridos más frecuentes

Si varios recorridos empatan en el tercer lugar, se muestran todos y se indica con una nota. En los datos finales hay un empate: dos recorridos con 3 casos.

### 6. Salidas ordenadas

Los gráficos y el modelo descubierto se guardan en `resultados/`.

## Limitaciones

- **Dependencia de `executionId`.** El filtro de flujos asume que el historial informa la ejecución de cada registro. Con los datos finales funcionó, incluidos los lazos de modificaciones y los casos mixtos (por ejemplo, modificaciones, recordatorio y rechazo). Si no coincidiera, el CSV saldría vacío o incompleto.
- **Ids del BPMN.** `ACTIVIDADES` usa los ids del diagrama actual. Si el BPMN se reexporta con ids distintos, la lista debe actualizarse.
- **Solo lo observado.** La minería solo reconstruye lo que aparece en los datos. Por ejemplo, no hay ninguna instancia que reciba modificaciones después de un recordatorio, aunque el BPMN lo permite.
- **Datos simulados.** Los porcentajes dependen del plan de `make corridas` (y de las 5 instancias del cupo, que son aceptaciones directas) y no representan el comportamiento de empresas reales.
- **Solo la negociación.** Las actividades de preparación y posteriores no se analizan.

## Adaptar el script a otro proceso

En `export_history.py` se cambian tres constantes: `BASE_URL`, `PROCESS_KEY` y el conjunto `ACTIVIDADES`.