# Motor de procesos y acceso de las empresas

Esta sección explica las clases que se incorporaron al motor de Flowable, las variables que usan y las decisiones de implementación, junto con el backend del acceso de las empresas (escenario 10).

## 1. Despliegue en Flowable UI

El proceso corre en Flowable UI 6.8 (Docker), sobre la misma base PostgreSQL `Institucion` que guarda las solicitudes, los cursos y las empresas.

- **Clases del proyecto y Drools.** La imagen de Flowable UI trae los motores BPMN y DMN, pero no Drools. `make build` compila el `.jar` del proyecto y copia a `flowable-lib/` solo las librerías de Drools que faltan; las que la imagen ya trae en otra versión no se tocan, para evitar conflictos. El contenedor monta esa carpeta en su classpath.
- **Registro de las reglas.** `RulesConfigurator` (registrado como `EngineConfigurator` en `META-INF/services`) agrega el `RulesDeployer` al motor de procesos, para que el `.drl` se despliegue junto con el BPMN. `make deploy` despliega los dos en un único deployment por la API REST.
- **Zona horaria.** Los contenedores corren en UTC. Los timers de fecha y las fechas que manda el simulador se interpretan igual en todas las máquinas del grupo.
- **Edición en el Modeler.** Al guardar, el Modeler convierte en `sequenceFlow` las flechas entre un boundary de compensación y su tarea, y Flowable 6.8 necesita una `association`. `ModelerExportFixer` corrige eso (y el nombre del proceso) al importar y exportar, editando solo esas líneas para que el diff sea mínimo.

## 2. Viabilidad: conteo de cursos en ejecución

El listener `EvaluateViability` corre al iniciar "Evaluar Viabilidad" y le pasa a las reglas la cantidad de cursos en ejecución (`AmountCursos`). La regla rechaza la solicitud si hay 5 o más.

**Qué cuenta como curso en ejecución.** Se cuentan las instancias activas del proceso (por key, así entran todas las versiones desplegadas) que tienen la variable `cursoId`, es decir, las que ya pasaron por "Habilitar acceso de la empresa". El enunciado habla de las instancias no finalizadas, incluidas las que están en acuerdo; el profesor aclaró después que una solicitud ocupa lugar recién cuando la empresa acepta. Con este criterio:

- La instancia que se evalúa queda afuera sola, porque todavía no tiene `cursoId`.
- Las solicitudes en evaluación o negociación no cuentan.
- Las instancias terminadas (finalizadas, canceladas, rechazadas) no cuentan, porque dejan de estar activas.

La cantidad la obtiene el motor; nunca se ingresa a mano.

## 3. Asignación por menor carga

"Confeccionar lista definitiva" tiene el Task Listener `AssignToLeastBusyAdministrative` en el evento `create`. Asigna la tarea al integrante del grupo `Administracion` con menos tareas de usuario asignadas en el motor.

- **Qué cuenta:** solo las tareas activas con `assignee` igual a ese usuario. Las que tienen al grupo como candidato y nadie reclamó no cuentan.
- **Empate:** se elige al azar entre los empatados. El desempate es una estrategia que se inyecta por constructor, así los tests usan una fija y el resultado es reproducible.
- **Sin integrantes:** la tarea queda sin `assignee` y con `Administracion` como grupo candidato, para que alguien la reclame más adelante.

## 4. Aulas y compensación

El web service de aulas está simulado dentro del motor.

- **Reservar aula** (`ReserveClassroom`) asigna la primera de las dos aulas libres y la guarda en `aulaAsignada`. Un aula está ocupada mientras alguna instancia activa la tenga en esa variable. Si no queda ninguna, lanza `BpmnError("SIN_AULAS")`.
- **Falta de aulas (escenario 7).** Un boundary de error en "Reservar aula" captura `SIN_AULAS`, dispara las compensaciones de lo que ya se consiguió y propaga el error a "Preparar curso", que cierra con "Notificar Cierre" (`resultado = cancelado`). El código del error queda en `motivoCierre`, así la web puede mostrar el motivo.
- **Liberar Aula** (`ReleaseClassroom`) es la tarea de compensación: elimina `aulaAsignada` y el aula vuelve a quedar libre. Flowable solo compensa actividades que terminaron, así que una cancelación libera únicamente lo que se había conseguido.

## 5. Habilitar acceso de la empresa

`CreateCourseOnAcceptance` corre cuando la empresa acepta la propuesta:

- **Valida las fechas:** si la `fechaInicio` acordada es posterior a `fechaFinPropuesta`, rechaza el paso sin crear nada.
- **Crea el curso:** con los datos de la solicitud y de la propuesta, lo vincula a la solicitud y guarda `cursoId` en la instancia.
- **Crea la cuenta de la empresa** en `cuentas_empresa`, si todavía no tiene una. Una empresa tiene una sola cuenta: los cursos siguientes la reutilizan.
  - Usuario: el email de contacto en minúsculas; si falta o ya lo usa otra empresa, `empresa-<id>`.
  - Clave: 12 caracteres aleatorios. Se guarda solo el hash PBKDF2-HMAC-SHA256 (600.000 iteraciones, sal aleatoria), nunca en texto plano ni en variables del proceso. Las iteraciones van dentro del texto guardado, así se pueden subir sin invalidar las claves existentes.
  - Entrega simulada: las credenciales se escriben en el log de Flowable. Es solo para la demostración; un despliegue real las enviaría por email.
