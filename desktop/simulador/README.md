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

Al abrirse se conecta sola con los valores por defecto. Si Flowable está en otra dirección o con otras credenciales, se cambian arriba y se aprieta **Conectar**.

1. En la tabla de solicitudes se ve la empresa, la temática, el contacto y la **etapa** del proceso (verde: le toca a la empresa; naranja: le toca a la institución; gris: nada pendiente).
2. Al elegir una solicitud aparecen, como botones de colores, solo las comunicaciones que el proceso espera ahora.
3. Elegir una comunicación, completar sus datos y apretar **Enviar comunicación**. En la aceptación, la fecha viene sugerida (dos minutos adelante, en hora local) para probar el timer sin esperar.

La tabla y las comunicaciones se actualizan solas cada 5 segundos (se puede desactivar) y después de cada envío. El panel **Registro** guarda, con hora, lo que hizo el simulador.

### Consola

```bash
mvn test                                    # tests (no necesitan Flowable)

mvn -q compile exec:java -Dexec.args="listar"
mvn -q compile exec:java -Dexec.args="habilitadas <instanciaId>"
mvn -q compile exec:java -Dexec.args="enviar <instanciaId> ACEPTACION fechaInicio=2026-11-20T09:00:00"
mvn -q compile exec:java -Dexec.args="enviar <instanciaId> RECHAZO"
```

Por defecto usa `http://127.0.0.1:8080/flowable-ui/process-api` con `admin`/`test`. Se cambia con las variables de entorno `FLOWABLE_REST_URL`, `FLOWABLE_USER` y `FLOWABLE_PASSWORD`.

## Probado contra Flowable real

Con `make dev-up` y `make deploy`, iniciando solicitudes con `POST /api/solicitudes` y completando "Registrar y Elaborar propuesta":

- Aceptación, rechazo, nómina y cancelación se envían bien desde la ventana y desde la consola; el proceso avanza y guarda `fechaInicio` y `participantes`.
- La fecha de aceptación se envía con zona horaria: si se escribe sin zona se usa la de esta máquina. Se comprobó que Flowable lee una fecha sin zona como UTC, así que una hora local quedaba corrida.
- Cancelación, reprobada el 12/11/2026 con el BPMN de master: la instancia termina en "Notificar Cierre" con `resultado = cancelado` (también en `solicitudes.resultado` y `cursos.estado`). Solo se compensa lo que ya se había conseguido: con el aula reservada y el aula virtual completada, se liberaron las dos, y el equipamiento, que no se había contratado, no se tocó. Antes la instancia quedaba detenida en el subproceso "Cancelación"; se corrigió en el BPMN (#55).
- Después de la aceptación, las habilitadas pasan a ser `NOMINA` y `CANCELACION`, como espera el proceso.

## Pendiente

- "Recibir nómina" es un receive task identificado por el id de la actividad (`Comunicacion.NOMINA`). Si cambia el id en el BPMN, hay que actualizarlo acá. Alternativa más estable: convertirlo en un evento de mensaje `msgNomina` (lo decide el dueño del BPMN).
