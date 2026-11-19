# Cómo probar los 11 escenarios

Pasos para recorrer con las pantallas reales (sitio web, apps de escritorio y Flowable UI) los escenarios de la demostración. Los escenarios 1, 3, 4, 5 y 6 también se pueden recorrer sin pantallas con `make recorrido` (ver el README).

## Antes de empezar

1. Desde la raíz del repositorio (rama `master`).
2. `make dev-up`, `make db-migrate`, `make seed` (pide usuario y clave de Flowable: `admin` / `test`) y `make deploy`.
3. Abrir el sitio en **http://localhost:8081** (usar `localhost`, no `127.0.0.1`: la cookie de sesión es distinta). Flowable UI está en http://localhost:8080/flowable-ui (`admin` / `test`).
4. Simulador de la empresa: `cd desktop/simulador && mvn -q compile exec:java`. App de materiales: `cd desktop/materiales && mvn -q compile exec:java`. Las dos piden Java 11 o 17.
5. Entorno limpio: cancelar o terminar las instancias que hayan quedado de pruebas anteriores, sobre todo antes del escenario 5, que cuenta cursos en ejecución.

| Usuario | Clave | Grupo | Hace |
|---|---|---|---|
| `jefeAdministrativo` | `JefeA` | Administracion | Registrar y revisar propuesta, archivar solicitud, enviar informe |
| `empleadoAdministrativo` | `EmpleadoA` | Administracion | Preparar materiales, evaluar recursos, lista definitiva, aula virtual y equipamiento |
| `jefeDeEquipo` | `JefeD123` | Docentes | Definir docente, elaborar informe |
| `docente1` / `docente2` | `Docente1` / `Docente2` | Docentes | Desarrollar curso (el elegido) |
| Empresa | sale del log | — | `docker compose logs flowable \| grep "Cuenta de empresa creada"` |

El personal entra por http://localhost:8081/common/login.html.

**Fechas (escenario 1).** "Desarrollar curso" no se puede completar antes de `fechaFinPropuesta`. Al registrar la propuesta poner **inicio posible y fin = hoy**, y en el simulador aceptar con la fecha sugerida (dos minutos adelante). El día se compara en UTC: pasadas las 21:00 (hora de Argentina) "hoy" ya es mañana, y la fecha de fin quedaría anterior al inicio acordado. Hacer la prueba antes de esa hora.

**Dos tareas sin pantalla web.** "Solicitar Aula Virtual" y "Contratar equipamiento" se completan en Flowable UI (aplicación *Task*), con `empleadoAdministrativo`: reclamar y completar.

**Consultas útiles** (`make dev-psql`):

```sql
SELECT id, estado, resultados FROM cursos ORDER BY id DESC LIMIT 5;
SELECT id, resultado FROM solicitudes ORDER BY id DESC LIMIT 5;
```

## 9. Solicitud desde el sitio web

- [ ] Portada → "Completar formulario" (`public/request.html`). Cargar una empresa nueva y enviar.
- [ ] Con datos inválidos (correo mal escrito, participantes en 0) se muestra un error y no se crea nada.
- [ ] Con datos válidos aparece la confirmación. En Flowable UI hay una instancia nueva en "Registrar y Elaborar propuesta", y en `solicitudes` una fila con `business_key`.

## 1. Aceptación y fin normal, con liberación

Partir de una solicitud del escenario 9.

- [ ] `jefeAdministrativo` → bandeja → "Registrar y Elaborar propuesta". Con campos vacíos o fechas invertidas muestra el error y marca el campo. Completar con inicio y fin = hoy. Se ve la vista previa del PDF y se puede descargar.
- [ ] Simulador: elegir la solicitud → **ACEPTACION** con la fecha sugerida. En el log de Flowable queda la cuenta de la empresa.
- [ ] "Preparar curso" arranca en paralelo:
  - [ ] **Preparar materiales** desde la app de materiales (`empleadoAdministrativo`: reclamar y completar).
  - [ ] **Recibir nómina**: simulador → **NOMINA** con nombres separados por coma. Después "Confeccionar lista definitiva" en la web.
  - [ ] **Evaluar Recursos** en la web: marcar aula física, virtual y equipamiento (sin ninguno da error). Después completar aula virtual y equipamiento en Flowable UI.
- [ ] `jefeDeEquipo` → Docentes → "Definir docente": elegir `docente1`.
- [ ] Esperar el timer "Fecha programada" (la fecha de inicio acordada, más unos segundos).
- [ ] `docente1` → "Desarrollar curso" y confirmar la finalización. Con otro docente no aparece la tarea.
- [ ] `jefeDeEquipo` → "Elaborar informe de resultados":
  - [ ] Con texto: escribirlo y guardar; la web genera el PDF.
  - [ ] Con PDF adjunto: marcarlo; el texto es opcional. Probar también un archivo que no es PDF y uno de más de 10 MB (muestra el error).
