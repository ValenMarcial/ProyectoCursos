# Aplicaciones de escritorio, consultas de análisis y web del equipo docente

Esta sección describe la aplicación de preparación de materiales (4.2.1), el simulador de comunicaciones de la empresa (4.2.2), las consultas de análisis (4.3), las pantallas del equipo docente (1.4 y 4.1.4) y las ejecuciones generadas para el análisis. Cubre los escenarios 1, 2 y 11 de la defensa.

## 1. Preparación de materiales (API Java)

Aplicación de escritorio (Swing) en `desktop/materiales`. Usa la API Java de Flowable con un motor propio conectado a la misma base que Flowable UI. Es un proyecto Maven separado, así sus dependencias no se mezclan con las del motor.

Un integrante del grupo `Administracion` ingresa con su usuario y contraseña de Flowable (`IdentityService.checkPassword`). Ve las tareas *Preparar materiales* asignadas a él o disponibles para su grupo, y puede reclamarlas y completarlas. Un usuario de otro grupo no puede entrar.

Decisiones:

- **`asyncExecutorActivate=false`**, como pide el enunciado. La app solo consulta y completa tareas; los timers y los trabajos asíncronos los ejecuta Flowable UI. Si los dos motores ejecutaran trabajos sobre la misma base, se los disputarían.
- **`databaseSchemaUpdate=false`**. La app nunca crea ni modifica tablas.
- **Búsqueda de la tarea por su id en el BPMN** (`taskDefinitionKey`), no por el nombre, para que un renombre en el editor no la haga desaparecer.
- **Reglas de reclamo y fin.** Para reclamar, la tarea no tiene que tener responsable y el usuario tiene que pertenecer a un grupo candidato. Para completarla, la tarea tiene que ser del usuario.
- **Unión asíncrona.** Si *Preparar materiales* es la última rama de "Preparar curso", completarla haría que el motor de la app siguiera hasta "Enviar confirmación". Ese script Groovy no lo puede ejecutar, porque la app no trae Groovy ni las clases del proyecto. Por eso la unión "Preparación completa" es `flowable:async="true"`: la app solo completa la tarea y Flowable UI ejecuta el resto. Lo verifica un test.
- **`kie-api`.** El BPMN tiene una tarea de reglas (Drools). Para leer variables o completar tareas, el motor carga la definición completa y necesita las interfaces de KIE, aunque la app nunca ejecute esa tarea.

## 2. Simulador de comunicaciones de la empresa (API REST)

Aplicación de escritorio en `desktop/simulador`. Actúa sobre las instancias por la API REST de Flowable. El enunciado admite línea de comandos solo para grupos de hasta 3 integrantes, así que la interfaz principal es una ventana. La consola queda para pruebas rápidas.

| Comunicación | Cómo llega al proceso | Datos |
|---|---|---|
| Aceptación | Mensaje `msgAceptacion` | `fechaInicio` |
| Rechazo | Mensaje `msgRechazo` | — |
| Modificaciones | Mensaje `msgModificaciones` | `observaciones` |
| Nómina | Señal al receive task "Recibir nómina" | `participantes` |
| Cancelación | Mensaje `msgCancelacion` | — |

- **Solo comunicaciones habilitadas.** Antes de mostrar las opciones, el simulador consulta `GET /runtime/executions`. Solo deja enviar lo que el proceso espera en ese momento. La tabla de solicitudes muestra en qué etapa está cada una y a quién le toca actuar, la empresa o la institución.
- **Zona horaria.** Flowable interpreta una fecha sin zona como UTC. El simulador manda `fechaInicio` con la zona de la máquina, así la hora que se escribe es la que se programa. Para probar el timer sin esperar, sugiere una fecha dos minutos más adelante.
- **Cancelación.** Se volvió a probar con el BPMN actual. La instancia termina en "Notificar Cierre" con `resultado = cancelado` y solo se compensa lo que ya se había conseguido.

## 3. Consultas de análisis (4.3)

Están en la pestaña **Análisis** de la app de materiales y también se pueden correr por consola (`mvn -q compile exec:java -Dexec.args="analisis"`). Salen del historial de Flowable (`HistoryService`) y toman todas las versiones desplegadas del proceso.

- **4.3.1 Resultados:** cantidad y porcentaje de solicitudes terminadas para cada `resultado`.
- **4.3.2 Preparación paralela:** duración promedio de cada rama:
  - Inscripciones: desde "Recibir nómina" hasta el fin de "Confeccionar lista definitiva".
  - Materiales.
  - Recursos: desde "Evaluar Recursos" hasta la última acción elegida.

  Solo cuentan los cursos que completaron "Preparar curso", así que las preparaciones interrumpidas por una cancelación quedan afuera.
