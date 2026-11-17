# Preparar la presentación

En la presentación no hay slides: se muestran las aplicaciones funcionando, y cada integrante muestra lo que hizo. El profesor pide dos cosas:

- Ejecutar instancias distintas para cubrir los caminos del modelo.
- Llegar con muchas instancias ya ejecutadas, para que 4.3 y 4.4 tengan sentido.

Esta guía explica cómo dejar lista la máquina de la presentación y cómo compartir esos datos.

## 1. Una sola base

Se presenta desde **una sola máquina**, que llamamos "máquina de la demo". Si cada uno usa su propia base, las consultas de 4.3 y la minería de 4.4 dan números distintos según quién presente.

Las instancias, el historial de Flowable, los cursos y las cuentas de empresa están en la base `Institucion`, que comparten Flowable y el backend. Los PDF de los informes están en `data/informes/`. Para mover la demo a otra máquina hay que llevar las dos cosas (ver la sección 4).

## 2. Empezar de cero en la máquina de la demo

Las instancias viejas de `make recorrido` se completaron en segundos y como `admin`, así que deforman 4.3. Por eso conviene arrancar con una base vacía:

```bash
make dev-reset        # BORRA la base local
make db-migrate
make seed             # usuarios y grupos (pide las credenciales de Flowable para importar el modelo)
make deploy
```

## 3. Generar las instancias: `make corridas`

```bash
make corridas CANTIDAD=40 PARALELO=4 NO_VIABLES=2
```

Lanza 40 instancias, 4 a la vez. Tarda unos 30 a 40 minutos, porque hace pausas reales. Cada instancia:

- **Completa las tareas como las personas**, por el backend y con la sesión de cada una, igual que la web. Cada tarea la completa quien la tiene asignada. Si es una tarea de grupo, la reclama antes un administrativo al azar. Así 4.3.3 cuenta a cada uno.
- **Prepara el curso en paralelo y con pausas.** Las tres ramas tardan tiempos distintos en cada curso, y por eso 4.3.2 da promedios que dicen algo.
- **Varía la negociación** (para 4.4):
  - cero, una o dos modificaciones, cada una con "Revisar propuesta";
  - a veces deja pasar un recordatorio antes de responder;
  - termina en aceptación, rechazo o falta de respuesta.
- **Varía el resto:**
  - los recursos elegidos;
  - el docente (`jefeDeEquipo`, `docente1` o `docente2`);
  - si el informe se escribe o se adjunta. El PDF se sube de verdad, así "Enviar informe" lo puede mostrar.

Destinos aproximados:

| Destino | Proporción |
|---|---|
| Fin normal | 45 % |
| Rechazo | 25 % |
| Cancelación durante la preparación | 15 % |
| Falta de respuesta | 15 % |

Con varias instancias a la vez, a veces no queda aula física (hay dos). Esa instancia termina cancelada por `SIN_AULAS`, que también es un camino del modelo. `NO_VIABLES=2` llena al final el cupo de 5 cursos, manda 2 solicitudes que el motor rechaza por viabilidad y después cancela los cursos que usó para llenar el cupo.

Opciones:

- `PARALELO`: cuántas instancias corren a la vez. Con 5 o más, el motor empieza a rechazar por viabilidad.
- `ESCALA=0`: saca las pausas. Es más rápido, pero 4.3.2 queda con duraciones casi nulas, así que solo sirve para probar.

Al final muestra un resumen por resultado. Para ver 4.3 completo:

```bash
cd desktop/materiales && mvn -q compile exec:java -Dexec.args="analisis"
```

Si el Postgres de Docker no está en el puerto 5432, hay que agregar `MOTOR_DB_URL=jdbc:postgresql://127.0.0.1:<puerto>/Institucion` adelante.

**Antes del 17/11/2026, la app de materiales guardaba las horas con la zona de la PC.** Los demás guardan en UTC, así que el fin de "Preparar materiales" quedaba 3 horas corrido y 4.3.2 podía dar duraciones negativas. Ya está corregido. Las instancias de bases viejas que se completaron con la app siguen con ese error: es otro motivo para empezar de cero.

## 4. Guardar y compartir la base

```bash
make demo-guardar                 # crea data/demo/demo.dump y data/demo/demo-informes.tar.gz
make demo-cargar                  # REEMPLAZA la base local por la de esos archivos
make demo-guardar DEMO=data/demo/demo-17-11   # con otro nombre
```

- `data/demo/` no se sube al repo, porque el dump pesa y tiene datos de prueba con cuentas. Se comparte por Drive.
- `demo-cargar` para Flowable y el backend, restaura la base y los PDF, y los vuelve a levantar. El dump incluye el BPMN desplegado: no hace falta `make deploy`, pero sí tener el mismo código (`make build`).
- Antes de una prueba que pueda romper algo, conviene guardar con `make demo-guardar` y volver con `make demo-cargar`.

## 5. Instancias para mostrar en vivo

Además de las 40 terminadas, conviene dejar instancias **frenadas** antes de cada momento que se quiere mostrar, para no esperar timers delante del profesor.

| Camino | Cómo dejarlo listo |
|---|---|
| Fin normal (escenario 1) | Una solicitud aceptada y en "Preparar curso", para seguirla desde la web y la app de materiales |
| Modificaciones (2) | Una solicitud con la propuesta enviada, para mandar modificaciones con el simulador |
| Rechazo (3) y falta de respuesta (4) | Solicitudes con la propuesta enviada |
| Límite de cursos (5) | `make recorrido ESCENARIO=limite` justo antes. `ESCENARIO=limpiar-limite` después |
| Cancelación (6) | Una solicitud aceptada con recursos ya conseguidos |
| Falta de aulas (7) | Dos cursos con aula física reservada y un tercero listo para "Evaluar Recursos" |
| Solicitud desde la web (9) y empresa (10) | Se hacen en vivo. Las credenciales de la empresa salen con `docker compose logs flowable \| grep "Cuenta de empresa creada"` |
| Materiales y simulador (11) | Un curso con "Preparar materiales" como última rama pendiente |

Estas instancias se crean después de `make corridas` y antes de `make demo-guardar`, así quedan en el dump.

## 6. Problemas conocidos

- **Puerto 8080 ocupado.** Si otro programa (por ejemplo, un servidor de Django) usa el 8080, `make deploy` choca contra él. Se puede publicar Flowable en otro puerto con un `docker-compose.override.yml` local (`ports: !override ["127.0.0.1:8090:8080"]`). Después se usa `make deploy FLOWABLE_REST=http://127.0.0.1:8090/flowable-ui/process-api`, y lo mismo con `make corridas`.
- **Tests del motor.** Necesitan Java 11 o 17, porque Drools 7 falla con Java 21 o superior.
