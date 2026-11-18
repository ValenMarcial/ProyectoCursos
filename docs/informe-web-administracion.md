# Web de Administración, login y bandeja del personal

Esta sección describe la web del personal administrativo: las pantallas de Administración, el login y la bandeja comunes, la separación por grupos y el backend de sesión y tareas que las sostiene. Cubre los escenarios 2, 3, 4 y 8 de la defensa.

## 1. Cómo se conectan las piezas

```
Navegador (web/administration, web/common)
      │   solo habla con el backend, nunca con Flowable
      ▼
Backend Spring Boot (:8081)   /api/sesion, /api/tareas, /api/instancias
      │   llama a Flowable con su cuenta admin y aplica los permisos él mismo
      ▼
Flowable (:8080)  ←→  PostgreSQL (base Institucion)
```

El navegador no habla directo con Flowable por dos razones. Una es no exponer las credenciales del motor. La otra es que Flowable no controla permisos: se comprobó que con la sesión de un usuario se podía completar una tarea asignada a otro. Ese control vive en el backend (`TareaService`).

## 2. Login, sesión y secciones

- **Login común** (`web/common/login.html`). Manda usuario y clave de Flowable a `POST /api/sesion`. El backend los valida contra Flowable, guarda en la sesión el usuario, su nombre y sus grupos, y renueva el id de sesión. La clave no se guarda: solo se usa para validar.
- **Sesión.** El navegador queda con la cookie `JSESSIONID` (30 minutos sin uso). `GET /api/sesion` dice quién está logueado y `DELETE /api/sesion` cierra. Toda `/api` exige sesión; quedan libres, por método y ruta, `POST /api/sesion`, `POST /api/solicitudes` (alta pública) y `POST /api/empresa/sesion` (login de las empresas). Las rutas `/api/empresa/**` piden la sesión de la empresa, no la del personal.
- **Sesión vencida.** Si cualquier pedido responde 401, el adaptador lleva al login con `?volver=<pantalla>`, y después del login se vuelve a donde se estaba. Cada pantalla pide sesión al abrir (`exigirSesion`, de `web/common/js/sesion.js`).
- **Secciones por grupo.** Administración (grupo `Administracion`) y Equipo docente (grupo `Docentes`). Una sola tabla (`web/common/js/secciones.js`) dice quién entra adónde: el login lleva a cada persona a su sección (o a la bandeja común si no tiene una sola), y cada pantalla, a través de su `js/guardia.js` y de `protegerSeccion` (`web/common/js/seccion.js`), muestra quién tiene la sesión con "Cerrar sesión" y, si la persona es de otro grupo, un aviso con el enlace a la suya. Es una regla de la interfaz, no de seguridad: los datos los protege el backend (una tarea ajena responde 404 al verla y 403 al completarla).

Usuarios del personal: `jefeAdministrativo` y `empleadoAdministrativo` (Administración); `jefeDeEquipo`, `docente1` y `docente2` (Docentes).

## 3. Bandeja de tareas

`web/common/bandeja.html` es común a los dos grupos. Muestra **Mis tareas** (asignadas al usuario) y **Del grupo** (sin asignar, de sus grupos, con el botón "Tomar"). Cada tarea tiene un enlace a su pantalla: la tabla de qué tarea del BPMN abre qué pantalla está en un solo lugar (`web/common/js/pantallas.js`), así que agregar una pantalla es agregar una línea.

## 4. Backend de sesión y tareas

Un solo `TareaService` genérico sirve a la bandeja, a las pantallas de Administración y a las de docentes. Todo se limita al proceso `process-proyecto-curso` (constante `PROCESO` de `FlowableTareaClient`).

