# App de preparación de materiales (API Java de Flowable)

Aplicación de escritorio del punto 4.2.1 del enunciado. Usa la **API Java de Flowable** (no la REST): se conecta directo a la base del motor y permite a un administrativo:

- Identificarse con su usuario y contraseña de Flowable.
- Ver las tareas *Preparar materiales* que tiene asignadas o puede reclamar por su grupo.
- Reclamar una tarea disponible para el grupo.
- Completar una tarea (solo la puede completar quien la reclamó).

Es un proyecto Maven independiente del motor, igual que el simulador (`../simulador`).

## Cómo usarla

Con el entorno levantado (`make dev-up`) y el proceso desplegado (`make deploy`), desde esta carpeta:

```bash
mvn -q compile exec:java      # abre la ventana
mvn test                      # pruebas (usan un motor en memoria, no necesitan Docker)
```

Ingresar con un usuario del grupo Administracion (por ejemplo `empleadoAdministrativo` o `jefeAdministrativo`, creados por `make seed`). Una persona de otro grupo (docentes) no puede entrar. La lista se actualiza sola cada 5 segundos.

## Conexión a la base

Por defecto toma `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD` del `.env` de la raíz del repo y el puerto 5432. Si tu Postgres está en otro puerto (por ejemplo 5433 porque ya tenés uno local), se cambia con variables de entorno:

```bash
MOTOR_DB_URL=jdbc:postgresql://127.0.0.1:5433/Institucion mvn -q compile exec:java
```

También existen `MOTOR_DB_USER` y `MOTOR_DB_PASSWORD`.

## Decisiones

- **`asyncExecutorActivate=false`** (lo pide el enunciado): esta app solo consulta y completa tareas; los timers y trabajos los ejecuta Flowable UI. Si los dos motores ejecutaran trabajos sobre la misma base, se los disputarían.
- **`databaseSchemaUpdate=false`**: la app nunca crea ni modifica tablas, solo se conecta a las que ya existen.
- **Login con Flowable** (`IdentityService.checkPassword` del mismo motor de procesos), no hay cuentas aparte.
- **Cómo encuentra la tarea:** por su id en el BPMN (`taskDefinitionKey`, constante `CLAVE_TAREA`), así un renombre en el editor no la hace desaparecer. Si algún día se vuelve a dibujar la tarea y cambia el id, también la encuentra por el nombre "Prepara/Preparar materiales".
- **Reclamar** exige que la tarea no tenga responsable y que el usuario pertenezca a un grupo candidato; **completar** exige que la tarea sea del usuario.
- **`kie-api` en las dependencias:** el BPMN real tiene una tarea de reglas ("Evaluar Viabilidad", Drools). Para leer variables, reclamar o completar, el motor carga la definición del proceso y necesita las interfaces de KIE, aunque la app nunca ejecute esa tarea. Sin ellas falla con `NoClassDefFoundError: org/kie/api/runtime/rule/AgendaFilter`. La prueba `funcionaConUnProcesoQueTieneTareaDeReglas` lo verifica.

## Probado contra Flowable real

El 12/11/2026, con el BPMN de master desplegado y Postgres en Docker: se llevó una solicitud hasta "Preparar curso" (aceptación y nómina enviadas con el simulador), se completaron las otras ramas y "Preparar materiales" quedó como la última. Desde esta app, `empleadoAdministrativo` la vio con su empresa y temática, la reclamó y la completó; `jefeAdministrativo` dejó de verla y no pudo completarla. Flowable UI siguió el proceso solo hasta "Definir Docente" (unión asíncrona). Escenario 11 cumplido.

## Requisito en el BPMN: unión asíncrona

Si "Preparar materiales" es la última rama de "Preparar curso" en terminar, al completarla desde esta app el motor de la app seguiría ejecutando el proceso hasta "Enviar confirmación", un script Groovy que esta app no puede correr (no trae Groovy ni las clases del proyecto) y falla con `Can't find scripting engine for 'groovy'`. Por eso la unión **"Preparación completa"** (`sid-420D072A-C42B-404D-BBDA-B884DD90C410`) debe tener `flowable:async="true"`: la app solo completa la tarea y el resto lo ejecuta Flowable UI. La prueba `completarSiendoLaUltimaRamaNoEjecutaElResto` lo verifica (sin el async falla).

## Consultas de análisis (4.3)

Salen del historial de Flowable (`HistoryService`) para el proceso `process-proyecto-curso`, de todas sus versiones desplegadas. Se ven en la pestaña **Análisis** de la ventana (tras ingresar) o por consola, sin ventana ni login:

```bash
mvn -q compile exec:java -Dexec.args="analisis"
```

- **4.3.1 Resultados:** cantidad y porcentaje de solicitudes terminadas por `resultado` (finalizado, cancelado, rechazado, no_viable, cerrado_por_falta_de_respuesta). El porcentaje es sobre el total de terminadas. Si alguna terminó sin `resultado`, aparece como `sin_resultado`.
- **4.3.2 Preparación paralela:** duración promedio de cada rama, desde que empieza hasta que termina: inscripciones (de "Recibir nómina" al fin de "Confeccionar lista definitiva"), materiales ("Preparar materiales") y recursos (de "Evaluar Recursos" a la última acción elegida). Cuenta solo los cursos que llegaron al fin del subproceso "Preparar curso", así que excluye las preparaciones interrumpidas por cancelación.
- **4.3.3 Tareas por persona:** tareas de usuario completadas por cada administrativo y docente (sin pendientes ni canceladas).

Dependen de los ids de actividad del BPMN (constantes en `Analisis.java`): si se vuelve a dibujar alguna de esas actividades, hay que actualizar el id.

