En este documento se desarrolla el paso a paso de la realización de las actividades del modelo BPMN.

# Mensajes, timers y variables del proceso

Estos nombres son los que usan el sitio web y el simulador de comunicaciones para actuar sobre las instancias. Si se cambia alguno en el BPMN, hay que avisar al grupo y actualizar esta sección.

Son los valores mínimos que hicieron falta para que Flowable acepte desplegar el modelo (antes rechazaba el despliegue con 15 errores de validación). Las tareas automáticas todavía son simulaciones: escriben en el log de Flowable (`make dev-logs`) y, cuando corresponde, guardan variables.

## Mensajes

Todos los mensajes usan el mismo valor como id y como name.

| Mensaje | Evento | Quién lo envía |
|---|---|---|
| `msgSolicitud` | Inicio "Solicitud recibida" | Sitio web, al enviar el formulario de solicitud |
| `msgAceptacion` | Aceptación (gateway "Esperar respuesta") | Simulador. Tiene que enviar `fechaInicio` |
| `msgRechazo` | Rechazo (gateway "Esperar respuesta") | Simulador |
| `msgModificaciones` | Modificaciones (gateway "Esperar respuesta") | Simulador, con las observaciones |
| `msgCancelacion` | Inicio del subproceso de evento "Cancelación", dentro de "Preparar curso" | Simulador |

Por la API REST:

```bash
# Iniciar una solicitud (lo que hace el sitio web)
POST /flowable-ui/process-api/runtime/process-instances
{"message": "msgSolicitud", "variables": [...]}

# Enviar un mensaje a una instancia en curso (lo que hace el simulador):
# 1. buscar la ejecución que espera el mensaje
GET /flowable-ui/process-api/runtime/executions?processInstanceId=<id>&messageEventSubscriptionName=msgAceptacion
# 2. enviarle el mensaje con sus variables
PUT /flowable-ui/process-api/runtime/executions/<executionId>
{"action": "messageEventReceived", "messageName": "msgAceptacion", "variables": [{"name": "fechaInicio", "value": "2026-11-20T09:00:00"}]}
```

## Timers

Los plazos usan variables y no valores fijos en el modelo, como pide el enunciado (3.5): así se pueden acortar para las pruebas sin modificar el BPMN.

| Timer | Configuración | Variable |
|---|---|---|
| "5 días" (espera de respuesta) | Time duration `${plazoRecordatorio}` | Duración ISO-8601. Si no viene al iniciar, "Enviar propuesta" la define en `PT5D`. Para probar se puede iniciar el proceso con `PT1M`. |
| "Fecha programada" (inicio del curso) | Time date `${fechaInicio}` | Fecha ISO-8601, por ejemplo `2026-11-20T09:00:00`. La envía el simulador con `msgAceptacion`, así que está definida antes de llegar al timer. |

## Tareas automáticas

Los scripts están en Groovy, que viene incluido en Flowable UI. No usan `${...}` dentro del script: concatenan con `+`.

| Tarea | Tipo | Qué hace hoy |
|---|---|---|
| Notificar Decisión | Script | Log y `resultado = "no_viable"` |
| Enviar propuesta | Script | `recordatoriosEnviados = 0` (el contador se reinicia con cada versión nueva de la propuesta, como pide el enunciado), define `plazoRecordatorio = "PT5D"` si no existe, y log |
| Enviar recordatorio | Script | `recordatoriosEnviados + 1` y log |
| Habilitar acceso de la empresa | Script | Log. Falta crear o reutilizar la cuenta de la empresa |
| Enviar confirmación | Script | Log |
| Notificar Cierre | Script | Log y `resultado = "cancelado"` |
| Liberar Aula Virtual | Script (compensación) | Log |
| Reservar aula | Service task (expresión) | `aulaAsignada = "Aula 1"`. Falta la clase Java que simule el web service y lance el error por falta de aulas (escenario 7) |
| Liberar Aula | Service task (expresión, compensación) | Elimina `aulaAsignada` |

## Variables

| Variable | Tipo | La define | La usa |
|---|---|---|---|
| `isViable` | boolean | Tarea "Evaluar Viabilidad" (ver sección de viabilidad) | Gateway "Es viable?" |
| `plazoRecordatorio` | String (duración ISO-8601) | Inicio del proceso o "Enviar propuesta" | Timer "5 días" |
| `recordatoriosEnviados` | int | "Enviar propuesta" (0) y "Enviar recordatorio" (+1) | Gateway "¿Se enviaron 2 recordatorios?": Sí `${recordatoriosEnviados >= 2}`, No `${recordatoriosEnviados < 2}` |
| `fechaInicio` | String (fecha ISO-8601) | Simulador, con `msgAceptacion` | Timer "Fecha programada" |
| `aulaAsignada` | String | "Reservar aula" | "Liberar Aula" |
| `resultado` | String | Cada camino de cierre | Execution Listener que actualiza `cursos.estado` |