- [ ] `jefeAdministrativo` → "Enviar informe a empresa y archivar expediente": se ve el informe y se descarga el PDF. Completar.
- [ ] Resultado: `cursos.estado = finalizado`, `cursos.resultados` tiene la ruta del PDF y el archivo está en `data/informes/cursos/<instancia>/`. En el log de Flowable aparecen las liberaciones (`[WS Aulas]`).

## 2. Modificaciones y nueva propuesta

- [ ] Con una propuesta enviada, simulador → **MODIFICACIONES** con observaciones.
- [ ] `jefeAdministrativo` → "Revisar propuesta": se ven las observaciones y los datos de la solicitud. Cambiar el presupuesto y completar: el PDF nuevo y el mensaje de confirmación dicen "versión 2".
- [ ] Repetir MODIFICACIONES y comprobar que al completar la revisión dice "versión 3". Después **ACEPTACION** y que el curso siga el camino normal.

## 3. Rechazo

- [ ] Con una propuesta enviada, simulador → **RECHAZO**.
- [ ] `jefeAdministrativo` → "Archivar solicitud": el motivo dice que la empresa rechazó la propuesta. Archivar.
- [ ] `solicitudes.resultado = rechazado`.

## 4. Cierre por falta de respuesta

- [ ] Iniciar la solicitud con `"plazoRecordatorio": "PT1M"` (ver el `curl` de `docs/uso-web-springboot.md`, sección 2). El formulario del sitio no lo ofrece. Alternativa: `make recorrido ESCENARIO=sin-respuesta`.
- [ ] Completar la propuesta y esperar dos recordatorios.
- [ ] "Archivar solicitud" dice que la empresa no respondió. `solicitudes.resultado = cerrado_por_falta_de_respuesta`.

## 5. Límite de cursos en ejecución

- [ ] Sin cursos en ejecución (ver "Antes de empezar"), correr `make recorrido ESCENARIO=limite`.
- [ ] Hay cinco cursos en ejecución y la solicitud siguiente termina como `no_viable` (`solicitudes.resultado`).
- [ ] Limpiar con `make recorrido ESCENARIO=limpiar-limite`.

## 6. Cancelación con compensaciones

- [ ] Después de aceptar, completar "Evaluar Recursos" con los tres recursos y completar aula virtual y equipamiento.
- [ ] Simulador → **CANCELACION**.
- [ ] En el log aparecen las tres liberaciones, y `cursos.estado = cancelado`.
- [ ] Repetir cancelando con solo parte de los recursos: solo se libera lo que ya se había conseguido.

## 7. Falta de aulas

Hay solo dos aulas físicas (`Aula 1` y `Aula 2`).

- [ ] Llevar **tres** cursos seguidos hasta "Evaluar Recursos" con aula física marcada. No cancelar ni terminar los dos primeros.
- [ ] El tercero se queda sin aula: el proceso libera lo que había conseguido, el curso queda `cancelado` y el motivo de cierre es `SIN_AULAS`.
- [ ] Cancelar los otros dos al terminar.

## 8. Asignación, reclamo y fin de tareas

- [ ] En la bandeja de cada usuario, "mías" muestra lo asignado y "grupo" lo que su grupo puede reclamar.
- [ ] Reclamar una tarea de grupo con `empleadoAdministrativo`: pasa a "mías". Otro usuario del grupo ya no puede reclamarla.
- [ ] Una tarea ajena no se puede abrir ni completar: probar con `docente2` sobre la tarea de `docente1`, y con `empleadoAdministrativo` sobre "Registrar propuesta".
- [ ] Sin sesión, cualquier pantalla del personal redirige al login.

## 10. Acceso de la empresa y consulta exclusiva

- [ ] Portada → "Ingresar como empresa". Usuario o clave incorrectos: muestra el error.
- [ ] Con la cuenta del log: "Mis cursos" muestra el nombre de la empresa y sus cursos, no los de otra.
- [ ] El estado de cada curso cambia al recargar a medida que avanza el proceso (en preparación, confirmado, pendiente de inicio, en curso, en redacción del informe, finalizado o cancelado).
- [ ] En un curso en preparación, el desplegable "Actividades en curso" muestra las ramas en paralelo. En uno finalizado o cancelado no aparece.
- [ ] "Cerrar sesión" vuelve al login, y "atrás" no entra de nuevo. Si en el mismo navegador hay una sesión del personal, sigue abierta.
- [ ] Con la sesión de una empresa, entrar a una pantalla del personal redirige al login.

## 11. Materiales (API Java) y simulador (API REST)

- [ ] App de materiales: un usuario de Docentes no puede entrar; `empleadoAdministrativo` sí.
- [ ] Ve las tareas "Preparar materiales" de su grupo, reclama una y la completa. No puede completar una reclamada por otra persona.
- [ ] Simulador: la tabla muestra la etapa de cada solicitud, y solo se habilitan las comunicaciones que el proceso espera (por ejemplo, **NOMINA** y **CANCELACION** recién después de aceptar).

## Qué anotar cuando algo falla

Escenario y paso, usuario con el que se probó, mensaje que mostró la pantalla y, si hay, la línea del log (`docker compose logs flowable` o `docker compose logs backend`). Con eso alcanza para reproducirlo.
