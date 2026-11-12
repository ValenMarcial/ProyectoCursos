package flowable.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.flowable.engine.IdentityService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.engine.impl.rules.RulesDeployer;
import org.flowable.engine.runtime.Execution;
import org.flowable.job.api.Job;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Los cinco cierres del proceso sobre el BPMN real: cada camino deja la variable resultado y
 * RecordProcessResult la registra en solicitudes.resultado (y en cursos.estado cuando hay curso).
 */
class ProcessResultModelTest {
    private static final String REGISTRAR_PROPUESTA = "sid-F96A6968-13D6-4EA4-929E-69230127C208";
    private static final String ARCHIVAR_SOLICITUD = "sid-DA4178CD-28E4-4E74-A085-B6D7BE829D62";
    private static final String PREPARAR_CURSO = "sid-9DE766AD-6C6B-4DA4-A60E-24BECC4FC4DF";
    private static final String PREPARAR_MATERIALES = "sid-809C7C67-70EC-4018-BBF7-6A27DD12D4BB";
    private static final String RECIBIR_NOMINA = "sid-40B80C57-5A63-4D5C-A43B-710C8B140370";
    private static final String LISTA_DEFINITIVA = "sid-67285B9F-672D-4038-A765-E67245B387D1";
    private static final String EVALUAR_RECURSOS = "sid-F3381B5B-C506-4AC2-A09D-747447EFB31B";
    private static final String AULA_VIRTUAL = "sid-C3399A6A-261E-4D23-9AF8-F9F58D4A81CB";
    private static final String DEFINIR_DOCENTE = "sid-A3B3F53F-1D5E-42AA-AAC0-C69120F53E85";
    private static final String DESARROLLAR_CURSO = "sid-378C089F-415C-4EC6-B426-E2D989460B8C";
    private static final String ELABORAR_INFORME = "sid-8EE040F3-0293-488C-AE3F-3BD79DB56897";
    private static final String ENVIAR_INFORME = "sid-BA739FA3-D40A-4AFD-AD69-062B808ABF63";
    private static final String EQUIPAMIENTO = "sid-6AAC5E2F-BF84-4C7C-8963-7D1F6CBEF790";
    private static final String LIBERAR_AULA = "sid-CF527638-CD0A-48BF-9EF6-DDB557838793";
    private static final String LIBERAR_AULA_VIRTUAL = "sid-892710F9-B2AE-4002-82D0-131829D8FA29";
    private static final String LIBERAR_EQUIPAMIENTO = "sid-3DEB48C5-A36A-4EBF-A77C-150D02FC969E";

    private StandaloneInMemProcessEngineConfiguration config;
    private ProcessEngine engine;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private ManagementService managementService;

