En este documento se desarrolla el paso a paso de la realización de las actividades del modelo BPMN.

# Mensajes, timers y variables del proceso

Estos nombres son los que usan el sitio web y el simulador de comunicaciones para actuar sobre las instancias. Si se cambia alguno en el BPMN, hay que avisar al grupo y actualizar esta sección.

Para iniciar una solicitud desde la web con Spring Boot, mirá la [guía de uso del backend](./uso-web-springboot.md).

Son los valores mínimos que hicieron falta para que Flowable acepte desplegar el modelo (antes rechazaba el despliegue con 15 errores de validación). Las tareas automáticas todavía son simulaciones: escriben en el log de Flowable (`make dev-logs`) y, cuando corresponde, guardan variables.

## Mensajes

Todos los mensajes usan el mismo valor como id y como name.

| Mensaje | Evento | Quién lo envía |
|---|---|---|
| `msgSolicitud` | Inicio "Solicitud recibida" | Sitio web, al enviar el formulario de solicitud |
| `msgAceptacion` | Aceptación (gateway "Esperar respuesta") | Simulador externo. Tiene que enviar `fechaInicio` |
| `msgRechazo` | Rechazo (gateway "Esperar respuesta") | Simulador externo |
| `msgModificaciones` | Modificaciones (gateway "Esperar respuesta") | Simulador externo. Envía `observaciones` |
| `msgCancelacion` | Inicio del subproceso de evento "Cancelación", dentro de "Preparar curso" | Simulador |

La aplicación web usa el backend local (`http://127.0.0.1:8081`) únicamente para iniciar solicitudes. El backend se configura desde el POM raíz con el perfil Maven `backend`; el perfil predeterminado `flowable-engine` conserva por separado las fuentes y dependencias del motor. El backend se comunica con Flowable y no expone las credenciales del motor al navegador. Su única ruta es:

| Operación | Ruta | Datos requeridos |
|---|---|---|
| Iniciar solicitud | `POST /api/solicitudes` | `empresaNombre`, `contactoNombre`, `contactoEmail`, `tematica`, `descripcion`, `participantesEstimados`, `fechasPreferidas`; `contactoTelefono` y `plazoRecordatorio` opcionales |

El backend busca la empresa por nombre normalizado y la crea si no existe. Guarda los datos del formulario en `solicitudes` antes de llamar a Flowable; después registra allí `business_key` e `instancia_flowable_id`. También envía `solicitudId` y el ID interno de la empresa como variables; ninguno lo aporta el cliente. `plazoRecordatorio`, si se envía, debe ser una duración ISO-8601 positiva; si no, el BPMN asigna `P5D` (cinco días). Para pruebas puede enviarse `PT1M`. Cuando la empresa acepta, “Habilitar acceso de la empresa” crea el curso y guarda su ID en `solicitudes.curso_id` (variable de proceso `cursoId`).

Las variables del mensaje `msgSolicitud` son `solicitudId`, `empresaId`, `empresaNombre`, `tematica`, `descripcion`, `contactoNombre`, `contactoEmail`, `participantesEstimados`, `fechasPreferidas` y, si se informaron, `contactoTelefono` y `plazoRecordatorio`.

El backend devuelve `503` si no puede guardar en PostgreSQL. Un rechazo HTTP 4xx de Flowable devuelve `502` y elimina la fila de `solicitudes`, pero conserva la empresa. Ante timeout o 5xx, el backend consulta Flowable por `businessKey`; si encuentra la instancia, persiste el vínculo y devuelve `201`. Si no puede confirmarla, conserva la fila pendiente y devuelve `502` con `businessKey`, `estado: "PENDIENTE"` y el ID de instancia si se llegó a conocer.

El backend llama por REST a Flowable con estas operaciones:

```bash
# Iniciar una solicitud con el evento msgSolicitud
POST /flowable-ui/process-api/runtime/process-instances
{"message": "msgSolicitud", "businessKey": "solicitud-...", "variables": [...]}

```

