# Proyecto Cursos: organización de cursos de capacitación en Flowable

Implementación del proceso de organización de cursos de capacitación (enunciado *Proyecto GTPN*, versión 3) sobre Flowable 6.8. Tiene el proceso BPMN con sus reglas y listeners, un backend REST en Spring Boot, el sitio web de la institución y dos aplicaciones de escritorio.

---

## 1. Qué hay en el repositorio

| Carpeta o archivo | Contenido |
|---|---|
| `src/main/resources/processes/ProyectoCursos.bpmn20.xml` | Modelo BPMN ejecutable (proceso `process-proyecto-curso`) |
| `src/main/resources/rules/viability.drl` | Regla de negocio de viabilidad (Drools) |
| `src/main/java/flowable` | Componentes del motor: listeners, delegates, validaciones, aulas simuladas y seed de usuarios |
| `src/main/java/web/backend` | Backend REST (Spring Boot): solicitudes, sesión y tareas del personal, informes en PDF y acceso de las empresas |
| `web/` | Sitio web: formulario público, empresas, Administración y equipo docente ([web/README.md](web/README.md)) |
| `desktop/materiales` | App de preparación de materiales con la API Java y las consultas de 4.3 ([README](desktop/materiales/README.md)) |
| `desktop/simulador` | Simulador de las comunicaciones de la empresa con la API REST ([README](desktop/simulador/README.md)) |
| `infra/` | Base de datos `Institucion` (schema, seed y migraciones) y scripts (`recorrido.py`, `corridas.py`) |
| `docs/contrato.md` | Contrato del proceso: ids de tareas, mensajes, variables y sus tipos |
| `docs/informe-*.md` | Informe: variables, listeners y decisiones de implementación |
| `docs/preparar-demo.md` | Cómo generar las instancias para 4.3 y 4.4 y dejar lista la presentación |

---

## 2. Cómo ejecutar la solución

Requisitos: Docker con Docker Compose, Java 11 o 17, Maven y Python 3.

```bash
make dev-up      # compila y levanta Postgres, Flowable UI, el backend y pgAdmin
make db-migrate  # aplica las migraciones de la base Institucion
make seed        # crea usuarios y grupos en Flowable e importa el BPMN al Modeler (pide admin / test)
make deploy      # despliega el BPMN y la regla de viabilidad en el motor
```

Con eso queda todo funcionando:

| Qué | Dónde |
|---|---|
| Sitio web (público, empresas y personal) | <http://localhost:8081> (usar `localhost`, no `127.0.0.1`) |
| Login del personal | <http://localhost:8081/common/login.html> |
| Flowable UI | <http://localhost:8080/flowable-ui> (`admin` / `test`) |
| App de materiales | `cd desktop/materiales && mvn -q compile exec:java` |
| Simulador de la empresa | `cd desktop/simulador && mvn -q compile exec:java` |
| Consultas 4.3 por consola | `cd desktop/materiales && mvn -q compile exec:java -Dexec.args="analisis"` |

### Usuarios y grupos

`make seed` los crea en Flowable. El personal entra a todas las aplicaciones con su usuario y su clave de Flowable: no hay cuentas aparte en la base de la institución.

| Usuario | Clave | Grupo | Rol |
|---|---|---|---|
| `jefeAdministrativo` | `JefeA` | `Administracion` | Jefe administrativo |
| `empleadoAdministrativo` | `EmpleadoA` | `Administracion` | Administrativo |
| `jefeDeEquipo` | `JefeD123` | `Docentes` | Jefe del equipo docente |
| `docente1` | `Docente1` | `Docentes` | Docente |
| `docente2` | `Docente2` | `Docentes` | Docente |

También crea una cuenta para cada integrante del grupo de desarrollo (`valentin`, `juan`, `matiasP`, `matiasB`, `marcelo`) en el grupo `Admin`, con acceso al Modeler. Las cuentas de las empresas las crea el proceso en "Habilitar acceso de la empresa"; para la demostración, las credenciales se escriben en el log de Flowable:

```bash
docker compose logs flowable | grep "Cuenta de empresa creada"
```