- **Es idempotente:** un reintento no duplica el curso ni la cuenta.

Todo ocurre en una sola transacción: si falla la cuenta, tampoco queda el curso.

## 6. Resultado del proceso y estado del curso

**Resultado.** Cada camino de cierre define la variable `resultado`:

| Resultado | Dónde se define |
|---|---|
| `no_viable` | Script de "Notificar Decisión" |
| `rechazado` | Listener `end` del evento "Rechazo" |
| `cerrado_por_falta_de_respuesta` | Listener `take` del flujo "Sí" de "¿Se enviaron 2 recordatorios?" |
| `cancelado` | Script de "Notificar Cierre" (cancelación o falta de aulas) |
| `finalizado` | Listener `start` del fin del proceso, después de "Enviar informe" |

El Execution Listener `RecordProcessResult`, configurado a nivel proceso, corre al terminar la instancia:

- Escribe el resultado siempre en `solicitudes.resultado`, y también en `cursos.estado` si existe el curso.
- Rechazo, falta de respuesta y no viable terminan antes de "Habilitar acceso" y no tienen curso.
- Un valor desconocido es un error del modelo: lanza una excepción y la instancia no termina.

**Estados intermedios.** Para que la empresa vea en qué etapa está su curso, `UpdateCourseState` escribe `cursos.estado` al iniciar algunos elementos del BPMN. El estado llega como campo del listener, así una sola clase sirve para todos:

| Estado | Cuándo |
|---|---|
| `en_preparacion` | "Habilitar acceso" crea el curso |
| `confirmado` | Inicio de "Enviar confirmación" (la preparación terminó) |
| `pendiente_de_inicio` | Inicio del timer "Fecha programada" (ya hay docente) |
| `en_desarrollo` | Inicio de "Desarrollar curso" |
| `en_elaboracion_informe` | Inicio de "Elaborar informe de resultados" |
| `finalizado` / `cancelado` | `RecordProcessResult`, al terminar la instancia |

**Transacción.** Los dos listeners escriben con la conexión del motor, porque Flowable y las tablas de la institución comparten la base. El cambio de estado se confirma o se deshace junto con el paso del proceso: si la instancia no logra avanzar, no queda ningún estado cambiado.

## 7. Errores de negocio

Las validaciones de negocio lanzan `FlowableIllegalArgumentException`. La API REST de Flowable la devuelve como 400 con el mensaje, y la web lo muestra tal cual. Cualquier otra excepción (un bug, la base caída) llega como 500 y la web muestra un mensaje genérico. Siguen esta regla:

- `ValidateProposalDates`: presupuesto y fechas de la propuesta.
- `ValidateResourceSelection`: al menos un recurso elegido.
- `CreateCourseOnAcceptance`: fecha de inicio acordada.
- `ValidateSelectedTeacher`: docente obligatorio y del grupo `Docentes`.

En todos los casos la tarea no se completa y se puede corregir y reintentar.

## 8. Unión asíncrona de "Preparar curso"

La unión de las ramas de "Preparar curso" es asíncrona (`flowable:async="true"`). Completar la última rama solo deja un job, y el resto del proceso lo ejecuta Flowable UI. Así la aplicación de materiales, que usa la API Java con su propio motor y sin las clases del proyecto, puede completar "Preparar materiales" aunque sea la última rama.

## 9. Backend del acceso de las empresas

El backend Spring Boot expone la API que usa la web de empresas:

| Ruta | Qué hace |
|---|---|
| `POST /api/empresa/sesion` | Inicia sesión con usuario y clave (400 sin datos, 401 si no coinciden) |
| `GET` / `DELETE /api/empresa/sesion` | Consulta o cierra la sesión |
| `GET /api/empresa/cursos` | Cursos de la empresa, del más nuevo al más viejo |
| `GET /api/empresa/cursos/{id}` | Detalle del curso con sus actividades pendientes (404 si es de otra empresa) |

Decisiones:

- **Sesión separada de la del personal.** Con una sesión de empresa no se puede usar la API del personal, y al revés (401). La empresa sale siempre de la sesión, nunca del pedido, así una empresa no puede consultar cursos ajenos.
- **Actividades pendientes.** Salen del historial de Flowable: las actividades de la instancia que todavía no terminaron. Así aparecen las ramas paralelas de la preparación, la espera de la nómina y la del inicio del curso. Se omiten compuertas, eventos de inicio y fin y el subproceso.
- **Login sin pistas.** Un usuario inexistente se compara contra un hash fijo, para que la respuesta tarde lo mismo que con una clave incorrecta y no revele qué usuarios existen.

## 10. Pruebas y demostración

- **Tests automáticos:** JUnit con motor H2 en memoria sobre el BPMN real. Recorren los cinco cierres, la viabilidad, la asignación, las aulas, la cuenta de empresa y los estados.
- **`make recorrido`:** script que recorre el proceso de punta a punta por la API (final, cancelación, rechazo, falta de respuesta), completando tareas y mandando los mensajes de la empresa. Sirve para la demostración y para generar ejecuciones con cierres distintos para la minería.
- **Escenario 5:** `make recorrido ESCENARIO=limite` completa hasta 5 cursos en ejecución y muestra que la solicitud siguiente termina como no viable. `ESCENARIO=limpiar-limite` cancela después esos cursos.