| Operación | Regla |
|---|---|
| Listar `alcance=mias` | Tareas con `assignee` igual al usuario de la sesión |
| Listar `alcance=grupo` | Tareas sin asignar cuyo grupo candidato es alguno de los del usuario. Sin grupos, lista vacía sin llamar a Flowable: con el parámetro vacío, Flowable lo ignora y devolvería todas |
| Ver una tarea | Solo si es del usuario o de un grupo suyo; si no, 404 (no revela si existe) |
| Variables de una instancia | Solo si el usuario tiene una tarea activa en ella, y solo las de una lista cerrada |
| Reclamar | Solo si la tarea es de un grupo suyo (si ya es suya, no hace nada). El responsable sale siempre de la sesión y el pedido no lleva cuerpo. Un `claim` sin responsable libera la tarea, por eso nunca se manda vacío |
| Completar | Solo si la tarea ya es del usuario (si no, 403). Cada variable se valida contra una lista cerrada (nombre y tipo) y el cuerpo para Flowable se arma de nuevo solo con nombre, tipo y valor |

- **Paginación:** `start` (0 por defecto) y `size` (50 por defecto, máximo 100). El backend manda siempre `size`, porque Flowable pagina de a 10 por su cuenta.
- **Errores.** Los del backend salen como `{"message"}`. Los del motor (400 de negocio, 404, 409, 500) se reenvían con el mismo estado y cuerpo. Un 401 o 403 del motor significa que falló la cuenta admin del backend: se responde 502 y se registra el motivo sin credenciales.
- **Limitación conocida:** las listas de variables que se pueden leer y escribir son únicas para todo el proceso, no por tarea.

## 5. Adaptador de la API

En `web/administration/js/`:

- `contrato.js` reúne lo que depende de acuerdos con el grupo: rutas, ids de las tareas del BPMN, y nombres y tipos de las variables. Si algo cambia, se cambia ahí.
- `api.js` ofrece las operaciones que usan las pantallas (`listarTareas`, `obtenerTarea`, `reclamarTarea`, `completarTarea`, `obtenerVariables`, `obtenerInforme` y las de sesión) y hace tres trabajos para ellas: convierte las fechas (el formulario usa dd/MM/aaaa y Flowable pide ISO), manda los tipos explícitos (`long`, `date`, `string`, `boolean`) porque sin ellos los listeners del motor fallan al convertir, y traduce los errores a mensajes para el usuario (400 con `exception` es un rechazo de negocio y se muestra tal cual; 500 es siempre un mensaje genérico; 401 lleva al login).
- **Modo simulado.** Con `?api=mock` las pantallas funcionan sin backend, con respuestas que imitan a las reales (`mock.js`); el modo queda guardado en la pestaña. Se usó para desarrollar las primeras pantallas cuando el backend todavía no tenía los endpoints de tareas, y sirve para ensayar sin depender de Flowable. No simula el avance del proceso: al completar, la tarea solo desaparece.

## 6. Pantallas de Administración

| Pantalla | Tarea del BPMN | Quién | Qué hace |
|---|---|---|---|
| Registrar y elaborar propuesta | Registrar y Elaborar propuesta | `jefeAdministrativo` | Formulario con programa, presupuesto y fechas; genera el PDF de la propuesta con vista previa, botón para descargarlo y enlace para abrirlo en otra pestaña (no se descarga solo) |
| Revisar propuesta | Revisar propuesta | `jefeAdministrativo` | Muestra las observaciones de la empresa y la propuesta anterior; se corrige y genera el PDF de la nueva versión (escenario 2) |
| Archivar solicitud | Archivar solicitud | `jefeAdministrativo` | Muestra el motivo (rechazo o falta de respuesta) y cierra el expediente (escenarios 3 y 4) |
| Evaluar recursos | Evaluar Recursos | Grupo `Administracion` (se reclama) | Aula física, aula virtual y equipamiento; exige al menos uno (escenario 8) |
| Confeccionar lista definitiva | Confeccionar lista definitiva | La asigna un listener (`AssignToLeastBusyAdministrative`) al administrativo con menos carga; si llega sin asignar, la pantalla la reclama | Muestra la nómina que envió la empresa, permite agregar, editar y quitar nombres y la confirma (escenario 8) |
| Enviar informe y archivar | Enviar informe a empresa y Archivar expediente | `jefeAdministrativo` | Muestra el informe, permite ver y descargar el PDF y archiva el expediente |

