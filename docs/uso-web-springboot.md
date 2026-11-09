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

## 4. Simular manualmente hasta “Evaluar Recursos”

Estos pasos permiten recorrer el proceso desde la terminal, sin depender de las pantallas administrativas del sitio. Requieren Docker, Java 11 o 17 y Maven.

### Preparar el entorno

Desde la raíz del proyecto:

```bash
make dev-up
make db-migrate
make verify
make build
make seed # carga los usuarios e importa el bpmn
make deploy
```

El build y reinicio hacen que Flowable cargue el listener Java de validación de recursos. Volvé a ejecutar `make deploy` después de cada cambio al BPMN.

### Iniciar la solicitud

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
    "fechasPreferidas": "Marzo de 2027",
    "plazoRecordatorio": "PT10M"
  }'
```

Guardá el valor `processInstanceId` de la respuesta `201 Created`; se usa en los pasos siguientes. Si la instancia resulta no viable, el proceso termina antes de crear la tarea de propuesta. La regla actual permite la solicitud cuando hay menos de cinco instancias activas previas.

### Completar “Registrar y Elaborar propuesta”

En los ejemplos siguientes, el usuario local creado por la seed es `jefeAdministrativo` y su contraseña es `JefeA`. Definí el prefijo de la API REST de Flowable y consultá sus tareas:

```bash
FLOWABLE=http://127.0.0.1:8080/flowable-ui/process-api

curl -sS -u jefeAdministrativo:JefeA \
  "$FLOWABLE/runtime/tasks?assignee=jefeAdministrativo"
```

Copiá el `id` de la tarea **Registrar y Elaborar propuesta** y completala reemplazando `TASK_ID`:

```bash
curl -i -u jefeAdministrativo:JefeA \
  -H 'Content-Type: application/json' \
  -X POST "$FLOWABLE/runtime/tasks/TASK_ID" \
  -d '{
    "action": "complete",
    "variables": [
      {"name": "programa", "type": "string", "value": "Capacitación BPMN"},
      {"name": "presupuesto", "type": "long", "value": 150000},
      {"name": "fechaInicioPosible", "type": "date", "value": "2026-11-20T00:00:00.000+0000"},
      {"name": "fechaFinPropuesta", "type": "date", "value": "2026-11-25T00:00:00.000+0000"}
    ]
  }'
```

El presupuesto debe ser cero o mayor y la fecha final no puede ser anterior a la inicial. Al completar la tarea, el proceso envía la propuesta de forma simulada y queda esperando la respuesta de la empresa.

### Aceptar la propuesta

Desde otra terminal, enviá `msgAceptacion` con el simulador. Reemplazá `PROCESS_INSTANCE_ID` por el valor guardado al iniciar la solicitud:

```bash
cd desktop/simulador
mvn -q compile exec:java -Dexec.args="enviar PROCESS_INSTANCE_ID ACEPTACION fechaInicio=2026-11-20T09:00:00"
```

La comunicación solo está habilitada mientras la instancia espera una respuesta. El simulador también permite consultar las comunicaciones disponibles:

```bash
mvn -q compile exec:java -Dexec.args="habilitadas PROCESS_INSTANCE_ID"
```

### Completar “Evaluar Recursos”

La aceptación inicia “Preparar curso”; sus ramas corren en paralelo. Consultá las tareas candidatas de Administración y copiá el `id` de **Evaluar Recursos**:

```bash
curl -sS -u jefeAdministrativo:JefeA \
  "$FLOWABLE/runtime/tasks?candidateGroup=Administracion"
```

Completá la tarea enviando los tres booleanos. Debe haber al menos uno en `true`; el gateway inclusivo puede activar varias ramas:

```bash
curl -i -u jefeAdministrativo:JefeA \
  -H 'Content-Type: application/json' \
  -X POST "$FLOWABLE/runtime/tasks/RESOURCE_TASK_ID" \
  -d '{
    "action": "complete",
    "variables": [
      {"name": "aulaFisica", "type": "boolean", "value": true},
      {"name": "aulaVirtual", "type": "boolean", "value": false},
      {"name": "equipamiento", "type": "boolean", "value": false}
    ]
  }'
```

Si las tres opciones son falsas o no se envían, el listener rechaza la finalización y la tarea queda pendiente. Si la tarea no aparece, verificá que la solicitud haya sido viable, que se haya completado la propuesta y que el simulador haya enviado la aceptación a esa misma instancia.

## Consideraciones

- La empresa se identifica por nombre, pero esto no autentica a quien envía la solicitud.
- `make db-migrate` aplica las migraciones institucionales pendientes sin borrar el volumen. El baseline inicial se registra en versión `0`; los cambios futuros se agregan como migraciones `001`, `002`, etc. Si el volumen se creó antes de este baseline, recrealo una vez con `make dev-reset`.
- La web debe llamar al backend, no directamente a Flowable ni incluir las credenciales de Flowable.
- CORS permite por defecto los orígenes locales `http://localhost:3000` y `http://localhost:5173`.

Para conocer los nombres de mensajes y variables del modelo, consultá el [contrato de integración](./contrato.md).