La llamada REST directa es interna del backend; el navegador debe usar `/api/solicitudes`, no las credenciales/API de Flowable. Las respuestas posteriores se envían por un simulador externo, no por este backend.

## Timers

Los plazos usan variables y no valores fijos en el modelo, como pide el enunciado (3.5): así se pueden acortar para las pruebas sin modificar el BPMN.

| Timer | Configuración | Variable |
|---|---|---|
| "5 días" (espera de respuesta) | Time duration `${plazoRecordatorio}` | Duración ISO-8601. El script "Enviar propuesta" asigna `P5D` si la variable todavía no existe. El endpoint web permite sobrescribirla para pruebas. |
| "Fecha programada" (inicio del curso) | Time date `${fechaInicio}` | Fecha ISO-8601, por ejemplo `2026-11-20T09:00:00`. La envía el simulador con `msgAceptacion`, así que está definida antes de llegar al timer. |

## Tareas automáticas

Los scripts están en Groovy, que viene incluido en Flowable UI. No usan `${...}` dentro del script: concatenan con `+`.

| Tarea | Tipo | Qué hace hoy |
|---|---|---|
| Notificar Decisión | Script | Log y `resultado = "no_viable"` |
| Enviar propuesta | Script | Simula el envío registrando en el log el programa, presupuesto y fechas de la propuesta; reinicia `recordatoriosEnviados = 0` y define `plazoRecordatorio = "P5D"` si no existe |
| Enviar recordatorio | Script | `recordatoriosEnviados + 1` y log |
| Habilitar acceso de la empresa | Java (`flowable.proposal.CreateCourseOnAcceptance`) | Crea el curso con nombre y descripción de la solicitud, programa y presupuesto de la propuesta, `fechaInicio` acordada por la empresa y fecha de finalización propuesta; asigna su ID a `solicitudes.curso_id`. Si la `fechaInicio` acordada es posterior a `fechaFinPropuesta`, falla sin crear el curso. Es idempotente: un reintento no duplica el curso. Usa `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` en el motor de Flowable |
| Enviar confirmación | Script | Log |
| Notificar Cierre | Script | Log y `resultado = "cancelado"` |
| Liberar Aula Virtual | Script (compensación) | Log |
| Reservar aula | Service task (`flowable.classroom.ReserveClassroom`) | Simula el web service de aulas: asigna la primera aula libre en `aulaAsignada` o lanza el error `SIN_AULAS` (escenario 7). Ver "Reservar y liberar aula" |
| Liberar Aula | Service task (`flowable.classroom.ReleaseClassroom`, compensación) | Simula la liberación en el web service y elimina `aulaAsignada` |

## Reservar y liberar aula

El web service de aulas está simulado (enunciado 4.1.3) y deja el log `[WS Aulas]`.

- **Aulas:** hay dos, `Aula 1` y `Aula 2`.
- **Cuándo un aula está ocupada:** mientras alguna instancia activa la tenga en `aulaAsignada`.
- **Reservar aula** asigna la primera libre. Si no queda ninguna, lanza `BpmnError("SIN_AULAS")`.
- **Escenario 7:** con dos cursos que ya reservaron aula, el tercero que llega a "Reservar aula" se queda sin aula.
- **Liberar Aula** elimina `aulaAsignada`, con lo que el aula vuelve a quedar libre.

Cambios en el BPMN:

```xml
<!-- dentro de <definitions>, junto a los <message> -->
<error id="errSinAulas" errorCode="SIN_AULAS"/>

<serviceTask id="sid-065649F8-9F01-4C34-B290-C37474CC479D" name="Reservar aula" flowable:class="flowable.classroom.ReserveClassroom"/>
<serviceTask id="sid-CF527638-CD0A-48BF-9EF6-DDB557838793" name="Liberar Aula" isForCompensation="true" flowable:class="flowable.classroom.ReleaseClassroom"/>
```

Flowable 6.8 vincula el boundary de compensación con su tarea mediante una `association`, no mediante un `sequenceFlow`. Con `sequenceFlow`, la instancia falla al llegar a la tarea con el error `Parent activity for boundary compensation event could not be found`. Lo reproduje en un test aislado.

