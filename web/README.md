# Sitio web de la institución

Sitio web de la institución (sección 4.1 del enunciado). Permite a las empresas solicitar capacitaciones y consultar sus cursos, y al personal de la institución (administración y equipo docente) gestionar las tareas del proceso.

Es un único sitio con distintas vistas según quién ingresa. Todas las vistas operan sobre las mismas instancias del proceso en Flowable.


## Cómo está organizado

```
web/
├── index.html          Portada con acceso a cada sección
├── common/              Login y bandeja del personal, módulos JS y estilos compartidos
├── public/            Sección pública: formulario de solicitud
├── enterprise/            Acceso y seguimiento para empresas
├── administration/     Funciones para Administración
└── teachers/           Funciones para el equipo docente
```

## Sección pública

### Sección de capacitación

Permite a las empresas registrar una solicitud mediante un formulario que incluye:

- Datos de la empresa y de la persona de contacto.
- Temática y descripción de la capacitación solicitada.
- Cantidad estimada de participantes.
- Descripción de fechas preferidas.

Al enviarlo se registra la información y se inicia una instancia del proceso. Cada solicitud queda vinculada con su instancia en Flowable.

Carpeta: `public/`

## Sección de empresas

Las empresas acceden con una cuenta propia, que se crea en la actividad automática *Habilitar acceso de la empresa*. Una vez identificada, la empresa puede:

- Consultar sus cursos (solo los suyos).
- Ver el estado de cada curso: en preparación, confirmado y pendiente de inicio, en desarrollo, en elaboración del informe, finalizado o cancelado.
- Durante la preparación, consultar las actividades pendientes o en ejecución, incluidas las de las ramas paralelas.

Carpeta: `enterprise/`

## Sección de administrativos

El personal de Administración ingresa con sus credenciales de Flowable. Puede:

- Consultar las tareas asignadas a su usuario y las disponibles para su grupo.
- Reclamar y completar las tareas que correspondan.
- Completar *Registrar y elaborar propuesta* y *Revisar propuesta* (con generación del PDF).
- Completar *Archivar solicitud*, *Confeccionar lista definitiva* y *Evaluar recursos*.
- El jefe administrativo también consulta el informe de resultados y completa *Enviar informe a empresa y archivar expediente*.

Carpeta: `administration/`

## Sección de equipo docente

El jefe del equipo docente ingresa con sus credenciales de Flowable y puede:

- Consultar sus tareas de *Definir docente para desarrollar el curso*.
- Seleccionar uno de los tres docentes (incluido él mismo) y asignarlo a *Desarrollar curso*.
- Elaborar el informe de resultados desde el sitio: se escribe el texto y la web lo convierte a PDF al guardar, o se adjunta un PDF propio (en ese caso el texto es opcional). La ruta del archivo se genera automáticamente.

Cada docente puede consultar y completar las tareas *Desarrollar curso* que tenga asignadas. La tarea solo se puede completar desde la fecha de fin acordada.

Carpeta: `teachers/`

Pantallas:

- `index.html`: bandeja con las tareas docentes asignadas al usuario. Todas llegan asignadas (al jefe por el BPMN, o al docente elegido), así que no se reclaman.
- `definir-docente.html`: el jefe elige uno de los tres integrantes (`jefeDeEquipo`, `docente1`, `docente2`) y completa la tarea con `docenteSeleccionado`. Está pensada para el celular: el enunciado (1.4) pide hacerla "mediante la aplicación móvil".
- `desarrollar-curso.html`: el docente ve el curso y registra la finalización del dictado, con confirmación en la misma pantalla.
- `elaborar-informe.html`: *Elaborar informe de resultados*. Se escribe el texto (la web lo convierte a PDF al guardar) o se adjunta un PDF propio, y el backend devuelve la ruta que se envía como `rutaInforme`. Con PDF adjunto se marca `informeAdjunto` y el texto es opcional.

Reutiliza el adaptador de Administración (`crearApi` de `administration/js/api.js`), sus estilos y la capa común del personal: el login con `?volver=` (`common/js/sesion.js` y `navegacion.js`) y los avisos (`common/js/avisos.js`). Lo propio está en `teachers/js/`: ids de tareas e integrantes (`contrato-docentes.js`), un mock con usuarios del grupo Docentes (`mock-docentes.js`) y funciones comunes de las pantallas (`pantalla-docentes.js`).

Por defecto usa la API real: sin sesión, la bandeja manda al login común y vuelve después de ingresar. Para probar en modo simulado: `cd web && python3 -m http.server 8000` y abrir `http://localhost:8000/teachers/index.html?api=mock`. El selector "Ver como" (o `?usuario=docente1`) cambia de integrante. `?api=real` vuelve al backend.

## Quién entra adónde

El personal (Administración y Equipo docente) usa el mismo login y las mismas credenciales de Flowable; lo que decide a qué sección pertenece cada persona es su grupo:

