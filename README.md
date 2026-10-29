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

Se necesita Docker con Docker Compose, Java 11 y Maven para ejecutar el proyecto Java. El archivo `.env` configura la base y las credenciales locales:

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
- **Flowable UI 6.8** se conecta a esa misma base y crea/actualiza sus tablas internas al arrancar.
- **pgAdmin** se inicia con el profile `dev` y permite inspeccionar la base desde el navegador.

Las tablas `ACT_*` y `*_DATABASECHANGELOG*` son de Flowable. Las tablas `empresas`, `cursos` y `schema_version` son de este proyecto.

### Levantar y verificar

```bash
make dev-up      # inicia Postgres, Flowable y pgAdmin en segundo plano
make verify      # verifica configuración, tablas y disponibilidad de Flowable
make dev-logs    # sigue los logs; Ctrl-C deja los contenedores ejecutándose
make dev-psql    # abre psql conectado a Institucion
make dev-down    # detiene y elimina los contenedores, conserva los volúmenes
```

`make dev-up` espera a que PostgreSQL esté saludable. Flowable puede tardar unos segundos adicionales; `make verify` reintenta el acceso a la UI durante un tiempo limitado.

Direcciones locales:

- Flowable UI: <http://127.0.0.1:8080/flowable-ui>
- pgAdmin: <http://127.0.0.1:5050> (usuario `admin@localhost.com`, contraseña `admin123`)

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

1. `infra/db/init/01-institucion-schema.sh` ejecuta `infra/db/sql/institucion-schema.sql` en `Institucion`. Crea `schema_version`, `empresas` y `cursos`, incluyendo claves primarias, restricciones y unicidad de nombres.
2. `infra/db/init/02-seed.sh` ejecuta `infra/db/sql/seed.sql` si `LOAD_SEED_DATA=true`. Inserta cuatro empresas y tres cursos de ejemplo.

Para no insertar esos datos al crear una base nueva, poné `LOAD_SEED_DATA=false` en `.env` antes de iniciar PostgreSQL por primera vez. Esto omite solo la seed SQL; el schema se crea igual. Cambiar esa variable con un volumen ya inicializado no borra filas ni vuelve a ejecutar los scripts.

La seed SQL actual **no es idempotente**: las restricciones únicas del schema impiden duplicados, pero volver a ejecutar `seed.sql` sobre una base que ya tiene esos nombres produce un error de clave duplicada. No la ejecutes manualmente hasta que la seed use `ON CONFLICT`.

### Seed Java: usuarios, grupos y membresías de Flowable

La seed Java es independiente de `LOAD_SEED_DATA`. Primero levantá PostgreSQL y Flowable:

```bash
make dev-up
```

Esperá a que Flowable termine de iniciar y aplique sus migraciones; podés confirmarlo con `make verify`. Después, desde la raíz del proyecto, compilá y ejecutá la seed:

```bash
mvn compile
mvn exec:java -Dexec.mainClass=flowable.seed.Main
```

También podés compilar y ejecutar en un solo comando, una vez que `make dev-up` haya terminado y Flowable esté listo: `mvn compile exec:java -Dexec.mainClass=flowable.seed.Main`. `Main` conecta con `Institucion` y ejecuta `FlowableSeed`, que crea los usuarios con IDs del `1` al `10`, los grupos `Admin`, `Administracion` y `Docentes`, y sus membresías. Los datos se guardan en las tablas de identidad de Flowable, no en una tabla propia de usuarios.

La conexión y las contraseñas de esos usuarios están escritas en el código Java para desarrollo. Luego hay que externalizar la configuración antes de usarlo fuera del entorno local. Crear esos usuarios no despliega automáticamente procesos BPMN/DMN.

### Detener y empezar de cero

`make dev-down` conserva los datos. Para borrar **todos** los datos persistidos de PostgreSQL y la configuración de pgAdmin y volver a inicializar:

```bash
make dev-reset
```

Este comando equivale a detener Compose y eliminar sus volúmenes (`docker compose down -v`), y luego iniciar los servicios otra vez. Es destructivo: se pierden los datos de `Institucion`, incluidas las tablas y usuarios de Flowable. Con `LOAD_SEED_DATA=true`, el nuevo volumen vuelve a cargar las cuatro empresas y los tres cursos; para recrear también los usuarios y grupos, ejecutá `Main` después de que Flowable haya arrancado. No hace falta usar `dev-reset` para el trabajo diario.

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
5. **Plazos parametrizados.** Usar `${plazoRecordatorio}` en lugar de `PT5D` fijo. En desarrollo se usa `PT1M` sin tocar el modelo.

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

- [ ] Crear el repositorio con la estructura de carpetas.
- [ ] Acordar y escribir `docs/contrato.md`.
- [x] Armar `docker-compose.yml` de desarrollo y el script de inicialización.
- [ ] Armar el ambiente de producción y el checklist de despliegue.
- [ ] Probar un mini-proceso de compensación (cancelación a mitad del subproceso y fin normal) antes de construir el resto.
- [ ] Decidir dónde corre el entorno compartido (PC con Tailscale o VPS).
- [ ] Consultar con el profesor:
  - cómo espera que se maneje el escenario 7 (falta de aulas), que no está en el diagrama;
  - si las cuentas de las empresas pueden vivir solo en la base `Institucion`, sin ser usuarios de Flowable.