Hay que reemplazar los tres `sequenceFlow` que salen de los boundaries "Liberar recursos" por associations dentro del `<subProcess>`:

```xml
<association id="assocLiberarAula" sourceRef="sid-6EF58547-6206-4065-BC46-BDE7A86300E7" targetRef="sid-CF527638-CD0A-48BF-9EF6-DDB557838793" associationDirection="One"/>
<association id="assocLiberarAulaVirtual" sourceRef="sid-79D40E03-33A5-476E-AC89-3F44F73680A0" targetRef="sid-892710F9-B2AE-4002-82D0-131829D8FA29" associationDirection="One"/>
<association id="assocLiberarEquipamiento" sourceRef="sid-0FB02D1D-DFB1-4222-BA0E-6D58C0E87619" targetRef="sid-3DEB48C5-A36A-4EBF-A77C-150D02FC969E" associationDirection="One"/>
```

**Manejo de `SIN_AULAS`:** el boundary de error del subproceso "Preparar curso" no tiene `errorRef`, así que hoy ya atrapa cualquier error. Eso incluye `SIN_AULAS`, que termina en "Notificar Cierre" con `resultado = "cancelado"`. Si se agrega un boundary de error propio sobre "Reservar aula", tiene que declararse antes que el boundary de compensación. En el orden inverso, Flowable 6.8 falla con el mismo error de arriba.

## Registrar y elaborar propuesta

La tarea de usuario `Registrar y Elaborar propuesta` se asigna exclusivamente al usuario `jefeAdministrativo`. Su formulario requiere estos campos:

| Campo | Tipo | Uso |
|---|---|---|
| `programa` | string | Programa del curso incluido en la propuesta |
| `presupuesto` | long | Presupuesto propuesto |
| `fechaInicioPosible` | date | Fecha posible de inicio |
| `fechaFinPropuesta` | date | Fecha posible de finalización |

Al completar la tarea, el motor valida que `presupuesto` sea mayor o igual a cero y que `fechaFinPropuesta` sea posterior o igual a `fechaInicioPosible`; si alguna regla no se cumple, la tarea no se completa. Si la validación pasa, el flujo continúa automáticamente a `Enviar propuesta` y luego espera la respuesta de la empresa. El envío está simulado en el log de Flowable; todavía no integra correo u otro canal externo. Las fechas propuestas no son la fecha confirmada de inicio: `fechaInicio` sigue siendo la fecha que la empresa informa al aceptar la propuesta.

## Confeccionar lista definitiva

Al crearse la tarea, el Task Listener `flowable.assignment.AssignToLeastBusyAdministrative` (enunciado 3.4) la asigna a un integrante del grupo `Administracion` con menos tareas de usuario pendientes asignadas en el motor. Si varios integrantes comparten la menor carga, el listener elige uno al azar entre ellos.

- **Qué cuenta:** solo las tareas activas con `assignee` igual a ese usuario. Las que tienen al grupo como candidato y nadie reclamó no cuentan, porque no tienen `assignee`.
- **Empate:** se elige aleatoriamente entre los integrantes empatados en la menor cantidad de tareas asignadas. El desempate es una estrategia que recibe los empatados en orden alfabético; Flowable usa la aleatoria y los tests pueden inyectar una fija por constructor (ver `AssignToLeastBusyAdministrativeTest`).
- **Sin integrantes en el grupo:** la tarea queda sin assignee y conserva `Administracion` como grupo candidato. Cuando haya integrantes disponibles, podrán encontrar y reclamar esa tarea.

En el BPMN, la tarea queda así (el grupo candidato es el respaldo si el listener no asigna a nadie):

```xml
<userTask id="..." name="Confeccionar lista definitiva" flowable:candidateGroups="Administracion">
  <extensionElements>
    <flowable:taskListener event="create" class="flowable.assignment.AssignToLeastBusyAdministrative"/>
  </extensionElements>
</userTask>
```