- **4.3.3 Tareas por persona:** tareas de usuario completadas por cada administrativo y cada docente.

## 4. Web del equipo docente

Está en `web/teachers` y reutiliza la capa común del personal: el login con `?volver=`, la bandeja, los avisos, el adaptador de la API y el mapa de cada tarea a su pantalla. Todas las tareas docentes llegan ya asignadas, por el BPMN o por la elección del jefe, así que no se reclaman.

| Pantalla | Quién | Qué hace |
|---|---|---|
| `index.html` | Equipo docente | Bandeja con las tareas docentes del usuario y el curso de cada una |
| `definir-docente.html` | Jefe docente | Elige uno de los tres integrantes, incluido él mismo, y completa con `docenteSeleccionado` |
| `desarrollar-curso.html` | Docente elegido | Registra el fin del dictado, con confirmación |
| `elaborar-informe.html` | Jefe docente | Escribe el informe o adjunta un PDF propio |

- **"Aplicación móvil" (1.4).** "Definir Docente" se resolvió como web responsive: las pantallas se diseñaron primero para el celular. Falta que el profesor confirme este criterio.
- **Validaciones en el motor.** La web solo evita los errores obvios, como no elegir a nadie o mandar el informe vacío. La regla real la aplica el motor:
  - `ValidateSelectedTeacher`: el elegido tiene que ser del grupo `Docentes`.
  - `ValidateCourseEndDate`: el dictado no se puede dar por terminado antes de `fechaFinPropuesta`.
  - `ValidateReport`: el texto es obligatorio salvo que se adjunte un PDF.

  El mensaje del 400 se muestra tal cual.
- **Informe en PDF.** Si se escribe el texto, la web arma el PDF con jsPDF a partir del texto y los datos del curso. Si se adjunta uno propio, se marca `informeAdjunto`. En los dos casos el PDF se sube con `POST /api/tareas/{id}/informe` y se completa con la ruta que devuelve el backend (`rutaInforme`). Esa ruta queda en `cursos.resultados`. Antes de subir el archivo, la web revisa lo mismo que el backend: que empiece con `%PDF-`, que no esté vacío y que no pase de 10 MB.
- **Modo simulado.** Con `?api=mock`, las pantallas funcionan sin backend, con usuarios del grupo Docentes. Esto permitió desarrollarlas antes de que estuvieran el login y el backend.

**Observación sobre el enunciado.** La sección 4.1.3 dice que el informe se elabora "desde la interfaz de Flowable o, de manera opcional, desde el sitio web". Hoy `SaveReport` exige `rutaInforme`, así que la tarea solo se puede completar desde el sitio, que es el que sube el PDF.

## 5. Ejecuciones para el análisis

El 14/11/2026 se generaron 20 ejecuciones terminadas con `make recorrido`, con todos los recursos elegidos (aula física, virtual y equipamiento):

| Resultado | Ejecuciones |
|---|---|
| `finalizado` | 12 |
| `cancelado` | 3 |
| `rechazado` | 3 |
| `cerrado_por_falta_de_respuesta` | 2 |

A eso se suman una ejecución con modificaciones y nueva propuesta hecha con el simulador (escenario 2) y dos cursos llevados hasta el final desde las pantallas docentes con la API real (escenario 1). Estas ejecuciones alimentan las consultas de 4.3 y la minería de 4.4.

## 6. Pruebas

- **Tests automáticos.** Ninguno de estos tests necesita Docker.
  - Materiales: 14 tests con un motor en memoria. Cubren reclamar, completar, permisos, la unión asíncrona y las consultas 4.3.
  - Simulador: 15 tests. Cubren qué comunicaciones están habilitadas, cómo se arman los pedidos REST y la ventana.
- **Escenario 11.** Una solicitud se llevó hasta "Preparar curso", mandando la aceptación y la nómina con el simulador, y *Preparar materiales* quedó como la última rama. `empleadoAdministrativo` la reclamó y la completó desde la app. `jefeAdministrativo` dejó de verla. Flowable UI siguió el proceso solo hasta "Definir Docente".
- **Escenarios 1 y 2 con la web docente.** Con `make dev-up` y la API real, en el navegador:
  - Sin sesión, la bandeja manda al login y después vuelve a la bandeja.
  - El jefe eligió docente y el docente registró el fin del dictado.
  - El informe se completó una vez con texto y una vez con un PDF propio. Las dos instancias terminaron en `finalizado`, con la ruta en `cursos.resultados`.
- **Modo simulado.** Se recorrieron las pantallas en Chrome sin interfaz gráfica: enlaces de la bandeja, informe vacío, archivo que no es PDF, PDF generado y PDF propio, y el aviso para un usuario sin tareas.