| Grupo | Sección | Carpeta |
|---|---|---|
| `Administracion` | Administración | `administration/` |
| `Docentes` | Equipo docente | `teachers/` |

- **Después del login** cada persona va al inicio de su sección. Si venía de una pantalla (`?volver=`) y es de su sección, vuelve ahí; si era de la sección de otro grupo, se descarta. Sin una sección única (otro grupo, o los dos a la vez) va a la bandeja común.
- **Cada pantalla de una sección** carga primero `js/guardia.js`, que exige sesión, muestra quién ingresó con el botón "Cerrar sesión" al pie de la cabecera y, si la persona es de otro grupo, reemplaza la pantalla por un aviso con un enlace a la suya. La regla está en `common/js/secciones.js` y la guardia en `common/js/seccion.js`.
- **Es una regla de la interfaz, no de seguridad.** Los datos los protege el backend: una tarea de otro grupo responde 404 al verla y 403 al completarla, aunque alguien llegue a la pantalla.
- **Las empresas son aparte:** tienen otra cuenta y otra sesión (`/api/empresa/**`), así que ingresar como personal no las deja entrar a "Mis cursos" ni al revés.
- La bandeja común (`common/bandeja.html`) y el login no pertenecen a ninguna sección: sirven a todo el personal.

## Cómo abrir el sitio

Los archivos estáticos los sirve el backend (Spring Boot). Con `make dev-up`,
el sitio queda en `http://localhost:8081`.

Cada cambio en `web/` requiere volver a correr `make dev-up`.

Las pantallas del personal usan la API real y piden iniciar sesión. Para probarlas sin backend, con datos simulados: `cd web && python3 -m http.server 8000` y abrir `http://localhost:8000/common/bandeja.html?api=mock`. La elección queda en la pestaña hasta cerrarla; `?api=real` vuelve a la API real.

Para editar más rápido se puede servir `web/` con un servidor estático en el
puerto 3000 y abrirlo como `http://localhost:3000`. En ese caso las llamadas
al backend necesitan URL absoluta y CORS habilitado en el controller.

## Estado

Hecho:

- `public/`: formulario de solicitud (`request.html`), que llama a `POST /api/solicitudes` e inicia la instancia.
- `enterprise/`: login de la empresa (`login.html`) y "Mis cursos" (`course.html`), con el estado de cada curso y sus actividades en curso, sobre `/api/empresa/**`.
- `common/`: login del personal (`login.html`) contra `POST /api/sesion`, bandeja común (`bandeja.html`, con "Mis tareas" y "Del grupo") y los módulos compartidos: avisos, sesión, `?volver=`, tabla de tarea → pantalla (`pantallas.js`) y de quién entra adónde (`secciones.js`, `seccion.js`).
- `administration/`: menú y las seis pantallas (Registrar y elaborar propuesta, Revisar propuesta, Archivar solicitud, Evaluar recursos, Confeccionar lista definitiva, y Enviar informe y archivar expediente), con el adaptador `js/api.js`, su contrato (`js/contrato.js`) y el modo simulado (`js/mock.js`). El PDF de la propuesta se arma en el navegador con jsPDF (copia local en `js/vendor/`).
- `teachers/`: bandeja docente y las pantallas Definir docente, Desarrollar curso y Elaborar informe de resultados (ver arriba).
- Las pantallas del personal no hablan con Flowable: usan el backend (`/api/sesion`, `/api/tareas`, `/api/instancias`), que llama al motor con su cuenta y aplica los permisos con el usuario y los grupos de la sesión. El detalle está en `docs/informe-web-administracion.md`.

Pendiente:

- Llevar a un módulo común la vista previa y descarga del PDF, hoy copiadas entre Registrar y Revisar propuesta, y usar `formatearFechaAcordada` de `administration/js/texto.js` en `teachers/js/pantalla-docentes.js`, que tiene una copia.
- `obtenerInforme` (`administration/js/api.js`) no comprueba que el archivo sea `application/pdf` antes de abrirlo.
- Las pantallas de una sección cargan su script aunque la persona sea de otro grupo, antes de que la guardia las reemplace: el backend frena los pedidos, pero puede verse un error en la consola.
- El envío del informe a la empresa no lo hace el sistema: "Enviar y archivar" registra que se envió por fuera y completa la tarea.

## Convenciones que se pueden seguir para llamar al backend

- Cuando el sitio lo sirve el backend, usar rutas relativas (`/api/...`).
- Revisar siempre `respuesta.ok`: `fetch` no lanza error ante un 4xx o 5xx.
- Leer el cuerpo de la respuesta también cuando hay error. El backend puede
  devolver información útil (por ejemplo, el estado `PENDIENTE` en un 502).
- Mostrar al usuario un mensaje claro en cada caso de error.