## Tareas del curso disponibles para Administración

Las siguientes tareas tienen al grupo `Administracion` como grupo candidato en Flowable:

- `Confeccionar lista definitiva` (además, se asigna automáticamente a un miembro menos ocupado).
- `Prepara materiales`.
- `Evaluar Recursos`.
- `Solicitar Aula Virtual`.
- `Contratar equipamiento`.
- `Liberar Equipamiento` (tarea de compensación).

Las tareas sin asignación explícita permanecen sin responsable/grupo candidato configurado hasta que se definan.

## Variables

| Variable | Tipo | La define | La usa |
|---|---|---|---|
| `isViable` | boolean | Tarea "Evaluar Viabilidad" (ver sección de viabilidad) | Gateway "Es viable?" |
| `programa` | string | Formulario "Registrar y Elaborar propuesta" | Propuesta enviada a la empresa |
| `presupuesto` | long | Formulario "Registrar y Elaborar propuesta" | Propuesta enviada a la empresa y validación no negativa |
| `fechaInicioPosible` | date | Formulario "Registrar y Elaborar propuesta" | Propuesta enviada a la empresa y validación de fechas |
| `fechaFinPropuesta` | date | Formulario "Registrar y Elaborar propuesta" | Propuesta enviada a la empresa |
| `plazoRecordatorio` | String (duración ISO-8601) | "Enviar propuesta" (`P5D`, si no existe) | Timer "5 días" |
| `recordatoriosEnviados` | int | "Enviar propuesta" (0) y "Enviar recordatorio" (+1) | Gateway "¿Se enviaron 2 recordatorios?": Sí `${recordatoriosEnviados >= 2}`, No `${recordatoriosEnviados < 2}` |
| `fechaInicio` | String (fecha ISO-8601) | Simulador, con `msgAceptacion` | Timer "Fecha programada" y `cursos.fecha_inicio` |
| `observaciones` | String | Simulador, con `msgModificaciones` | Solo informativa: queda guardada en la instancia |
| `participantes` | String (nombres separados por coma) | Simulador, al señalar "Recibir nómina" | Tarea "Confeccionar lista definitiva" |
| `docenteSeleccionado` | String (ID de usuario del grupo `Docentes`) | Formulario o variables al completar "Definir Docente que desarrolla el curso" | Assignee de "Desarrollar curso" |
| `aulaAsignada` | String | "Reservar aula" | "Liberar Aula" |
| `resultado` | String | Cada camino de cierre | `RecordProcessResult` (ver "Resultado del proceso") |

## Definir el docente del curso

La tarea `Definir Docente que desarrolla el curso` está asignada a `jefeDeEquipo`. El jefe ingresa en el campo obligatorio `docenteSeleccionado` el ID exacto del usuario elegido y completa la tarea. Es un campo de texto, no una lista de docentes. El jefe también puede elegirse a sí mismo si pertenece al grupo `Docentes`; no necesita delegar la tarea.

**Contrato para la web:** al completar «Definir docente», debe enviarse la variable de proceso `docenteSeleccionado` como string, con el ID de un usuario perteneciente al grupo `Docentes` (por ejemplo, `docente2`). El backend web actual solo inicia solicitudes; todavía no expone una ruta para completar esta tarea.

Al completar la tarea, el listener `flowable.assignment.ValidateSelectedTeacher` consulta la identidad de Flowable y exige que el ID corresponda a un usuario del grupo **`Docentes`**. La validación también se ejecuta cuando una aplicación completa la tarea pasando la variable de proceso como string:

```java
taskService.complete(taskId, Map.of("docenteSeleccionado", "docente2"));
```

Si el ID falta, está vacío, no es un string, no existe o no pertenece a `Docentes`, se informa un error y la tarea permanece pendiente. El jefe debe corregir el valor antes de completarla. La pertenencia se verifica al completar la definición; no se vuelve a verificar si cambia el grupo del usuario después.