    @BeforeEach
    void setUp() throws SQLException {
        config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:resultado-modelo-" + System.nanoTime() + ";DB_CLOSE_DELAY=1000");
        config.setCustomPostDeployers(new ArrayList<>(List.of(new RulesDeployer())));
        engine = config.buildProcessEngine();
        engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/ProyectoCursos.bpmn20.xml")
                .addClasspathResource("rules/viability.drl")
                .deploy();
        runtimeService = engine.getRuntimeService();
        taskService = engine.getTaskService();
        managementService = engine.getManagementService();

        // Mismas tablas que usa RecordProcessResult, en la base del motor como en Flowable UI.
        sql("CREATE TABLE cursos (id BIGINT PRIMARY KEY, estado VARCHAR(40) NOT NULL)");
        sql("CREATE TABLE solicitudes (id BIGINT PRIMARY KEY, resultado VARCHAR(40), curso_id BIGINT)");

        IdentityService identityService = engine.getIdentityService();
        identityService.saveGroup(identityService.newGroup("Docentes"));
        identityService.saveUser(identityService.newUser("docente1"));
        identityService.createMembership("docente1", "Docentes");
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void elRechazoQuedaRegistradoAntesDeArchivar() throws SQLException {
        String instancia = iniciar(1L);
        registrarPropuesta(instancia);

        mensaje(instancia, "msgRechazo");

        // La pantalla de "Archivar solicitud" ya puede mostrar el motivo.
        tarea(instancia, ARCHIVAR_SOLICITUD);
        assertEquals("rechazado", runtimeService.getVariable(instancia, "resultado"));
        completar(instancia, ARCHIVAR_SOLICITUD, Map.of());
        assertTerminadaCon(instancia, "rechazado", 1L, null);
    }

    @Test
    void sinRespuestaSeCierraDespuesDeDosRecordatorios() throws SQLException {
        String instancia = iniciar(2L);
        registrarPropuesta(instancia);

        for (int vencimiento = 0; vencimiento < 3; vencimiento++) {
            dispararTimer(instancia);
        }

        assertEquals("cerrado_por_falta_de_respuesta", runtimeService.getVariable(instancia, "resultado"));
        completar(instancia, ARCHIVAR_SOLICITUD, Map.of());
        assertTerminadaCon(instancia, "cerrado_por_falta_de_respuesta", 2L, null);
    }

    @Test
    void conCincoCursosEnEjecucionNoEsViable() throws SQLException {
        for (long otro = 100; otro < 105; otro++) {
            runtimeService.setVariable(iniciar(otro), "cursoId", otro);
        }

        String instancia = iniciar(3L);

        assertTerminadaCon(instancia, "no_viable", 3L, null);
    }

    @Test
    void laCancelacionActualizaLaSolicitudYElCurso() throws SQLException {
        String instancia = llegarAPrepararCurso(4L, 40L);

        mensaje(instancia, "msgCancelacion");

        assertTerminadaCon(instancia, "cancelado", 4L, 40L);
    }

    @Test
    void elCursoQueTerminaQuedaFinalizado() throws SQLException {
        String instancia = llegarAPrepararCurso(5L, 50L);
        completar(instancia, PREPARAR_MATERIALES, Map.of());
        runtimeService.trigger(ejecucion(instancia, RECIBIR_NOMINA), Map.of("participantes", "Ana Pérez, Juan Gómez"));
        completar(instancia, LISTA_DEFINITIVA, Map.of());
        completar(instancia, EVALUAR_RECURSOS, Map.of("aulaFisica", false, "aulaVirtual", true, "equipamiento", false));
        completar(instancia, AULA_VIRTUAL, Map.of());
        // "Preparación completa" es asíncrona: cada rama que llega deja un job.
        ejecutarJobs(instancia);
        // Estados intermedios que ve la empresa (UpdateCourseState).
        assertEquals("confirmado", consultar("SELECT estado FROM cursos WHERE id = 50"));

        completar(instancia, DEFINIR_DOCENTE, Map.of("docenteSeleccionado", "docente1"));
        assertEquals("pendiente_de_inicio", consultar("SELECT estado FROM cursos WHERE id = 50"));
        dispararTimer(instancia);
        assertEquals("en_desarrollo", consultar("SELECT estado FROM cursos WHERE id = 50"));
        completar(instancia, DESARROLLAR_CURSO, Map.of());
        assertEquals("en_elaboracion_informe", consultar("SELECT estado FROM cursos WHERE id = 50"));
        completar(instancia, ELABORAR_INFORME, Map.of());
        completar(instancia, ENVIAR_INFORME, Map.of());

        assertTerminadaCon(instancia, "finalizado", 5L, 50L);
    }

    @Test
    void elFinNormalLiberaTodosLosRecursosReservados() throws SQLException {
        String instancia = llegarAPrepararCurso(7L, 70L);
        completar(instancia, PREPARAR_MATERIALES, Map.of());
        runtimeService.trigger(ejecucion(instancia, RECIBIR_NOMINA), Map.of(
                "participantes", "Ana Pérez, Juan Gómez"));
        completar(instancia, LISTA_DEFINITIVA, Map.of());
        completar(instancia, EVALUAR_RECURSOS, Map.of(
                "aulaFisica", true,
                "aulaVirtual", true,
                "equipamiento", true));
        assertEquals("Aula 1", runtimeService.getVariable(instancia, "aulaAsignada"));
        completar(instancia, AULA_VIRTUAL, Map.of());
        completar(instancia, EQUIPAMIENTO, Map.of());
        ejecutarJobs(instancia);
        completar(instancia, DEFINIR_DOCENTE, Map.of("docenteSeleccionado", "docente1"));
        dispararTimer(instancia);
        completar(instancia, DESARROLLAR_CURSO, Map.of());
        completar(instancia, ELABORAR_INFORME, Map.of());
        completar(instancia, ENVIAR_INFORME, Map.of());

        assertTerminadaCon(instancia, "finalizado", 7L, 70L);
        assertHistoricActivity(instancia, LIBERAR_AULA);
        assertHistoricActivity(instancia, LIBERAR_AULA_VIRTUAL);
        assertHistoricActivity(instancia, LIBERAR_EQUIPAMIENTO);
    }

    @Test
    void laUnionDePreparacionEsAsincrona() {
        String instancia = llegarAPrepararCurso(6L, 60L);
        completar(instancia, PREPARAR_MATERIALES, Map.of());

        // Completar la rama no ejecuta la unión en el mismo pedido: queda un job pendiente.
        assertEquals(1, managementService.createJobQuery().processInstanceId(instancia).count());
    }

    private String iniciar(long solicitudId) {
        sql("INSERT INTO solicitudes (id) VALUES (" + solicitudId + ")");
        Map<String, Object> variables = new HashMap<>();
        variables.put("solicitudId", solicitudId);
        variables.put("empresaNombre", "Tech Solutions S.A.");
        variables.put("tematica", "BPMN");
        return runtimeService.startProcessInstanceByMessage("msgSolicitud", variables).getId();
    }

    private void registrarPropuesta(String instancia) {
        completar(instancia, REGISTRAR_PROPUESTA, Map.of(
                "programa", "Modelado de procesos",
                "presupuesto", 150000L,
                "fechaInicioPosible", new Date(1_800_000_000_000L),
                "fechaFinPropuesta", new Date(1_802_000_000_000L)));
    }

    /**
     * Mueve una solicitud a "Preparar curso" con un curso ya creado. "Habilitar acceso" necesita
     * la base institucional real, así que acá se carga cursoId como lo dejaría esa tarea.
     */
    private String llegarAPrepararCurso(long solicitudId, long cursoId) {
        String instancia = iniciar(solicitudId);
        sql("INSERT INTO cursos (id, estado) VALUES (" + cursoId + ", 'en_preparacion')");
        runtimeService.setVariable(instancia, "cursoId", cursoId);
        runtimeService.setVariable(instancia, "fechaInicio", "2026-01-15T09:00:00");
        List<String> actuales = runtimeService.createExecutionQuery().processInstanceId(instancia).list().stream()
                .map(Execution::getActivityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(instancia)
                .moveActivityIdsToSingleActivityId(actuales, PREPARAR_CURSO)
                .changeState();
        return instancia;
    }

    private String tarea(String instancia, String clave) {
        return taskService.createTaskQuery().processInstanceId(instancia).taskDefinitionKey(clave).singleResult().getId();
    }

    private void completar(String instancia, String clave, Map<String, Object> variables) {
        taskService.complete(tarea(instancia, clave), variables);
    }

    private String ejecucion(String instancia, String actividad) {
        return runtimeService.createExecutionQuery().processInstanceId(instancia).activityId(actividad)
                .singleResult().getId();
    }

    private void mensaje(String instancia, String nombre) {
        Execution suscripta = runtimeService.createExecutionQuery().processInstanceId(instancia)
                .messageEventSubscriptionName(nombre).singleResult();
        runtimeService.messageEventReceived(nombre, suscripta.getId());
    }

    private void dispararTimer(String instancia) {
        Job timer = managementService.createTimerJobQuery().processInstanceId(instancia).singleResult();
        managementService.executeJob(managementService.moveTimerToExecutableJob(timer.getId()).getId());
    }

    private void ejecutarJobs(String instancia) {
        for (Job job : managementService.createJobQuery().processInstanceId(instancia).list()) {
            managementService.executeJob(job.getId());
        }
    }

    private void assertHistoricActivity(String instancia, String actividad) {
        assertEquals(1, engine.getHistoryService().createHistoricActivityInstanceQuery()
                .processInstanceId(instancia)
                .activityId(actividad)
                .count(), "Se ejecutó la actividad BPMN " + actividad);
    }

    private void assertTerminadaCon(String instancia, String resultado, long solicitudId, Long cursoId)
            throws SQLException {
        assertEquals(0, runtimeService.createProcessInstanceQuery().processInstanceId(instancia).count(),
                "La instancia debería haber terminado");
        assertEquals(resultado, consultar("SELECT resultado FROM solicitudes WHERE id = " + solicitudId));
        if (cursoId == null) {
            assertNull(consultar("SELECT curso_id FROM solicitudes WHERE id = " + solicitudId));
        } else {
            assertEquals(resultado, consultar("SELECT estado FROM cursos WHERE id = " + cursoId));
        }
    }

    private void sql(String sentencia) {
        try (Connection connection = config.getDataSource().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sentencia);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String consultar(String consulta) throws SQLException {
        try (Connection connection = config.getDataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultado = statement.executeQuery(consulta)) {
            return resultado.next() ? resultado.getString(1) : null;
        }
    }
}
