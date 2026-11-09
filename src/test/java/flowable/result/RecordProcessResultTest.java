package flowable.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecordProcessResultTest {
    private ProcessEngine engine;
    private ProcessEngineConfigurationImpl config;

    @BeforeEach
    void setUp() throws SQLException {
        StandaloneInMemProcessEngineConfiguration configuration = new StandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl("jdbc:h2:mem:resultado-" + System.nanoTime() + ";DB_CLOSE_DELAY=1000");
        engine = configuration.buildProcessEngine();
        config = configuration;
        engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/resultado-test.bpmn20.xml")
                .deploy();

        // Las tablas de la institución en la misma base del motor, como en Docker
        sql("CREATE TABLE cursos (id BIGINT PRIMARY KEY, estado VARCHAR(40) NOT NULL)");
        sql("CREATE TABLE solicitudes (id BIGINT PRIMARY KEY, resultado VARCHAR(40), curso_id BIGINT)");
        sql("INSERT INTO cursos VALUES (10, 'en_preparacion')");
        sql("INSERT INTO solicitudes VALUES (1, NULL, 10)");
        sql("INSERT INTO solicitudes VALUES (2, NULL, NULL)");
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void conCursoActualizaElCursoYLaSolicitud() throws SQLException {
        finish(variables(1L, 10L, "finalizado"));

        assertEquals("finalizado", query("SELECT estado FROM cursos WHERE id = 10"));
        assertEquals("finalizado", query("SELECT resultado FROM solicitudes WHERE id = 1"));
    }

    @Test
    void sinCursoSoloActualizaLaSolicitud() throws SQLException {
        // Rechazo, sin respuesta y no viable terminan antes de que se cree el curso
        finish(variables(2L, null, "rechazado"));

        assertEquals("rechazado", query("SELECT resultado FROM solicitudes WHERE id = 2"));
        assertEquals("en_preparacion", query("SELECT estado FROM cursos WHERE id = 10"));
    }

    @Test
    void sinResultadoTerminaSinTocarLaBase() throws SQLException {
        String instanceId = finish(variables(1L, 10L, null));

        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instanceId).count());
        assertNull(query("SELECT resultado FROM solicitudes WHERE id = 1"));
        assertEquals("en_preparacion", query("SELECT estado FROM cursos WHERE id = 10"));
    }

    @Test
    void unResultadoDesconocidoNoCierraLaInstancia() throws SQLException {
        String instanceId = startAtTask(variables(1L, 10L, "terminado"));

        assertThrows(FlowableException.class, () -> completeTask(instanceId));
        assertEquals(1, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instanceId).count());
        assertNull(query("SELECT resultado FROM solicitudes WHERE id = 1"));
    }

    @Test
    void siFallaElUpdateDelCursoNoQuedaNadaAMedias() throws SQLException {
        // La solicitud se actualiza primero; el curso falla por una restricción. No tiene que quedar
        // la solicitud cancelada con la instancia todavía activa.
        sql("ALTER TABLE cursos ADD CONSTRAINT estado_valido CHECK (estado <> 'cancelado')");
        String instanceId = startAtTask(variables(1L, 10L, "cancelado"));

        assertThrows(FlowableException.class, () -> completeTask(instanceId));
        assertNull(query("SELECT resultado FROM solicitudes WHERE id = 1"));
        assertEquals(1, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instanceId).count());
    }

    @Test
    void siElMotorFallaDespuesDelListenerNoSeActualizaNada() throws SQLException {
        // Otro listener de fin, que corre después de este, falla antes de que el motor escriba sus cambios
        engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/resultado-falla-test.bpmn20.xml")
                .deploy();
        String instanceId = engine.getRuntimeService()
                .startProcessInstanceByKey("test-resultado-falla", variables(1L, 10L, "finalizado")).getId();

        assertThrows(RuntimeException.class, () -> completeTask(instanceId));
        assertNull(query("SELECT resultado FROM solicitudes WHERE id = 1"));
        assertEquals("en_preparacion", query("SELECT estado FROM cursos WHERE id = 10"));
    }

    private String startAtTask(Map<String, Object> variables) {
        return engine.getRuntimeService().startProcessInstanceByKey("test-resultado", variables).getId();
    }

    private void completeTask(String instanceId) {
        TaskService taskService = engine.getTaskService();
        taskService.complete(taskService.createTaskQuery().processInstanceId(instanceId).singleResult().getId());
    }

    private String finish(Map<String, Object> variables) {
        String instanceId = startAtTask(variables);
        completeTask(instanceId);
        return instanceId;
    }

    private static Map<String, Object> variables(Long solicitudId, Long cursoId, String resultado) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("solicitudId", solicitudId);
        if (cursoId != null) {
            variables.put("cursoId", cursoId);
        }
        if (resultado != null) {
            variables.put("resultado", resultado);
        }
        return variables;
    }

    private void sql(String statement) throws SQLException {
        try (Connection connection = config.getDataSource().getConnection(); Statement st = connection.createStatement()) {
            st.execute(statement);
        }
    }

    private String query(String select) throws SQLException {
        try (Connection connection = config.getDataSource().getConnection(); Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(select)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