Una vez completada, el proceso espera el timer `Fecha programada` y asigna `Desarrollar curso` al usuario guardado en `docenteSeleccionado`.


## Asignaciones directas de tareas

Las siguientes tareas tienen responsables fijos en el modelo BPMN:

| Tarea | Responsable |
|---|---|
| Revisar propuesta | `jefeAdministrativo` |
| Archivar solicitud | `jefeAdministrativo` |
| Enviar informe a empresa y Archivar expediente | `jefeAdministrativo` |
| Definir Docente que desarrolla el curso | `jefeDeEquipo` |
| Elaborar informe de resultados | `jefeDeEquipo` |

Valores de `resultado`, iguales a los del `CHECK` de `cursos.estado`: `no_viable` y `cancelado` (ya definidos); `rechazado`, `cerrado_por_falta_de_respuesta` y `finalizado` (pendientes).

## Resultado del proceso

El Execution Listener `flowable.result.RecordProcessResult` (enunciado 3.4) corre al terminar la instancia y registra el valor de `resultado` en la base de la institución:

- **Siempre** en `solicitudes.resultado` (migración `003`), usando `solicitudId`. Rechazo, sin respuesta y no viable terminan antes de "Habilitar acceso", así que no tienen curso: la solicitud es lo único que pueden actualizar.
- **Si existe el curso** (variable `cursoId`), también en `cursos.estado`.
- **Sin `resultado`:** deja un warning en el log y la instancia termina igual. Es un camino de cierre al que le falta definir la variable.
- **Con un valor desconocido:** lanza una excepción y la instancia no termina (es un error del modelo).

Usa la conexión de la transacción del motor, porque Flowable y las tablas de la institución están en la misma base. El UPDATE se ejecuta justo antes del commit, así el estado y el cierre de la instancia se confirman o se deshacen juntos. Probado en Flowable UI: si la instancia no logra terminar, no queda ningún estado cambiado.

Para que corra hay que aplicar la migración (`make db-migrate`) y engancharlo a nivel proceso, dentro de `<process>` y antes del primer elemento:

```xml
<process id="process-proyecto-curso" name="Proyecto Cursos" isExecutable="true">
  <extensionElements>
    <flowable:executionListener event="end" class="flowable.result.RecordProcessResult"></flowable:executionListener>
  </extensionElements>
  ...
```

En el Modeler se configura en las propiedades del proceso (clic en un lugar vacío del lienzo), en "Execution listeners".

## Errores de negocio y errores internos

Cuando una clase rechaza un dato por una regla de negocio, lanza `FlowableIllegalArgumentException`. La API REST de Flowable la devuelve como **400** y el texto de `exception` se puede mostrar tal cual al usuario. Cualquier otra excepción (un bug, una base caída, un dato faltante que debería haber puesto el proceso) llega como **500**, y la web muestra un mensaje genérico.

| Clase | Regla que devuelve 400 |
|---|---|
| `flowable.proposal.ValidateProposalDates` | Presupuesto obligatorio y no negativo; fechas obligatorias; fin posterior o igual al inicio |
| `flowable.resources.ValidateResourceSelection` | Al menos un recurso elegido |
| `flowable.proposal.CreateCourseOnAcceptance` | `fechaInicio` acordada no posterior a `fechaFinPropuesta` |

Una clase nueva con validaciones de negocio debe seguir la misma regla. El backend reenvía el status que recibe de Flowable.

## Pendiente para que el proceso corra completo

Estos puntos no impiden desplegar, pero sí que el proceso recorra todos los caminos:

- `resultado` en los caminos que terminan en tareas de usuario: rechazo, sin respuesta y finalización normal.
- Enganchar `RecordProcessResult` a nivel proceso (ver "Resultado del proceso").
- Asignaciones y grupos candidatos de las demás tareas de usuario.
- Enganchar en el BPMN las clases de "Reservar aula" y "Liberar Aula", y vincular las compensaciones con `association` (ver "Reservar y liberar aula").

## Evaluación de recursos

La tarea **Evaluar Recursos** registra los recursos requeridos mediante tres campos booleanos:

