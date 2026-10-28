# Proyecto GTPN: entorno y flujo de trabajo del grupo

Este documento define cómo vamos a trabajar los cinco sobre el proyecto de organización de cursos en Flowable 6.8: cómo armamos el entorno para que todos veamos lo mismo, cómo organizamos el repositorio y cómo nos repartimos el trabajo.

---

## 1. La idea central: el entorno se reproduce desde código

Para que todos veamos lo mismo **no hace falta compartir una única base de datos**. Hace falta que cualquiera pueda reconstruir el mismo entorno desde el repositorio con un par de comandos.

Además es obligatorio por otra razón: el día de la defensa todo va a correr en una sola máquina. Si el entorno depende de datos que alguien cargó a mano, no vamos a poder rearmarlo.

Por eso usamos dos entornos:

| Entorno | Para qué sirve | Dónde corre |
|---|---|---|
| **Local** | Desarrollar y probar cada uno lo suyo | En la máquina de cada uno, con Docker |
| **Compartido** | Integrar las apps entre sí y ensayar la defensa | Un único servidor al que nos conectamos todos |

---

## 2. Entorno local

### Qué levanta el `docker-compose.yml`

- **PostgreSQL**, con dos bases:
  - `flowable`: la base del motor.
  - `institucion`: nuestra base (empresas, cursos, estado, etc.). Se crea sola con un script SQL ubicado en `/docker-entrypoint-initdb.d`, que también carga los datos de prueba. Esto cubre además el entregable de *scripts para crear la BD y cargar datos de prueba*.
- **Flowable 6.8** (`flowable/flowable-ui:6.8.0`) apuntando a esa base, con nuestro `.jar` de listeners y delegates incluido.
- Más adelante, el **servicio REST de aulas**.

### Script de inicialización

Junto al compose hay un script (bash o Python) que, usando la API REST de Flowable:

1. Crea los cinco usuarios y los grupos `administracion` y `docentes`.
2. Despliega el BPMN y la DMN.

### Cómo resetear el entorno

```bash
docker compose down -v     # borra contenedores y volúmenes (base incluida)
docker compose up -d       # levanta todo de cero
./infra/bootstrap.sh       # usuarios, grupos y despliegue
```

Con esto cualquiera queda con un entorno limpio e idéntico al de los demás. Sirve especialmente para probar cada escenario de la defensa desde cero.

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
/infra        docker-compose, SQL de la base institucion, scripts de inicialización
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
| **2. Backend Java e infraestructura** | Delegates, task listener, execution listener, compensaciones y manejo de errores; base `institucion`; servicio REST de aulas; `docker-compose` y entorno compartido | `.jar` + fuentes, SQL, servicio de aulas, `/infra` |
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
- [ ] Armar `docker-compose.yml` y el script de inicialización.
- [ ] Probar un mini-proceso de compensación (cancelación a mitad del subproceso y fin normal) antes de construir el resto.
- [ ] Decidir dónde corre el entorno compartido (PC con Tailscale o VPS).
- [ ] Consultar con el profesor:
  - cómo espera que se maneje el escenario 7 (falta de aulas), que no está en el diagrama;
  - si las cuentas de las empresas pueden vivir solo en la base `institucion`, sin ser usuarios de Flowable.