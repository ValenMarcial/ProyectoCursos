# Proyecto GTPN: entorno y flujo de trabajo del grupo

Este documento define cómo vamos a trabajar sobre el proyecto de organización de cursos en Flowable, cómo armamos el entorno para que todos veamos lo mismo, cómo organizamos el repositorio y cómo nos repartimos el trabajo.

---

## 1. La idea central: el entorno se reproduce desde código

Cualquiera puede reconstruir el mismo entorno desde el repositorio con un par de comandos.

Esta pensado como que todo va a correr en una sola máquina. Si el entorno depende de datos que alguien cargó a mano, no vamos a poder rearmarlo.

Por eso usamos dos entornos:

| Entorno | Para qué sirve | Dónde corre |
|---|---|---|
| **Local** | Desarrollar y probar cada uno lo suyo | En la máquina de cada uno, con Docker |
| **Compartido** | Integrar las apps entre sí y ensayar la defensa | Un único servidor al que nos conectamos todos |

>Por el momento el entorno `Compartido` no esta implementado

---

## 2. Entorno local

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

`docker-compose.yml` define tres servicios:

- **PostgreSQL 15** crea la base `Institucion` al inicializar un volumen vacío. Su healthcheck espera a que PostgreSQL acepte conexiones.
- **Flowable UI 6.8** se conecta a esa misma base y crea/actualiza sus tablas internas al arrancar. Además carga nuestro `.jar` y las librerías de Drools desde `flowable-lib/` (ver [Desplegar el proceso en Flowable](#desplegar-el-proceso-en-flowable)).
- **pgAdmin** se inicia con el profile `dev` y permite inspeccionar la base desde el navegador.

Las tablas `ACT_*` y `*_DATABASECHANGELOG*` son de Flowable. Las tablas `empresas`, `solicitudes`, `cursos` y `schema_version` son de este proyecto.

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

### Backend para iniciar solicitudes en Flowable

El código Spring Boot está en el paquete `web.backend` dentro de `src/main/java/web` y se inicia junto con el entorno mediante `make dev-up`. Su única función es iniciar una solicitud en Flowable con el evento de mensaje `msgSolicitud`. Tanto el motor como el backend se configuran desde el POM raíz: el perfil predeterminado `flowable-engine` compila el motor y `mvn -Pbackend test` compila y prueba el backend, sin mezclar sus fuentes en el JAR que carga Flowable.

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

Este backend no registra las respuestas posteriores de la empresa ni autentica a quien envía la solicitud. El envío de la propuesta también continúa simulado mediante un log de Flowable.

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

También podés compilar y ejecutar en un solo comando, una vez que `make dev-up` haya terminado y Flowable esté listo: `mvn compile exec:java -Dexec.mainClass=flowable.seed.Main`. `Main` conecta con `Institucion` y ejecuta `FlowableSeed`, que crea los usuarios con IDs del `1` al `10`, los grupos `Admin`, `Administracion` y `Docentes`, y sus membresías. Los datos se guardan en las tablas de identidad de Flowable, no en una tabla propia de usuarios.

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

La Task app de Flowable UI no muestra los formularios del BPMN (`formProperty`), y los pasos de la empresa (aceptación, nómina, cancelación) todavía no tienen pantalla. `make recorrido` hace todo eso por la API REST: inicia una solicitud desde el backend, completa las tareas con sus datos y manda los mensajes. Sirve para probar, para la demo y para generar ejecuciones con cierres distintos (minería, 4.4).

```bash
make recorrido ESCENARIO=final           # acepta, prepara, desarrolla el curso y cierra con el informe (~1 minuto)
make recorrido ESCENARIO=cancelar        # acepta y cancela durante "Preparar curso"
make recorrido ESCENARIO=rechazo         # la empresa rechaza y se archiva
make recorrido ESCENARIO=sin-respuesta   # dos recordatorios y se archiva (~1 minuto)
make recorrido ESCENARIO=final RECURSOS=virtual FLOWABLE_USER=juan FLOWABLE_PASSWORD=Juan
```

Al terminar muestra las actividades recorridas, el valor de `resultado` y si la instancia terminó. Si el proceso se queda en algún paso, avisa dónde. Cada corrida crea una empresa "Empresa recorrido HHMMSS"; las instancias se pueden seguir en la Admin app mientras corre. Necesita el backend y Flowable levantados y el BPMN desplegado; solo usa Python 3 sin dependencias.

Cada escenario termina con su `resultado` (`finalizado`, `cancelado`, `rechazado` o `cerrado_por_falta_de_respuesta`), que `RecordProcessResult` registra en `solicitudes.resultado` y, si hay curso, en `cursos.estado`.

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
| `cursos` | `id` (PK identity), `empresa_id` (FK), nombre, descripción, programa, presupuesto, fechas, resultados, `instancia_flowable_id` (único), estado de negocio | Cursos existentes/registrados. En esta entrega no tienen relación con solicitudes; esa asociación y la creación tras la aceptación quedan para otra PR. |
| `cuentas_empresa` | `empresa_id` (PK y FK), `usuario` (único), `clave_hash`, `creada_en` | Cuenta de acceso de cada empresa. La crea "Habilitar acceso de la empresa"; la clave se guarda con hash (ver `docs/contrato.md`, "Acceso de las empresas"). |

`solicitudes.instancia_flowable_id` guarda el ID de la instancia de Flowable desde que se inicia el proceso. `solicitudes.business_key` se genera antes del inicio y permite correlacionar el proceso con la solicitud local. `integracion_flowable_estado` diferencia `pendiente`, `iniciada` y `legacy`.

La relación entre `solicitudes` e instancias de Flowable es **1:1**: cada formulario inicia su propia instancia. En esta entrega todavía no existe una relación entre `solicitudes` y `cursos`.

`estado` refleja el avance del curso y lo actualiza el Execution Listener al cerrar la instancia. Valores permitidos: `en_preparacion`, `confirmado`, `pendiente_de_inicio`, `en_desarrollo`, `en_elaboracion_informe`, `finalizado`, `cancelado`, `rechazado`, `cerrado_por_falta_de_respuesta` y `no_viable`.

La solicitud no tiene un estado de curso: su integración con Flowable se controla por separado. Los estados de `cursos` corresponden al ciclo de vida de los cursos existentes.

> **Sobre `resultados`:** es un `VARCHAR(255)` y en la seed ya contiene una ruta de ejemplo. La idea es que guarde la **ruta donde se va a guardar el PDF** con el informe de resultados del curso, no el contenido del informe en sí. Podriamos mover los PDFs a un storage externo por ejemplo MinIO, ahi conviene reemplazar esa ruta por la clave o URL del objeto y agregar una columna para el **hash** que permita verificar que el archivo no se corrompió.

---

## 3. Entorno compartido

Lo usamos cuando el sitio web, la app móvil, el simulador y la app de escritorio tienen que operar sobre **las mismas instancias del proceso**.

### Opciones

- **Una PC del grupo con Tailscale.** Es gratis y arma una red privada entre nuestras máquinas en pocos minutos, sin abrir puertos. Los demás acceden a Flowable con la IP de Tailscale de esa PC. Contra: esa PC tiene que estar prendida.
- **Un VPS barato o gratuito** (por ejemplo, el nivel gratuito de Oracle Cloud). Siempre está disponible, pero hay que cuidarlo más: contraseñas, no exponer Postgres a internet.

### Lo que NO vamos a hacer

Que cada uno corra su propio Flowable en Docker apuntando a una misma base Postgres en la nube. Tiene dos problemas:

- **Timers.** Cada contenedor de Flowable tiene su propio ejecutor de jobs. Los timers (recordatorios, fecha de inicio del curso) los dispara *cualquiera* de los motores conectados. Si en esa máquina el `.jar` está desactualizado o falta, falla con `ClassNotFoundException` y no se entiende por qué.
- **Despliegues.** Si alguien redespliega el BPMN, les cambia el proceso a todos en medio de sus pruebas.

> **App de escritorio con API Java:** se conecta directo a la base, así que configurarla con `asyncExecutorActivate = false` para que no compita disparando timers.

---

## 4. Repositorio

Usamos **un único repositorio (monorepo)**:

```
/infra        docker-compose, SQL de la base Institucion, scripts de inicialización
/motor        BPMN, DMN y proyecto Maven con listeners y delegates (+ tests JUnit)
/aulas        servicio REST de aulas
/web          sitio web
/desktop      app con API Java y simulador REST
/mobile       app móvil
/docs         contrato del proceso, informe, guion de la defensa
```

---

## 5. Reglas de trabajo

1. **El BPMN tiene un solo dueño.** El XML de un BPMN se mergea muy mal en Git: dos personas editándolo en paralelo terminan con un archivo roto. Si necesitás un cambio, se lo pedís al dueño o abrís un issue.
2. **El contrato (`docs/contrato.md`) se define primero y se cambia solo con acuerdo del grupo.** Incluye:
   - la key del proceso,
   - los IDs de las tareas,
   - los nombres de los mensajes,
   - las variables y su tipo.

   Es lo que permite que cada uno avance sin esperar al resto.
3. **Ramas por funcionalidad y pull requests a `main`**, con una revisión rápida de otro integrante. `main` siempre tiene que levantar con `docker compose up`.
4. **Tests JUnit del motor.** Si alguien cambia el BPMN y se rompe, por ejemplo, el camino de aceptación, se entera antes de subirlo.
5. **Plazos parametrizados.** El timer usa `${plazoRecordatorio}` y el BPMN asigna `P5D` (cinco días) si no se pasó un valor. El endpoint web permite enviar una duración corta como `PT1M` para pruebas, sin modificar el BPMN.

### Cuidados al redesplegar en el entorno compartido

- **Versiones del BPMN.** Cada despliegue crea una versión nueva. Las instancias viejas siguen con la versión anterior, pero usan el `.jar` nuevo. Si cambiamos el nombre de una variable o de una clase, las instancias viejas pueden romperse. Ante la duda, reseteamos el entorno.
- **El `.jar` dentro de Flowable.** Si cambia un listener o un delegate, hay que reconstruir el `.jar` y reiniciar el contenedor. No alcanza con redesplegar el BPMN.

---

## 6. Reparto de tareas (propuesta)

| Integrante | Responsabilidad | Entregables |
|---|---|---|
| **1. Motor e integración** | BPMN completo (dueño del modelo), DMN, usuarios y grupos, contrato del proceso | `.bpmn20.xml`, `.dmn`, `docs/contrato.md` |
| **2. Backend Java e infraestructura** | Delegates, task listener, execution listener, compensaciones y manejo de errores; base `Institucion`; servicio REST de aulas; `docker-compose` y entorno compartido | `.jar` + fuentes, SQL, servicio de aulas, `/infra` |
| **3. Sitio web: parte pública y empresas** | Formulario de solicitud e inicio de la instancia, login y vistas de la empresa, generación de PDFs | `/web` |
| **4. Sitio web: administración** | Bandeja de tareas, reclamo y completado, formularios de propuesta, revisión, evaluación de recursos, lista, archivo e informe | `/web` |
| **5. Apps externas** | App de escritorio con API Java (materiales), simulador de comunicaciones REST, app móvil para docentes | `/desktop`, `/mobile` |

- Los integrantes 1 y 2 trabajan muy pegados.
- Los integrantes 3 y 4 comparten el proyecto del sitio, así que tienen que acordar el stack el primer día.
- El informe final y el guion de la defensa (12 escenarios) los escribimos entre todos: cada uno documenta lo suyo y el integrante 1 lo unifica.

---

## 7. Próximos pasos

- [x] Crear el repositorio con la estructura de carpetas.
- [ ] Acordar y escribir `docs/contrato.md`.
- [x] Armar `docker-compose.yml` de desarrollo y el script de inicialización.
- [x] Importar el BPMN al Modeler desde `Main`.
- [x] Desplegar el BPMN y las reglas Drools en Flowable con `make deploy`.
- [ ] Completar las asignaciones de tareas, las condiciones del gateway de recursos y `resultado` en los caminos de rechazo, sin respuesta y finalizado (ver `docs/contrato.md`).
- [ ] Armar el ambiente de producción y el checklist de despliegue.
- [ ] Probar un mini-proceso de compensación (cancelación a mitad del subproceso y fin normal) antes de construir el resto.
- [ ] Decidir dónde corre el entorno compartido (PC con Tailscale o VPS).
- [ ] Consultar con el profesor:
  - cómo espera que se maneje el escenario 7 (falta de aulas), que no está en el diagrama;
  - si las cuentas de las empresas pueden vivir solo en la base `Institucion`, sin ser usuarios de Flowable;
  - si una misma empresa puede contratar el mismo curso más de una vez; de ser así, la unicidad de `cursos` debería considerar las fechas.