| Variable | Rama del gateway |
|---|---|
| `aulaFisica` | Presencial: reservar aula |
| `aulaVirtual` | Virtual: solicitar aula virtual |
| `equipamiento` | Equipamiento especial: contratar equipamiento |

El gateway es inclusivo, por lo que puede activar varias ramas. La tarea no se puede completar si ninguna opción está marcada; cada opción individual es opcional. Si al completar no se envía alguna de las tres variables, `ValidateResourceSelection` la guarda en `false`, porque el gateway falla al evaluar una condición sobre una variable que no existe.

`src/test/java/flowable/resources/ResourcesGatewayModelTest.java` lo prueba sobre el BPMN real: una sola opción activa solo su rama, dos opciones activan las dos, las variables ausentes cuentan como `false` y sin ninguna opción la tarea se rechaza y sigue activa.

# Sección de viabilidad

Este documento describe la sección de **viabilidad** del proceso `Proyecto Cursos`, implementada en la rama `vm/evaluate-viability`. Su objetivo es fijar los IDs, las variables y el comportamiento esperado para que el resto de los componentes puedan integrarse sin depender de la implementación interna.

## 1. Ubicación en el proceso

| Elemento | Valor |
|---|---|
| Key del proceso | `process-proyecto-curso` |
| Nombre del proceso | `Proyecto Cursos` |
| Nombre de la tarea | `Evaluar Viabilidad` |
| Tipo | `businessRuleTask` |
| Nombre del gateway | `Es viable?` |
| Destino "Sí" | `Registrar y Elaborar propuesta` (`sid-F96A6968-13D6-4EA4-929E-69230127C208`) |
| Destino "No" | `Notificar Decisión` (`sid-D4A460A6-6451-48B2-BD5D-F76FDEC3887F`) |

El inicio del proceso es un evento de mensaje que lleva directo a la tarea de viabilidad.

## 2. Flujo de la sección

```
Inicio (mensaje)
      |
      v
Evaluar Viabilidad  (businessRuleTask + Drools)
      |
      v
  Es viable?
   /       \
 Sí         No
  |          |
  v          v
Registrar   Notificar
y Elaborar  Decisión
propuesta      |
               v
             Fin
```

Si el curso es viable, el proceso continúa con la elaboración de la propuesta. Si no lo es, se notifica la decisión y la instancia termina.

## 3. Qué se hizo en la rama vm/evaluate-viability

La rama agrega cinco piezas:

| Archivo | Responsabilidad |
|---|---|
| `src/main/java/flowable/viability/AmountCursos.java` | Objeto (fact) serializable que transporta la cantidad de instancias activas hacia Drools. |
| `src/main/java/flowable/viability/EvaluateViability.java` | `ExecutionListener` de inicio que cuenta las instancias activas y publica la variable local `amountCursos`. |
| `src/main/resources/rules/viability.drl` | Reglas Drools que insertan `Boolean.TRUE` o `Boolean.FALSE` según la cantidad de instancias activas. |
| `src/main/java/flowable/viability/ViabilityResult.java` | `ExecutionListener` de fin que normaliza la salida de Drools a un booleano en la variable `isViable`. |
| `src/main/resources/processes/ProyectoCursos.bpmn20.xml` | Conecta la tarea de reglas, ambos listeners y las condiciones del gateway. |

## 4. Cómo funciona

### 4.1. Listener `EvaluateViability` (inicio)

El listener se ejecuta al **iniciar** la tarea `Evaluar Viabilidad` (evento `start`). Sus pasos son:

1. Obtiene la key del proceso a partir del `processDefinitionId`, quitando la versión y el ID interno (`process-proyecto-curso:1:...` → `process-proyecto-curso`).
2. Consulta al `RuntimeService` cuántas instancias activas de esa key tienen la variable `cursoId`, es decir, ya pasaron por "Habilitar acceso de la empresa".
3. Guarda el resultado en la variable local `amountCursos`, como una instancia de `AmountCursos`.

