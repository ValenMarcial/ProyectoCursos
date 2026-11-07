# Sitio web de la institución

Sitio web de la institución (sección 4.1 del enunciado). Permite a las empresas solicitar capacitaciones y consultar sus cursos, y al personal de la institución (administración y equipo docente) gestionar las tareas del proceso.

Es un único sitio con distintas vistas según quién ingresa. Todas las vistas operan sobre las mismas instancias del proceso en Flowable.


## Cómo está organizado

```
web/
├── index.html          Portada con acceso a cada sección
├── comun/              Código compartido (actualmente solo un estilo)
├── public/            Sección pública: formulario de solicitud
├── enterprise/            Acceso y seguimiento para empresas
├── administration/     Funciones para Administración
└── teachers/           Funciones para el equipo docente
```

Cada carpeta tiene un responsable (ver la tabla de abajo). Lo que está en `comun/` se modifica avisando al grupo.

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
- Elaborar el informe de resultados (opcional desde el sitio; también se puede desde la interfaz de Flowable).

Cada docente puede consultar y completar las tareas *Desarrollar curso* que tenga asignadas.

Carpeta: `teachers/`

## Cómo abrir el sitio en local

Con el backend levantado (`make dev-up`), desde la carpeta `web/`:

```
python3 -m http.server 8000
```

Después entrar a `http://localhost:8000`.

La portada se puede ver abriendo `index.html` directamente, pero las páginas que se conectan al backend necesitan el servidor.


## Pendiente

- Antes de comenzar con la implementacion de cada vista con su logica correspondiente se debe definir que se implementara para servir estos archivos estaticos