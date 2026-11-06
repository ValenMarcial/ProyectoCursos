# Backend web Spring Boot: iniciar solicitudes

El backend Spring Boot recibe la solicitud pública, busca o registra la empresa en PostgreSQL, guarda los datos en `solicitudes` e inicia en Flowable una instancia mediante el evento de mensaje `msgSolicitud`. La solicitud queda vinculada al ID de instancia devuelto; esta entrega no crea ni asocia un curso.

No incluye una pantalla web, no completa tareas de Flowable, no envía propuestas y no recibe respuestas de la empresa. Las respuestas posteriores se simulan por separado por ahora.

## 1. Levantar y desplegar el proceso

Desde la raíz del proyecto:

```bash
make dev-up
make db-migrate
make verify
make deploy
```

`make dev-up` inicia PostgreSQL, Flowable y el backend. `make db-migrate` aplica en orden las migraciones institucionales pendientes desde el baseline; sobre una base actualizada informa la versión actual y no hace cambios. No borra ni recrea el volumen. `make deploy` publica el BPMN y sus reglas en Flowable; debe ejecutarse después de iniciar el entorno y cada vez que se quiera desplegar una nueva versión.

- Flowable UI: <http://127.0.0.1:8080/flowable-ui>
- Backend: <http://127.0.0.1:8081>

## 2. Iniciar una solicitud

La web envía un `POST` a `/api/solicitudes`. También se puede probar con `curl`:

```bash
curl -i -X POST http://127.0.0.1:8081/api/solicitudes \
  -H 'Content-Type: application/json' \
  -d '{
    "empresaNombre": "Tech Solutions S.A.",
    "contactoNombre": "Ana Pérez",
    "contactoEmail": "ana@example.com",
    "contactoTelefono": "555-0101",
    "tematica": "Capacitación BPMN",
    "descripcion": "Curso para el equipo de desarrollo",
    "participantesEstimados": 12,
    "fechasPreferidas": "Marzo de 2027"
  }'
```

El cuerpo requiere:

| Campo | Descripción |
|---|---|
| `empresaNombre` | Nombre usado para encontrar o registrar la empresa |
| `contactoNombre` | Nombre de la persona de contacto |
| `contactoEmail` | Email de contacto válido |
| `contactoTelefono` | Teléfono de contacto, opcional |
| `tematica` | Tema de la capacitación solicitada |
| `descripcion` | Descripción del curso solicitado |
| `participantesEstimados` | Cantidad positiva estimada de participantes |
| `fechasPreferidas` | Descripción libre de las fechas preferidas |
| `plazoRecordatorio` | Opcional. Duración ISO-8601 positiva para pruebas; por ejemplo, `PT1M` |

El backend normaliza el nombre de empresa quitando espacios externos y busca sin distinguir mayúsculas. Si no existe, registra la empresa; después guarda todos los datos del formulario en `solicitudes` con un `business_key` único y estado de integración `pendiente`. La transacción PostgreSQL termina antes de llamar a Flowable. Al recibir el ID de la instancia, lo guarda en `solicitudes.instancia_flowable_id` y actualiza el estado de integración a `iniciada`. No crea una fila en `cursos` todavía.

El ID interno de la empresa lo resuelve el backend y lo envía a Flowable; no se acepta un `empresaId` del cliente. El backend envía a Flowable el nombre e ID interno de la empresa, la temática, descripción, contacto, participantes estimados y fechas preferidas como variables del proceso.

El timer del proceso espera **cinco días** por defecto: al llegar a “Enviar propuesta”, el BPMN asigna `P5D` si `plazoRecordatorio` no está definida. Para probar sin esperar, enviá el campo opcional, por ejemplo `"plazoRecordatorio": "PT1M"`; Flowable usará ese valor en lugar del predeterminado para esa instancia.

Ejemplo para una prueba rápida:

```json
{
  "empresaNombre": "Tech Solutions S.A.",
  "contactoNombre": "Ana Pérez",
  "contactoEmail": "ana@example.com",
  "contactoTelefono": "555-0101",
  "tematica": "Capacitación BPMN",
  "descripcion": "Curso para el equipo de desarrollo",
  "participantesEstimados": 12,
  "fechasPreferidas": "Marzo de 2027",
  "plazoRecordatorio": "PT1M"
}
```

Una respuesta correcta es `201 Created`:

```json
{
  "processInstanceId": "ID_GENERADO_POR_FLOWABLE",
  "businessKey": "solicitud-IDENTIFICADOR_UNICO",
  "estado": "INICIADA"
}
```

Guardá el `processInstanceId` para localizar la instancia en Flowable. Si PostgreSQL no está disponible, el backend devuelve `503 Service Unavailable` y no inicia Flowable. Si Flowable responde con un rechazo HTTP 4xx, el backend elimina la fila de `solicitudes`, conserva la empresa y devuelve `502 Bad Gateway`. Ante timeout o respuesta 5xx, consulta Flowable por `businessKey`: si encuentra la instancia, guarda el vínculo y responde `201`; si no puede confirmarla, conserva la solicitud pendiente y devuelve `502` con `businessKey`, `estado: "PENDIENTE"` y el ID si se llegó a conocer. Verificá esa clave antes de reenviar para evitar duplicar solicitudes o instancias.

## 3. Qué ocurre después

Una vez iniciada la instancia, el proceso continúa en Flowable según el BPMN. El jefe administrativo completa allí la tarea **“Registrar y Elaborar propuesta”** con programa, presupuesto y fechas posibles. Cuando la empresa acepta, **“Habilitar acceso de la empresa”** crea el curso y guarda su ID en `solicitudes.curso_id`; el motor de Flowable necesita `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`. El paso **“Enviar propuesta”** actualmente solo registra datos en los logs; no manda un correo.

Esta API termina su responsabilidad al iniciar el proceso. No tiene una ruta para registrar `ACEPTACION`, `RECHAZO` o `MODIFICACIONES`; la recepción de esos mensajes debe integrarse por separado.

## Consideraciones

- La empresa se identifica por nombre, pero esto no autentica a quien envía la solicitud.
- `make db-migrate` aplica las migraciones institucionales pendientes sin borrar el volumen. El baseline inicial se registra en versión `0`; los cambios futuros se agregan como migraciones `001`, `002`, etc. Si el volumen se creó antes de este baseline, recrealo una vez con `make dev-reset`.
- La web debe llamar al backend, no directamente a Flowable ni incluir las credenciales de Flowable.
- CORS permite por defecto los orígenes locales `http://localhost:3000` y `http://localhost:5173`.

Para conocer los nombres de mensajes y variables del modelo, consultá el [contrato de integración](./contrato.md).