En pseudocódigo:

```
cursosEnEjecucion = count(instancias activas de process-proyecto-curso con la variable cursoId)
execution.setVariableLocal("amountCursos", new AmountCursos(cursosEnEjecucion))
```

**Qué cuenta como curso en ejecución.** El enunciado (3.1) habla de las instancias que no finalizaron. El profesor aclaró que una solicitud cuenta recién cuando la empresa acepta: mientras está en evaluación o negociación no ocupa lugar. El punto de corte es "Habilitar acceso de la empresa", que crea el curso y guarda `cursoId` (`CreateCourseOnAcceptance`).

La instancia que se está evaluando queda afuera sola, porque todavía no tiene `cursoId`; no hace falta filtrar por ID. Las instancias terminadas (finalizadas, canceladas, rechazadas) tampoco cuentan, porque dejan de estar activas. Se consideran todas las versiones desplegadas del proceso, porque la consulta es por key.

La variable se guarda con `setVariableLocal`, por lo que `amountCursos` queda acotada a la ejecución de la tarea y no se propaga al resto de la instancia.

### 4.2. Reglas Drools

El archivo `rules/viability.drl` contiene dos reglas mutuamente excluyentes:

| Regla | Condición | Acción |
|---|---|---|
| `Is Viable` | `amountInstancesActives < 5` | `insert(Boolean.TRUE)` |
| `Is Not Viable` | `amountInstancesActives >= 5` | `insert(Boolean.FALSE)` |

La tarea `Evaluar Viabilidad` está configurada con:

- `flowable:ruleVariablesInput="${amountCursos}"`: inserta el fact en la sesión Drools. Tiene que ser una expresión: sin `${}` Flowable inserta el texto `"amountCursos"`, ninguna regla se activa y todas las solicitudes salen como no viables.
- `flowable:rules="Is Viable,Is Not Viable"`: filtra por nombre de regla.
- `flowable:resultVariable="isViable"`: guarda la salida de Drools.

Las reglas no modifican el fact: insertan un booleano en la memoria de trabajo. El `.drl` debe formar parte del mismo deployment que el BPMN; Flowable lo carga desde los recursos del deployment.

### 4.3. Listener `ViabilityResult` (fin)

Flowable guarda en `isViable` el resultado de la tarea como una **colección** con todos los objetos de la sesión Drools (el fact `AmountCursos` y el `Boolean` insertado). Para que el gateway reciba un booleano limpio, el listener de fin hace lo siguiente:

1. Lee `isViable` como `Collection<?>`.
2. Verifica si la colección contiene `Boolean.TRUE`.
3. Sobrescribe `isViable` con el booleano resultante.

En pseudocódigo:

```
Collection<?> salida = (Collection<?>) execution.getVariable("isViable")
boolean viable = salida != null && salida.contains(Boolean.TRUE)
execution.setVariable("isViable", viable)
```

### 4.4. Gateway `Es viable?`

El gateway exclusivo evalúa la variable booleana `isViable`:

- Flujo **Sí**: `${isViable}` es `true`.
- Flujo **No**: `${!isViable}` es `true`.

Como el gateway es exclusivo, solo se toma uno de los dos caminos.

### 4.5. Recorrido de las variables

```
EvaluateViability (start)
  -> amountCursos (local) = otras instancias activas
Drools
  -> insert(Boolean.TRUE / Boolean.FALSE)
resultVariable
  -> isViable = [AmountCursos, Boolean]
ViabilityResult (end)
  -> isViable = boolean
Gateway "Es viable?"
  -> ${isViable} / ${!isViable}
```

## 5. Contrato de variables

| Variable | Tipo | Alcance | La crea | La consume |
|---|---|---|---|---|
| `amountCursos` | `flowable.viability.AmountCursos` | Local a la tarea `Evaluar Viabilidad` | `EvaluateViability` | Reglas Drools |
| `isViable` (intermedia) | `Collection` | Instancia de proceso | `flowable:resultVariable` de la tarea de reglas | `ViabilityResult` |
| `isViable` (final) | `boolean` | Instancia de proceso | `ViabilityResult` | Gateway `Es viable?` |