- **Validaciones.** La web solo evita los errores obvios; la regla real la aplica el motor (`ValidateProposalDates`, `ValidateResourceSelection`), y su mensaje se muestra tal cual.
- **Versión de la propuesta.** El PDF lleva su número de versión. `versionPropuesta` la escribe el motor en el evento "Modificaciones" (2, 3, ...) y la web solo la lee; si no llegara, usa 2 como respaldo.
- **Motivo del archivo.** Archivar solicitud no escribe nada: lee `resultado` (`rechazado` o `cerrado_por_falta_de_respuesta`) para mostrar el motivo.
- **Informe de resultados.** Lo elabora el jefe docente (web de docentes). Esta pantalla lo consulta: muestra el texto, o un aviso si el docente adjuntó su propio PDF, y baja el PDF con `GET /api/tareas/{id}/informe`. El archivo se pide como Blob al cargar, así los enlaces "Ver" y "Descargar" no los bloquea el navegador y, si falta, el aviso aparece de inmediato.
- **Envío a la empresa.** Ningún componente del sistema envía el informe: el botón "Enviar y archivar" registra que se envió por fuera y completa la tarea sin variables. Al completarla, el motor termina con `resultado = finalizado` y el curso pasa a `finalizado`.

## 7. Reutilización de código

Para no repetir lógica entre pantallas se extrajeron módulos comunes:

- `common/js/avisos.js` (mensajes en pantalla), `sesion.js` (`exigirSesion`), `navegacion.js` (`?volver=` seguro y destino después del login), `pantallas.js` (tarea → pantalla), `secciones.js` (quién entra adónde), `seccion.js` (`protegerSeccion`: guardia y barra de sesión) y `bandeja.js`.
- `administration/js/pantalla-tarea.js`: buscar qué tarea abrir (`?tarea=<id>`), recordarla en la URL, mostrar los datos de la solicitud (`camposSolicitud`) y las filas de datos.
- `formulario-propuesta.js`: leer, validar, precargar y convertir el formulario de la propuesta, compartido por Registrar y Revisar.
- `pdf-propuesta.js` (PDF con jsPDF 4.2.1, copia local en `js/vendor/` para no depender de internet en la defensa) y `texto.js` (`quitarAcentos`, `formatearPresupuesto`, `formatearFechaAcordada`).

## 8. Pruebas

- **Modo simulado:** cada pantalla se recorrió en Chromium, con sus casos de borde: tarea inexistente, tarea ya completada, doble clic, sesión vencida y datos faltantes.
- **API real** (`make dev-up`), con los usuarios de la seed y en el navegador: login, bandeja, cada pantalla hasta completar su tarea, y el efecto en el motor y en la base (por ejemplo, dos instancias llevadas hasta "Enviar informe", una con texto y otra con PDF adjunto, terminaron con `resultado = finalizado` y el curso en `finalizado`). Se probaron dos revisiones seguidas de la propuesta (versión 2 y 3).
- **Seguridad:** nombre de empresa con HTML mostrado como texto (todo se escribe con `textContent`), sin claves en las URL ni en `localStorage`, `?volver=` con rutas externas o de la sección de otro grupo termina en el destino propio (su sección o la bandeja), y una tarea ajena responde 404 o 403.

## 9. Limitaciones y pendientes

- El envío del informe a la empresa se registra pero no se hace desde el sistema (ver sección 6).
- Quedan copiadas la vista previa y descarga del PDF entre Registrar y Revisar propuesta (`descargarPdf` y `mostrarVistaPrevia`), y `formatearFechaAcordada` en la web docente (`teachers/js/pantalla-docentes.js`).
- `obtenerInforme` no comprueba que el archivo recibido sea `application/pdf` antes de abrirlo (sugerencia de la revisión de la #103). Hoy el backend responde siempre PDF.
- Las pantallas ajenas a la sección igual cargan su script antes de que la guardia las reemplace; el backend frena cualquier pedido, pero puede verse un error en la consola.
