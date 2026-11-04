# Backend web Spring Boot: iniciar solicitudes

El backend Spring Boot tiene un solo objetivo: recibir de la web los datos de una solicitud e iniciar en Flowable una instancia del proceso mediante el evento de mensaje `msgSolicitud`.

No incluye una pantalla web, no consulta PostgreSQL, no completa tareas de Flowable, no envía propuestas y no recibe respuestas de la empresa. Las respuestas posteriores se simulan por separado por ahora.

## 1. Levantar y desplegar el proceso

Desde la raíz del proyecto:

```bash
make dev-up
make verify
make deploy
```

`make dev-up` inicia PostgreSQL, Flowable y el backend. `make deploy` publica el BPMN y sus reglas en Flowable; debe ejecutarse después de iniciar el entorno y cada vez que se quiera desplegar una nueva versión.

- Flowable UI: <http://127.0.0.1:8080/flowable-ui>
- Backend: <http://127.0.0.1:8081>

## 2. Iniciar una solicitud

La web envía un `POST` a `/api/solicitudes`. También se puede probar con `curl`:

```bash
curl -i -X POST http://127.0.0.1:8081/api/solicitudes \
  -H 'Content-Type: application/json' \
  -d '{
    "empresaId": 1,
    "nombre": "Capacitación BPMN",
    "descripcion": "Curso para el equipo de desarrollo"
  }'
```

El cuerpo requiere:

| Campo | Descripción |
|---|---|
| `empresaId` | Identificador positivo de la empresa, guardado como variable del proceso |
| `nombre` | Nombre de la solicitud |
| `descripcion` | Descripción del curso solicitado |
| `plazoRecordatorio` | Opcional. Duración ISO-8601 positiva para pruebas; por ejemplo, `PT1M` |

El backend valida que los campos obligatorios estén presentes y que, si se indicó, el plazo sea una duración ISO-8601 positiva. Luego genera un `businessKey` único y llama a Flowable REST con `message: "msgSolicitud"` y las variables de la solicitud.

El timer del proceso espera **cinco días** por defecto: al llegar a “Enviar propuesta”, el BPMN asigna `P5D` si `plazoRecordatorio` no está definida. Para probar sin esperar, enviá el campo opcional, por ejemplo `"plazoRecordatorio": "PT1M"`; Flowable usará ese valor en lugar del predeterminado para esa instancia.

Ejemplo para una prueba rápida:

```json
{
  "empresaId": 1,
  "nombre": "Capacitación BPMN",
  "descripcion": "Curso para el equipo de desarrollo",
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

Guardá el `processInstanceId` para localizar la instancia en Flowable. Un error de conexión o una respuesta fallida de Flowable se devuelve como `502 Bad Gateway`.

## 3. Qué ocurre después

Una vez iniciada la instancia, el proceso continúa en Flowable según el BPMN. El jefe administrativo completa allí la tarea **“Registrar y Elaborar propuesta”**. Cuando esa tarea termina, el paso **“Enviar propuesta”** actualmente solo registra los datos en los logs; no manda un correo.

Esta API termina su responsabilidad al iniciar el proceso. No tiene una ruta para registrar `ACEPTACION`, `RECHAZO` o `MODIFICACIONES`; la recepción de esos mensajes debe integrarse por separado.

## Consideraciones

- `empresaId` se valida como positivo, pero el backend no verifica contra PostgreSQL que la empresa exista.
- La autenticación de la empresa no está implementada. `empresaId` en el JSON identifica los datos de la solicitud, pero no demuestra quién la envió.
- La web debe llamar al backend, no directamente a Flowable ni incluir las credenciales de Flowable.
- CORS permite por defecto los orígenes locales `http://localhost:3000` y `http://localhost:5173`.

Para conocer los nombres de mensajes y variables del modelo, consultá el [contrato de integración](./contrato.md).