La variable pública del contrato es `isViable` como booleano. `amountCursos` y la colección intermedia son detalles de implementación: no conviene que otros componentes dependan de ellos.

### Campos de `AmountCursos`

| Campo | Tipo | Descripción |
|---|---|---|
| `amountInstancesActives` | `long` | Cantidad de cursos en ejecución: instancias activas con `cursoId`. |

> **Nota de nombre:** el campo se llama `amountInstancesActives` (con `s` final) y así se usa en el DRL. Si se renombra, hay que actualizar la clase y las reglas al mismo tiempo.

`AmountCursos` implementa `java.io.Serializable` con `serialVersionUID = 1L`. Es obligatorio: Flowable serializa tanto la variable local `amountCursos` como la colección que guarda `resultVariable`.

## 6. Regla de decisión

Con `N` = cursos en ejecución (instancias activas que ya pasaron por "Habilitar acceso de la empresa"):

| Cursos en ejecución (`N`) | `amountInstancesActives` | Resultado | Camino |
|---|---|---|---|
| 0 a 4 | 0 a 4 | Viable | Sí |
| 5 o más | 5 o más | No viable | No |

En otras palabras, se permiten hasta **5 cursos en ejecución simultáneos**. Las solicitudes en evaluación o negociación no cuentan, así que puede haber más de 5 instancias activas.

El umbral está fijado en las reglas Drools. Para cambiarlo hay que modificar `viability.drl`, volver a compilar y redesplegar el recurso.

## 7. Cómo probarlo

1. Levantar el entorno y compilar:

   ```bash
   make dev-up
   mvn compile
   ```

2. Desplegar el BPMN **y** el DRL en el mismo deployment. Importar el modelo al Modeler no incluye el `.drl` y tampoco despliega. Un despliegue por API puede ser:

   ```java
   repositoryService.createDeployment()
       .addClasspathResource("processes/ProyectoCursos.bpmn20.xml")
       .addClasspathResource("rules/viability.drl")
       .deploy();
   ```

3. Iniciar una instancia del proceso. La primera solicitud debe tomar el camino **Sí** (`amountInstancesActives = 0`).

4. Iniciar 5 solicitudes y dejarlas en "Registrar y Elaborar propuesta": la siguiente sigue siendo **Sí**, porque no son cursos en ejecución.

5. Llevar esas 5 hasta "Habilitar acceso de la empresa" (completar la propuesta y enviar `msgAceptacion` con `fechaInicio` desde el simulador). La siguiente solicitud debe tomar el camino **No** y terminar en `Notificar Decisión`.

Para inspeccionar la decisión se puede consultar la variable `isViable` de la instancia desde Flowable UI o la API. La colección intermedia se sobrescribe al terminar la tarea, así que el valor observable es el booleano final.

## 9. Estado actual

La sección funciona de punta a punta en Flowable UI: `make deploy` despliega el BPMN y el `.drl` juntos, y `RulesConfigurator` registra el `RulesDeployer`. Drools 7 necesita `drools-mvel` y Java 11 o 17: usa `java.lang.Compiler`, que no existe desde Java 21.

### Tests

`src/test/java/flowable/viability/ViabilityTest.java` despliega en un motor H2 en memoria una copia de la sección (`src/test/resources/processes/viabilidad-test.bpmn20.xml`, con las mismas clases, reglas y condiciones) y cubre: primera solicitud viable, 4 cursos en ejecución viable, 5 cursos en ejecución no viable, solicitudes en evaluación que no cuentan e instancias finalizadas que no cuentan. Los tests simulan el paso por "Habilitar acceso" guardando `cursoId` en la instancia.

```bash
mvn test   # con Java 11 o 17
# sin JDK 11/17 instalado:
docker run --rm -v "$PWD":/app -v "$HOME/.m2":/root/.m2 -w /app maven:3.9-eclipse-temurin-11 mvn test
```