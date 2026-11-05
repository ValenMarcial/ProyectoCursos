# Simulador de comunicaciones de la empresa (API REST)

Simula lo que la empresa le comunica a la institución (punto 4.2.2 del enunciado) actuando sobre las instancias del proceso por la API REST de Flowable. Es un proyecto Maven independiente del motor: sus dependencias no se mezclan con las que carga Flowable UI.

> **Estado:** núcleo (cliente REST y lógica de comunicaciones habilitadas), ventana Swing y consola para pruebas rápidas. El enunciado solo deja usar línea de comandos a grupos de hasta 3 personas, y el grupo es de 5, por eso la interfaz principal es la ventana.

## Comunicaciones

| Comunicación | Cómo llega al proceso | Datos (variables) | Habilitada cuando |
|---|---|---|---|
| `ACEPTACION` | mensaje `msgAceptacion` | `fechaInicio` (ISO-8601, ej. `2026-11-20T09:00:00`) | El proceso espera la respuesta a la propuesta |
| `RECHAZO` | mensaje `msgRechazo` | ninguno | Ídem |
| `MODIFICACIONES` | mensaje `msgModificaciones` | `observaciones` | Ídem |
| `NOMINA` | señal al receive task "Recibir nómina" | `participantes` (separados por coma) | Durante "Preparar curso", antes de recibir la nómina |
| `CANCELACION` | mensaje `msgCancelacion` | ninguno | Durante "Preparar curso" |

Una comunicación está habilitada solo si el proceso la está esperando en ese momento: el simulador lo consulta con `GET /runtime/executions` y no deja enviar las demás.

## Requisitos y uso

Java 11 o 17 y Maven, con el entorno levantado (`make dev-up`) y el proceso desplegado (`make deploy`). Desde esta carpeta:

### Ventana

```bash
mvn -q compile exec:java                    # sin argumentos abre la ventana
```

1. Completar URL, usuario y contraseña de Flowable (vienen con los valores por defecto) y apretar **Conectar**.
2. Elegir una solicitud de la lista. Aparecen solo las comunicaciones que el proceso espera ahora.
3. Elegir una comunicación, completar sus datos y apretar **Enviar comunicación**. En la aceptación, la fecha viene sugerida (dos minutos adelante) para probar el timer sin esperar.

La lista y las comunicaciones se actualizan solas cada 5 segundos (se puede desactivar) y después de cada envío.

### Consola

```bash
mvn test                                    # tests (no necesitan Flowable)

mvn -q compile exec:java -Dexec.args="listar"
mvn -q compile exec:java -Dexec.args="habilitadas <instanciaId>"
mvn -q compile exec:java -Dexec.args="enviar <instanciaId> ACEPTACION fechaInicio=2026-11-20T09:00:00"
mvn -q compile exec:java -Dexec.args="enviar <instanciaId> RECHAZO"
```

Por defecto usa `http://127.0.0.1:8080/flowable-ui/process-api` con `admin`/`test`. Se cambia con las variables de entorno `FLOWABLE_REST_URL`, `FLOWABLE_USER` y `FLOWABLE_PASSWORD`.

## Pendiente

- Probarlo contra un Flowable real: hasta ahora la lógica se probó contra un servidor falso y la ventana solo con una prueba de humo (se construye sin errores). Las instancias deben iniciarse con `POST /api/solicitudes` del backend para que la aceptación funcione de punta a punta.
- "Recibir nómina" es un receive task identificado por el id de la actividad (`Comunicacion.NOMINA`). Si cambia el id en el BPMN, hay que actualizarlo acá. Alternativa más estable: convertirlo en un evento de mensaje `msgNomina` (lo decide el dueño del BPMN).
- `participantes` y `observaciones` no están todavía en `docs/contrato.md`.
