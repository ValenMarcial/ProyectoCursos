# Sitio web de la institución

Sitio web de la institución (sección 4.1 del enunciado). Permite a las empresas solicitar capacitaciones y consultar sus cursos, y al personal de la institución (administración y equipo docente) gestionar las tareas del proceso.

Es un único sitio con distintas vistas según quién ingresa. Todas las vistas operan sobre las mismas instancias del proceso en Flowable.


## Cómo está organizado

```
web/
├── index.html          Portada con acceso a cada sección
├── common/              Código compartido (actualmente solo un estilo)
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
- Elaborar el informe de resultados desde el sitio: adjunta el PDF, la web extrae el texto, completa el formulario y la ruta del archivo se genera automáticamente.

Cada docente puede consultar y completar las tareas *Desarrollar curso* que tenga asignadas. La tarea solo se puede completar desde la fecha de fin acordada.

Carpeta: `teachers/`

Pantallas:

- `index.html`: bandeja con las tareas docentes asignadas al usuario. Todas llegan asignadas (al jefe por el BPMN, o al docente elegido), así que no se reclaman.
- `definir-docente.html`: el jefe elige uno de los tres integrantes (`jefeDeEquipo`, `docente1`, `docente2`) y completa la tarea con `docenteSeleccionado`. Está pensada para el celular: el enunciado (1.4) pide hacerla "mediante la aplicación móvil".
- `desarrollar-curso.html`: el docente ve el curso y registra la finalización del dictado, con confirmación en la misma pantalla.
- *Elaborar informe de resultados* se completa desde el sitio: se adjunta el PDF y el backend devuelve la ruta que se envía como `rutaInforme`.

Reutiliza el adaptador de Administración (`crearApi` de `administration/js/api.js`), sus estilos y la capa común del personal: el login con `?volver=` (`common/js/sesion.js` y `navegacion.js`) y los avisos (`common/js/avisos.js`). Lo propio está en `teachers/js/`: ids de tareas e integrantes (`contrato-docentes.js`), un mock con usuarios del grupo Docentes (`mock-docentes.js`) y funciones comunes de las pantallas (`pantalla-docentes.js`).

Por defecto usa la API real: sin sesión, la bandeja manda al login común y vuelve después de ingresar. Para probar en modo simulado: `cd web && python3 -m http.server 8000` y abrir `http://localhost:8000/teachers/index.html?api=mock`. El selector "Ver como" (o `?usuario=docente1`) cambia de integrante. `?api=real` vuelve al backend.

## Cómo abrir el sitio

Los archivos estáticos los sirve el backend (Spring Boot). Con `make dev-up`,
el sitio queda en `http://localhost:8081`.

Cada cambio en `web/` requiere volver a correr `make dev-up`.

Las pantallas del personal usan la API real y piden iniciar sesión. Para probarlas sin backend, con datos simulados: `cd web && python3 -m http.server 8000` y abrir `http://localhost:8000/common/bandeja.html?api=mock`. La elección queda en la pestaña hasta cerrarla; `?api=real` vuelve a la API real.

Para editar más rápido se puede servir `web/` con un servidor estático en el
puerto 3000 y abrirlo como `http://localhost:3000`. En ese caso las llamadas
al backend necesitan URL absoluta y CORS habilitado en el controller.

## Pendiente

- Desarrollar las vistas de cada carpeta.
- Definir los endpoints del backend que cada vista necesite.
- Resolver cómo consumen las pantallas del personal la API REST de Flowable.

## Convenciones que se pueden seguir para llamar al backend

- Cuando el sitio lo sirve el backend, usar rutas relativas (`/api/...`).
- Revisar siempre `respuesta.ok`: `fetch` no lanza error ante un 4xx o 5xx.
- Leer el cuerpo de la respuesta también cuando hay error. El backend puede
  devolver información útil (por ejemplo, el estado `PENDIENTE` en un 502).
- Mostrar al usuario un mensaje claro en cada caso de error.