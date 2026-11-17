# Minería de procesos de la negociación (sección 4.4)

Análisis con PM4Py de las ejecuciones de la **etapa de negociación** (la que ocurre antes de "Preparar curso"), a partir del historial de Flowable.

> **Estado:** esta carpeta contiene el código. El CSV con los datos, los gráficos y la conclusión se agregan cuando estén generadas las instancias finales (ver [Los datos](#los-datos) y [CONCLUSION.md](CONCLUSION.md)).

## Qué hay en esta carpeta

| Archivo | Para qué sirve |
|---|---|
| `run.sh` | Prepara el entorno (si hace falta) y corre la exportación y el análisis |
| `requirements.txt` | Dependencias de Python: `pm4py`, `pandas`, `requests` |
| `export_history.py` | Exporta el historial de Flowable a `eventos.csv` |
| `analyze.py` | Calcula los recorridos más frecuentes y genera los gráficos |
| `CONCLUSION.md` | Comparación con el modelo BPMN (pendiente de los datos finales) |
| `.gitignore` | Evita subir el entorno virtual (`.venv/`) |

Los archivos que genera el análisis (`eventos.csv` y la carpeta `resultados/`) no están incluidos todavía.

## Requisitos

- Python 3 con `venv` y Graphviz (es un programa del sistema, no se instala con `pip`):
  - Ubuntu: `sudo apt install python3-venv graphviz`
  - macOS: `brew install graphviz`
- Flowable en marcha, con el proceso desplegado e instancias ejecutadas (`make dev-up` y `make deploy`).
- Un usuario de Flowable con acceso a la API REST.

## Cómo ejecutarlo

Desde esta carpeta:

```bash
chmod +x run.sh      # solo la primera vez
./run.sh             # exporta el historial y lo analiza
./run.sh exportar    # solo genera eventos.csv
./run.sh analizar    # solo analiza el eventos.csv existente
```

`run.sh` hace lo siguiente:

1. Crea el entorno virtual `.venv` si no existe.
2. Instala `requirements.txt` solo si todavía no está instalado o si el archivo cambió.
3. Avisa si no encuentra Graphviz.
4. Ejecuta los pasos pedidos.

La exportación pide por la terminal el usuario y la contraseña de Flowable. La URL de la API está escrita en `export_history.py` (`BASE_URL`, por defecto `http://localhost:8080/flowable-ui/process-api`).

### Sin `run.sh`

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python export_history.py
python analyze.py
```

## Qué produce

| Salida | Contenido |
|---|---|
| `eventos.csv` | Una fila por actividad ejecutada: `instancia`, `instancia_terminada`, `definicion`, `evento_id`, `actividad_id`, `actividad`, `tipo`, `inicio`, `fecha_hora`, `recurso` |
| Consola | Cantidad de instancias, recorridos distintos y los tres más frecuentes con su cantidad y porcentaje |
| `resultados/recorridos.png` | Grafo de sucesión directa: cada flecha indica cuántas veces una actividad siguió a otra |
| `resultados/modelo_descubierto.png` y `.bpmn` | Modelo BPMN reconstruido a partir de las ejecuciones |

## Los datos

El análisis usa **las instancias que haya en Flowable al momento de exportar**. Todavía no hay datos en el repositorio: el CSV y los gráficos definitivos se agregan cuando estén generadas las instancias finales. Para actualizarlos basta con volver a correr `./run.sh`; el código no cambia.

Sirve cualquier instancia ya generada, por el medio que sea (simulador, sitio web, etc.).

### Generar instancias en el momento (opcional)

Una forma rápida de comprobarlo localmente es el script `infra/scripts/recorrido.py`, desde la raíz del repositorio:

```bash
make recorrido ESCENARIO=<escenario>
```

| Escenario | Recorrido de negociación que produce |
|---|---|
| `rechazo` | Propuesta, rechazo, archivo |
| `sin-respuesta` | Propuesta, dos recordatorios, archivo (dura cerca de un minuto) |
| `cancelar` | Propuesta, aceptación (el resto es preparación) |
| `final` | Igual que `cancelar` en la negociación, pero recorre todo el proceso (tarda unos minutos) |
| `limite` | Solicitud no viable: antes completa los cursos que faltan para llegar a cinco en ejecución |
| `limpiar-limite` | Cancela los cursos que dejó `limite` |

Conviene ejecutar `limite` al final, seguido de `limpiar-limite`: mientras haya cinco cursos en ejecución, toda solicitud nueva resulta no viable.

Ejemplo de una tanda:

```bash
for i in 1 2 3 4; do make recorrido ESCENARIO=rechazo; done
for i in 1 2 3;   do make recorrido ESCENARIO=cancelar; done
make recorrido ESCENARIO=final
for i in 1 2;     do make recorrido ESCENARIO=sin-respuesta; done
make recorrido ESCENARIO=limite
make recorrido ESCENARIO=limpiar-limite
```

Estos escenarios no generan el camino de **modificaciones** (la empresa pide cambios y el jefe revisa la propuesta). Si ninguna instancia lo recorre, ese lazo no aparece en el análisis (ver [Limitaciones](#limitaciones)).

Verificación sugerida: la cantidad de instancias que informa la exportación debería coincidir con las ejecutadas.

## Decisiones de diseño

El script de exportación parte del provisto por la cátedra y se adaptó al proceso implementado.

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

### 2. No se filtra por instancias terminadas

El script original exporta solo instancias terminadas. Acá también se exportan las que siguen en curso, porque una solicitud que aceptó la propuesta y está en preparación ya completó su negociación; excluirla sesgaría el análisis hacia los casos que terminan dentro de la negociación. La columna `instancia_terminada` permite distinguirlas o excluirlas después. Las instancias eliminadas administrativamente (`deleteReason`) se siguen descartando, como en el original.

### 3. Filtro de flujos recorridos

Después de "Enviar propuesta", el proceso espera en una compuerta basada en eventos con cuatro salidas (5 días, Aceptación, Rechazo, Modificaciones). Flowable registra como finalizados **todos** esos eventos, aunque solo uno haya ocurrido. Sin corrección, cada caso mostraría las cuatro ramas y los recorridos no se podrían distinguir.

El script conserva solo las actividades de las que salió un flujo realmente recorrido:

1. Descarga el BPMN desplegado de cada versión del proceso, porque el historial registra cada flujo solo con su id y el elemento de origen figura únicamente en el diagrama.
2. Arma el conjunto de flujos recorridos de la instancia, identificados por **(ejecución, elemento de origen)**. La ejecución evita un error en los lazos: un evento que dispara en una ronda y no en otra es el mismo elemento, pero tiene una ejecución distinta en cada ronda.
3. Descarta las actividades sin flujo de salida recorrido.

En una prueba local con datos provisorios, sin este filtro la exportación daba 152 eventos y 11 recorridos distintos, frente a 92 eventos y 4 recorridos con el filtro. Sin filtro, los eventos de la compuerta aparecen en cada caso en un orden que varía, y el recorrido de un rechazo es indistinguible del de una aceptación.

### 4. Tipos de actividad que se analizan

`analizar.py` conserva `userTask`, `serviceTask`, `scriptTask`, `businessRuleTask` e `intermediateCatchEvent`. El script original conservaba solo los dos primeros, lo que descartaba la evaluación de viabilidad, el envío de propuesta y recordatorios, y los eventos de espera: justamente los que distinguen un recorrido de otro.

### 5. Empates en los tres recorridos más frecuentes

Si varios recorridos empatan en el tercer lugar, se muestran todos y se indica con una nota.

### 6. Salidas ordenadas

Los gráficos y el modelo descubierto se guardan en `resultados/`.

## Limitaciones

- **Dependencia de `executionId`.** El filtro de flujos asume que el historial informa la ejecución de cada registro. Si no coincidiera, el CSV saldría vacío o incompleto.
- **Ids del BPMN.** `ACTIVIDADES` usa los ids del diagrama actual. Si el BPMN se reexporta con ids distintos, la lista debe actualizarse.
- **Lazos con casos mixtos.** El filtro por ejecución está pensado para casos donde un evento dispara en una ronda y no en otra (por ejemplo, un recordatorio y luego un rechazo). Hay que comprobarlo con datos que incluyan ese caso.
- **Solo lo observado.** La minería solo reconstruye lo que aparece en los datos: un camino sin instancias (por ejemplo, las modificaciones) no figura en el modelo descubierto.
- **Solo la negociación.** Las actividades de preparación y posteriores no se analizan.

## Adaptar el script a otro proceso

En `export_history.py` se cambian tres constantes: `BASE_URL`, `PROCESS_KEY` y el conjunto `ACTIVIDADES`.