### Generar ejecuciones

```bash
make recorrido ESCENARIO=final    # recorre un camino del proceso (ver más abajo)
make corridas CANTIDAD=40         # genera muchas instancias variadas para 4.3 y 4.4
```

---

## 3. Entorno local en detalle

### Requisitos y configuración

Se necesita Docker con Docker Compose, Java 11 (o 17) y Maven para ejecutar el proyecto Java. Drools 7 no funciona con Java 21 o superior: compila, pero falla al ejecutar las reglas porque usa `java.lang.Compiler`, que ya no existe. El contenedor de Flowable trae Java 11, así que esto solo afecta a lo que se corre en la PC (por ejemplo, los tests). El archivo `.env` configura la base y las credenciales locales:

```env
POSTGRES_DB=Institucion
POSTGRES_USER=UserPostgres
POSTGRES_PASSWORD=Password
LOAD_SEED_DATA=true
```

Estas credenciales son solo para desarrollo local.

### Qué inicia Docker Compose

`docker-compose.yml` define cuatro servicios:

- **PostgreSQL 15** crea la base `Institucion` al inicializar un volumen vacío. Su healthcheck espera a que PostgreSQL acepte conexiones.
- **Flowable UI 6.8** se conecta a esa misma base y crea/actualiza sus tablas internas al arrancar. Además carga nuestro `.jar` y las librerías de Drools desde `flowable-lib/` (ver [Desplegar el proceso en Flowable](#desplegar-el-proceso-en-flowable)).
- **Backend** (Spring Boot) expone la API REST que usa el sitio web y sirve los archivos de `web/`.
- **pgAdmin** se inicia con el profile `dev` y permite inspeccionar la base desde el navegador.

Las tablas `ACT_*` y `*_DATABASECHANGELOG*` son de Flowable. Las tablas `empresas`, `solicitudes`, `cursos`, `cuentas_empresa` y `schema_version` son de este proyecto.

### Levantar y verificar

```bash
make dev-up      # compila y levanta Postgres, Flowable, el backend y pgAdmin en segundo plano
make db-migrate  # aplica en orden las migraciones pendientes del schema
make backend-test # ejecuta las pruebas del backend REST (perfil Maven backend)
make verify      # verifica configuración, tablas y disponibilidad de Flowable
make deploy      # despliega el BPMN y las reglas en Flowable
make dev-logs    # sigue los logs; Ctrl-C deja los contenedores ejecutándose
make dev-psql    # abre psql conectado a Institucion
make dev-down    # detiene y elimina los contenedores, conserva los volúmenes
```

`make dev-up` espera a que PostgreSQL esté saludable. Flowable puede tardar unos segundos adicionales; `make verify` reintenta el acceso a la UI durante un tiempo limitado.

Direcciones locales:

- Flowable UI: <http://127.0.0.1:8080/flowable-ui>
- Backend REST: <http://127.0.0.1:8081/api>
- pgAdmin: <http://127.0.0.1:5050> (usuario `admin@localhost.com`, contraseña `admin123`)

### Backend REST

El código Spring Boot está en el paquete `web.backend` dentro de `src/main/java/web` y se inicia junto con el entorno mediante `make dev-up`. Inicia las solicitudes en Flowable (evento de mensaje `msgSolicitud`), atiende la sesión y las tareas del personal, guarda y entrega los PDF del informe, y expone el acceso de las empresas. Habla con Flowable por su API REST. Tanto el motor como el backend se configuran desde el POM raíz: el perfil predeterminado `flowable-engine` compila el motor y `mvn -Pbackend test` compila y prueba el backend, sin mezclar sus fuentes en el JAR que carga Flowable.

Para ver los pasos de uso, mirá la [guía del backend Spring Boot](./docs/uso-web-springboot.md).

Iniciar una solicitud:

```bash
curl -i -X POST http://127.0.0.1:8081/api/solicitudes \
  -H 'Content-Type: application/json' \
  -d '{
    "empresaNombre": "Tech Solutions S.A.",
    "contactoNombre": "Ana Pérez",
    "contactoEmail": "ana@example.com",
    "contactoTelefono": "555-0101",
    "tematica": "Capacitación BPMN",
    "descripcion": "Curso para el equipo de desarrollo",
    "participantesEstimados": 12,
    "fechasPreferidas": "Marzo de 2027",
    "plazoRecordatorio": "PT1M"
  }'
```

El backend busca o crea la empresa por nombre, guarda una fila en `solicitudes` y vincula esa fila con el `processInstanceId` y `businessKey` devueltos por Flowable. El curso se crea más adelante: cuando la empresa acepta, `Habilitar acceso de la empresa` lo crea y guarda su ID en `solicitudes.curso_id`. Ante un timeout en el inicio intenta reconciliar consultando Flowable por la clave; si no confirma el resultado, devuelve `502` con esa clave y estado `PENDIENTE`. Verificá una solicitud pendiente antes de reenviar. `plazoRecordatorio` es opcional. **El plazo normal del proceso es de cinco días (`P5D`):** si omitís la variable, la tarea BPMN `Enviar propuesta` la establece antes de activar el timer. Para probar sin esperar cinco días, podés pasar una duración corta como `PT1M` en el JSON; ese valor se usa solo para esa instancia.

Las respuestas de la empresa (aceptación, rechazo, modificaciones, nómina y cancelación) las manda el simulador (`desktop/simulador`). El envío de la propuesta a la empresa está simulado con un log de Flowable.

Para ver PostgreSQL desde pgAdmin, iniciá sesión y registrá un servidor:
Hacer click derecho sobre Server y seleccionar Register. En la pestaña **Connection** usá:

| Campo | Valor |
|---|---|
| Host name/address | `postgres` |
| Port | `5432` |
| Maintenance database | `Institucion` |
| Username | `UserPostgres` |
| Password | `Password` |

El host `postgres` funciona desde el contenedor de pgAdmin en la red de Compose. Desde una herramienta instalada directamente en la PC, el host es `localhost` y el puerto `5432`.

### Schema y seed SQL: empresas y cursos

En el primer arranque con un volumen vacío, PostgreSQL ejecuta automáticamente, en orden:

1. `infra/db/init/01-institucion-schema.sh` ejecuta `infra/db/sql/institucion-schema.sql` en `Institucion`. Crea `schema_version`, `empresas`, `solicitudes` y `cursos`, incluyendo claves primarias, claves foráneas, restricciones y claves de negocio/instancia.
2. `infra/db/init/02-seed.sh` ejecuta `infra/db/sql/seed.sql` si `LOAD_SEED_DATA=true`. Inserta cuatro empresas, tres solicitudes y tres cursos de ejemplo independientes.

Para no insertar esos datos al crear una base nueva, poné `LOAD_SEED_DATA=false` en `.env` antes de iniciar PostgreSQL por primera vez. Esto omite solo la seed SQL; el schema se crea igual. Cambiar esa variable con un volumen ya inicializado no borra filas ni vuelve a ejecutar los scripts.

La seed SQL es **idempotente**: evita duplicar empresas y solicitudes por sus claves normalizadas/de negocio, y omite cursos de ejemplo que ya existen para la misma empresa, nombre y fecha de inicio.

Los scripts de inicialización solo corren con un volumen vacío y crean el schema base actual, registrado como versión `0` en `schema_version`. Los cambios posteriores se agregan como `infra/db/migrations/NNN-nombre.sql` (empezando por `001`) y se aplican con `make db-migrate`, que los ejecuta en orden y conserva el volumen y sus datos. Cada migración debe registrar su propio número en `schema_version`. Si tenés un volumen creado antes de este baseline, recrealo una vez con `make dev-reset`; ese comando elimina todos los volúmenes. Las migraciones no usan Flyway y no se ejecutan automáticamente al arrancar Spring Boot.

### Seed Java: usuarios, grupos e importación del modelo

La seed Java es independiente de `LOAD_SEED_DATA`. Primero levantá PostgreSQL y Flowable:

```bash
make dev-up
```

Esperá a que Flowable termine de iniciar y aplique sus migraciones; podés confirmarlo con `make verify`. Después, desde la raíz del proyecto, compilá y ejecutá la seed:

```bash
mvn compile
mvn exec:java -Dexec.mainClass=flowable.seed.Main
```

Por defecto, `Main` crea los usuarios y grupos e importa el modelo BPMN. Podés controlar cada parte con parámetros de lanzamiento:

| Parámetros | Crea usuarios/grupos | Importa el BPMN |
|---|---|---|
| ninguno | sí | sí |
| `--no-seed` | no | sí |
| `--no-import` | sí | no |

```bash
# Solo importar el BPMN (no crea usuarios ni grupos)
mvn exec:java -Dexec.mainClass=flowable.seed.Main -Dexec.args="--no-seed"

# Solo crear usuarios y grupos (no importa el BPMN ni pide credenciales)
mvn exec:java -Dexec.mainClass=flowable.seed.Main -Dexec.args="--no-import"
```

La seed se conecta a `jdbc:postgresql://localhost:5432/Institucion` con `UserPostgres` y `Password`. Si tu Postgres está en otro puerto o tiene otras credenciales, usá las mismas variables que la app de materiales:

```bash
MOTOR_DB_URL=jdbc:postgresql://localhost:5433/Institucion MOTOR_DB_USER=UserPostgres MOTOR_DB_PASSWORD=... make seed
```

Usar `--no-seed` y `--no-import` juntos es un error: `Main` muestra el uso y termina con código de salida distinto de cero, porque no habría nada para hacer. Un argumento desconocido también produce un error. Aun con `--no-seed` se crea el motor IDM y se conecta a `Institucion`.

También podés compilar y ejecutar en un solo comando, una vez que `make dev-up` haya terminado y Flowable esté listo: `mvn compile exec:java -Dexec.mainClass=flowable.seed.Main`. `Main` conecta con `Institucion` y ejecuta `FlowableSeed`, que crea los usuarios y grupos de la sección [Usuarios y grupos](#usuarios-y-grupos) y sus membresías. Los datos se guardan en las tablas de identidad de Flowable, no en una tabla propia de usuarios.

Cuando la importación está habilitada, `Main` solicita por terminal un usuario y una contraseña de Flowable UI e importa el modelo `src/main/resources/processes/ProyectoCursos.bpmn20.xml` al Modeler. El proceso se llama `Proyecto Cursos` y conserva el id `process-proyecto-curso`.

La importación usa las credenciales de Flowable UI, no las de PostgreSQL. El usuario debe pertenecer al grupo `Admin` —por ejemplo, cualquiera de los usuarios creados por la seed— porque ese grupo tiene el privilegio `access-modeler`; `Administracion` y `Docentes` no lo tienen. La contraseña se escribe en la terminal y queda visible durante la ejecución.

`ModelerImporter` autentica contra `http://localhost:8080/flowable-ui/` y hace un `POST` multipart a `/flowable-ui/modeler-app/rest/import-process-model`. El archivo se lee desde el classpath, así que volvé a compilar después de modificar el BPMN. Si las credenciales son inválidas, la importación se cancela; si el archivo no existe o la respuesta no es `200`/`201`, se informa el error y el cuerpo de la respuesta.

Importar el modelo al Modeler **no lo despliega** en el motor ni crea una definición de proceso: solo deja el modelo disponible en la aplicación Modeler. El despliegue se hace con `make deploy` (ver la sección siguiente). Volver a ejecutar `Main` repite la importación del archivo.

### Desplegar el proceso en Flowable

Flowable es el que ejecuta el proceso: crea las instancias y las tareas, dispara los timers y evalúa las reglas. El sitio web, el simulador y las apps solo le piden cosas por la API REST o la API Java (iniciar una solicitud, completar una tarea, enviar un mensaje). Para eso, Flowable tiene que tener **desplegado** el proceso y poder encontrar **nuestro código** (listeners, reglas). Sin despliegue, iniciar una solicitud responde "no existe ese proceso".

```bash
make dev-up      # compila y levanta todo
make deploy      # despliega BPMN + reglas; cada despliegue crea una versión nueva del proceso
```

Hay que volver a correr `make deploy` cada vez que cambia el BPMN o el `.drl`. Si cambia código Java (listeners, delegates), hay que recompilar y reiniciar Flowable, porque las clases se cargan al arrancar:

```bash
make build && docker compose restart flowable
```

Si el BPMN tiene errores, Flowable rechaza el despliegue y `make deploy` muestra la lista exacta de problemas (por ejemplo, un evento de mensaje sin `messageRef`). Sirve como chequeo del modelo antes de subirlo.

#### Qué hace cada pieza y por qué

| Pieza | Qué hace | Por qué hace falta |
|---|---|---|
| `make build` | Compila nuestro `.jar` y copia a `flowable-lib/` las librerías de Drools que Flowable UI no trae (19 jars). | La imagen `flowable/flowable-ui:6.8.0` incluye los motores BPMN y DMN, pero **no Drools**. Sin esas librerías, desplegar un BPMN con la tarea de reglas falla con `NoClassDefFoundError: org/kie/api/...`. Solo se copian las que faltan: las que la imagen ya trae en otra versión no se tocan, para evitar conflictos. |
| `entrypoint` del servicio `flowable` en `docker-compose.yml` | Arranca Flowable con el mismo comando que la imagen, pero suma `/opt/proyecto/lib/*` al classpath, donde se monta `flowable-lib/`. Está fuera de `target/` y `make build` la vacía sin borrarla: si se borra la carpeta montada (con `rm -rf` o `mvn clean`), Flowable sigue apuntando a la vieja y arranca sin nuestro `.jar`. `make dev-up` reinicia Flowable para que cargue el `.jar` recién compilado. | La imagen solo carga `/app/WEB-INF/lib/*`. Montar una carpeta ahí taparía las librerías de Flowable, y montar los jars uno por uno serían 20 líneas. |
| `FLOWABLE_CHECK_PROCESS_DEFINITIONS: "false"` | Desactiva el autodespliegue de Flowable al arrancar. | Flowable despliega solo cualquier `processes/*.bpmn20.xml` que encuentre en el classpath, y nuestro `.jar` incluye el BPMN. Si el modelo tiene un error, Flowable no arranca y queda reiniciándose. El despliegue se hace explícitamente con `make deploy`. |
| `RulesConfigurator` + `META-INF/services/org.flowable.common.engine.impl.EngineConfigurator` | Registra el `RulesDeployer` en el motor de procesos. Flowable encuentra la clase solo, gracias al archivo de `META-INF/services` (no se genera automáticamente: es parte del código). | Flowable trae el soporte para `.drl`, pero **apagado**. Sin el `RulesDeployer`, el `.drl` se guarda en el despliegue pero no se procesa, y la tarea falla con `deployment ... doesn't contain any rules`. Así no hace falta modificar la configuración de Flowable UI. |
| `make deploy` | Arma `target/deploy.zip` con el BPMN y el `.drl` y lo envía a `POST /flowable-ui/process-api/repository/deployments`. | El `.drl` tiene que estar en el **mismo despliegue** que el BPMN. La API REST de Flowable 6.8 acepta un solo archivo por despliegue: si se mandan dos, **ignora el segundo sin avisar**. Un `.zip` es la forma que acepta para desplegar varios recursos juntos. |
| Dependencias en `pom.xml` | `drools-mvel` y `flowable-engine-configurator` (`provided`). | Sin `drools-mvel`, Drools no puede compilar el `.drl`. `flowable-engine-configurator` ya viene en Flowable UI; se declara `provided` solo para compilar `RulesConfigurator`. |

`make deploy` usa por defecto el usuario `admin`/`test` de Flowable UI. Se puede cambiar con `make deploy FLOWABLE_USER=... FLOWABLE_PASSWORD=...`.

### Recorrer el proceso de punta a punta

`make recorrido` recorre un camino completo del proceso por la API REST, sin usar las pantallas: inicia una solicitud desde el backend, completa las tareas con sus datos y manda los mensajes. Sirve para probar, para la demo y para generar ejecuciones con cierres distintos (minería, 4.4).

```bash
make recorrido ESCENARIO=final           # acepta, prepara, desarrolla el curso y cierra con el informe (~1 minuto)
make recorrido ESCENARIO=cancelar        # acepta y cancela durante "Preparar curso"
make recorrido ESCENARIO=rechazo         # la empresa rechaza y se archiva
make recorrido ESCENARIO=sin-respuesta   # dos recordatorios y se archiva (~1 minuto)
make recorrido ESCENARIO=limite          # completa hasta 5 cursos en ejecución y la solicitud siguiente no es viable (escenario 5)
make recorrido ESCENARIO=limpiar-limite  # cancela los cursos que dejó «limite»
make recorrido ESCENARIO=final RECURSOS=virtual FLOWABLE_USER=juan FLOWABLE_PASSWORD=Juan
```

Al terminar muestra las actividades recorridas, el valor de `resultado` y si la instancia terminó. Si el proceso se queda en algún paso, avisa dónde. Cada corrida crea una empresa "Empresa recorrido HHMMSS" (`limite` crea "Empresa límite 2" a "Empresa límite 5", según cuántos cursos haya ya en ejecución, y los deja esperando en "Preparar curso" para mostrarlos en la defensa); las instancias se pueden seguir en la Admin app mientras corre. Necesita el backend y Flowable levantados y el BPMN desplegado; solo usa Python 3 sin dependencias.

Cada escenario termina con su `resultado` (`finalizado`, `cancelado`, `rechazado` o `cerrado_por_falta_de_respuesta`), que `RecordProcessResult` registra en `solicitudes.resultado` y, si hay curso, en `cursos.estado`.

Para la presentación hacen falta muchas instancias variadas (4.3 y 4.4): `make corridas` las genera con usuarios reales, pausas y negociación variada, y `make demo-guardar` / `make demo-cargar` guardan y cargan la base completa. Ver [docs/preparar-demo.md](docs/preparar-demo.md).

#### Problema conocido: Flowable se queda en "Waiting for changelog lock"

Si Flowable se corta mientras crea o actualiza sus tablas (por ejemplo, porque se reinició varias veces seguidas), puede quedar un bloqueo en la base y el siguiente arranque espera para siempre. Para liberarlo:

```bash
make dev-psql
# dentro de psql, buscar la tabla bloqueada (*_databasechangeloglock con locked = true) y liberarla, por ejemplo:
UPDATE act_app_databasechangeloglock SET locked = false, lockgranted = NULL, lockedby = NULL;
```

y después `docker compose restart flowable`.

### Detener y empezar de cero

`make dev-down` conserva los datos. Para borrar **todos** los datos persistidos de PostgreSQL y la configuración de pgAdmin y volver a inicializar:

```bash
make dev-reset
```

Este comando equivale a detener Compose y eliminar sus volúmenes (`docker compose down -v`), y luego iniciar los servicios otra vez. Es destructivo: se pierden los datos de `Institucion`, incluidas las tablas y usuarios de Flowable. Con `LOAD_SEED_DATA=true`, el nuevo volumen vuelve a cargar las cuatro empresas y los tres cursos; para recrear también los usuarios y grupos y volver a importar el BPMN al Modeler, ejecutá `Main` después de que Flowable haya arrancado. No hace falta usar `dev-reset` para el trabajo diario.

### Tablas propias de la base `Institucion`

Además de las tablas `ACT_*` que administra Flowable, el schema define cuatro tablas propias:

| Tabla | Columnas principales | Para qué sirve |
|---|---|---|
| `schema_version` | `version` (PK), `applied_at` | Registra las versiones del schema aplicadas; la inicialización nueva registra la versión `0` y las migraciones futuras empiezan en `001`. |
| `empresas` | `id` (PK identity), `nombre` (único sin distinguir mayúsculas ni espacios externos), `cantidad_empleados` (entero `>= 0`, opcional) | Empresas que solicitan cursos; se reutilizan por nombre normalizado. |
| `solicitudes` | `id` (PK identity), `empresa_id` (FK), `business_key` (único), temática/descripción, contacto, participantes, fechas preferidas, `instancia_flowable_id` (único), estado de integración | Datos del formulario público. Se crea al recibir la solicitud y se vincula inmediatamente con su instancia Flowable. |
| `cursos` | `id` (PK identity), `empresa_id` (FK), nombre, descripción, programa, presupuesto, fechas, resultados, `instancia_flowable_id` (único), estado de negocio | Cursos. "Habilitar acceso de la empresa" crea el curso al aceptarse la propuesta y lo vincula con su solicitud (`solicitudes.curso_id`) y con su instancia de Flowable. |
| `cuentas_empresa` | `empresa_id` (PK y FK), `usuario` (único), `clave_hash`, `creada_en` | Cuenta de acceso de cada empresa. La crea "Habilitar acceso de la empresa"; la clave se guarda con hash (ver `docs/contrato.md`, "Acceso de las empresas"). |

`solicitudes.instancia_flowable_id` guarda el ID de la instancia de Flowable desde que se inicia el proceso. `solicitudes.business_key` se genera antes del inicio y permite correlacionar el proceso con la solicitud local. `integracion_flowable_estado` diferencia `pendiente`, `iniciada` y `legacy`.

La relación entre `solicitudes` e instancias de Flowable es **1:1**: cada formulario inicia su propia instancia. Una solicitud aceptada queda vinculada con su curso por `solicitudes.curso_id`.

`estado` refleja el avance del curso: lo actualizan `UpdateCourseState` en cada etapa y el Execution Listener `RecordProcessResult` al cerrar la instancia. Valores permitidos: `en_preparacion`, `confirmado`, `pendiente_de_inicio`, `en_desarrollo`, `en_elaboracion_informe`, `finalizado`, `cancelado`, `rechazado`, `cerrado_por_falta_de_respuesta` y `no_viable`.

La solicitud no tiene un estado de curso: su integración con Flowable se controla por separado. Los estados de `cursos` corresponden al ciclo de vida de los cursos existentes.

> **Sobre `resultados`:** es un `VARCHAR(255)` y en la seed ya contiene una ruta de ejemplo. Guarda la **ruta relativa del PDF** con el informe de resultados, no el contenido del informe en sí. El backend usa `INFORMES_DIR=/data/informes` y `docker-compose.yml` monta `./data/informes:/data/informes`; la ruta guardada tiene la forma `cursos/<instanciaFlowableId>/<uuid>.pdf`. El texto del informe queda en la instancia de Flowable (`informeResultados`), no en la base. Si más adelante mueven los PDFs a un storage externo como MinIO, conviene reemplazar esa ruta por la clave o URL del objeto y agregar una columna para el **hash** que permita verificar que el archivo no se corrompió.

---

### Cuidados al redesplegar

- **Versiones del BPMN.** Cada despliegue crea una versión nueva. Las instancias viejas siguen con la versión anterior, pero usan el `.jar` nuevo: si cambia el nombre de una variable o de una clase, pueden romperse. Ante la duda, `make dev-reset`.
- **El `.jar` dentro de Flowable.** Si cambia un listener o un delegate, hay que reconstruir el `.jar` y reiniciar el contenedor (`make dev-up` lo hace). No alcanza con redesplegar el BPMN.

---

## 4. Documentación

- [docs/contrato.md](docs/contrato.md): ids de las tareas, mensajes, variables, asignaciones y errores de negocio.
- [docs/informe-motor-y-empresas.md](docs/informe-motor-y-empresas.md) y [docs/informe-apps-y-docentes.md](docs/informe-apps-y-docentes.md): informe de implementación.
- [docs/uso-web-springboot.md](docs/uso-web-springboot.md): uso del backend REST.
- [docs/preparar-demo.md](docs/preparar-demo.md): instancias para 4.3 y 4.4, y preparación de la presentación.
- [web/README.md](web/README.md), [desktop/materiales/README.md](desktop/materiales/README.md) y [desktop/simulador/README.md](desktop/simulador/README.md): cada aplicación.