Valores de `resultado`, iguales a los del `CHECK` de `cursos.estado`: `no_viable` y `cancelado` (ya definidos); `rechazado`, `cerrado_por_falta_de_respuesta` y `finalizado` (pendientes).

## Pendiente para que el proceso corra completo

Estos puntos no impiden desplegar, pero sí que el proceso recorra todos los caminos:

- Condiciones del gateway "Recursos necesarios" (por ejemplo, `${aulaFisica}`, `${aulaVirtual}`, `${equipamiento}`).
- `resultado` en los caminos que terminan en tareas de usuario: rechazo, sin respuesta y finalización normal.
- Asignaciones y grupos candidatos de cada tarea de usuario.
- La clase Java de "Reservar aula" con el error por falta de aulas.

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
2. Consulta al `RuntimeService` cuántas instancias activas hay para esa key.
3. Resta 1 para descontar la instancia actual, es decir, la solicitud que se está evaluando.
4. Guarda el resultado en la variable local `amountCursos`, como una instancia de `AmountCursos`.

En pseudocódigo:

```
totalActivas      = count(instancias activas de process-proyecto-curso)
otrasInstancias   = totalActivas - 1
execution.setVariableLocal("amountCursos", new AmountCursos(otrasInstancias))
```

La cuenta **incluye** la instancia actual, por eso se descuenta. Así, `amountInstancesActives` representa los cursos que ya estaban activos antes de evaluar esta solicitud.

La variable se guarda con `setVariableLocal`, por lo que `amountCursos` queda acotada a la ejecución de la tarea y no se propaga al resto de la instancia.

### 4.2. Reglas Drools

El archivo `rules/viability.drl` contiene dos reglas mutuamente excluyentes:

| Regla | Condición | Acción |
|---|---|---|
| `Is Viable` | `amountInstancesActives < 5` | `insert(Boolean.TRUE)` |
| `Is Not Viable` | `amountInstancesActives >= 5` | `insert(Boolean.FALSE)` |

La tarea `Evaluar Viabilidad` está configurada con:

- `flowable:ruleVariablesInput="amountCursos"`: inserta el fact en la sesión Drools.
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
  -> amountCursos (local) = N - 1
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
| `amountInstancesActives` | `long` | Cantidad de instancias activas del proceso, sin contar la actual. |

> **Nota de nombre:** el campo se llama `amountInstancesActives` (con `s` final) y así se usa en el DRL. Si se renombra, hay que actualizar la clase y las reglas al mismo tiempo.

`AmountCursos` implementa `java.io.Serializable` con `serialVersionUID = 1L`. Es obligatorio: Flowable serializa tanto la variable local `amountCursos` como la colección que guarda `resultVariable`.

## 6. Regla de decisión

Con `N` = instancias activas totales, incluida la solicitud actual:

| Instancias activas totales (`N`) | `amountInstancesActives` (`N - 1`) | Resultado | Camino |
|---|---|---|---|
| 1 a 5 | 0 a 4 | Viable | Sí |
| 6 o más | 5 o más | No viable | No |

En otras palabras, se permiten hasta **5 instancias activas simultáneas**. La sexta solicitud se rechaza.

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

4. Dejar instancias activas y volver a iniciar solicitudes. Mientras haya 4 o menos instancias previas, el camino es **Sí**.

5. Con 5 instancias previas activas, la siguiente debe tomar el camino **No** y terminar en `Notificar Decisión`.

Para inspeccionar la decisión se puede consultar la variable `isViable` de la instancia desde Flowable UI o la API. La colección intermedia se sobrescribe al terminar la tarea, así que el valor observable es el booleano final.

## 9. Estado actual y ajustes pendientes

La lógica de la sección está completa, pero la implementación todavía no es ejecutable de punta a punta. Antes de dar por cerrado el contrato hay que resolver:

- **El DRL no se despliega junto al BPMN.** `ModelerImporter` solo importa el BPMN al Modeler. Si el deployment no contiene `rules/viability.drl`, Flowable no encuentra el `KieBase` y falla con `deployment ... doesn't contain any rules`.
- **No hay tests JUnit** que cubran el camino viable y el no viable.