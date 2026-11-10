package flowable.proposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProposalDatesValidationTest {
    private ProcessEngine engine;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        StandaloneInMemProcessEngineConfiguration config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:propuesta-" + System.nanoTime() + ";DB_CLOSE_DELAY=1000");
        engine = config.buildProcessEngine();
        taskService = engine.getTaskService();
        engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/propuesta-validacion-test.bpmn20.xml")
                .deploy();
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void rechazaFechaFinalAnteriorYPermiteFechasIguales() {
        String processInstanceId = engine.getRuntimeService()
                .startProcessInstanceByKey("test-validacion-propuesta").getId();
        Task task = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
        assertEquals("jefeAdministrativo", task.getAssignee());

        Date startDate = Date.from(Instant.parse("2026-11-20T00:00:00Z"));
        Map<String, Object> variables = new HashMap<>();
        variables.put("presupuesto", 100L);
        variables.put("fechaInicioPosible", startDate);
        variables.put("fechaFinPropuesta", Date.from(Instant.parse("2026-11-19T00:00:00Z")));

        assertThrows(FlowableIllegalArgumentException.class, () -> taskService.complete(task.getId(), variables));
        assertEquals(1, taskService.createTaskQuery().taskId(task.getId()).count());

        variables.put("fechaFinPropuesta", startDate);
        taskService.complete(task.getId(), variables);
        assertEquals(0, taskService.createTaskQuery().taskId(task.getId()).count());
    }

    @Test
    void permiteFechaFinalPosterior() {
        String processInstanceId = engine.getRuntimeService()
                .startProcessInstanceByKey("test-validacion-propuesta").getId();
        Task task = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
        Date startDate = Date.from(Instant.parse("2026-11-20T00:00:00Z"));
        Map<String, Object> variables = new HashMap<>();
        variables.put("presupuesto", 100L);
        variables.put("fechaInicioPosible", startDate);
        variables.put("fechaFinPropuesta", Date.from(Instant.parse("2026-11-21T00:00:00Z")));

        taskService.complete(task.getId(), variables);

        assertEquals(0, taskService.createTaskQuery().taskId(task.getId()).count());
    }

    @Test
    void rechazaPresupuestoNegativoYPermiteCero() {
        String processInstanceId = engine.getRuntimeService()
                .startProcessInstanceByKey("test-validacion-propuesta").getId();
        Task task = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
        Date startDate = Date.from(Instant.parse("2026-11-20T00:00:00Z"));
        Map<String, Object> variables = new HashMap<>();
        variables.put("presupuesto", -1L);
        variables.put("fechaInicioPosible", startDate);
        variables.put("fechaFinPropuesta", startDate);

        assertThrows(FlowableIllegalArgumentException.class, () -> taskService.complete(task.getId(), variables));
        assertEquals(1, taskService.createTaskQuery().taskId(task.getId()).count());

        variables.put("presupuesto", 0L);
        taskService.complete(task.getId(), variables);
        assertEquals(0, taskService.createTaskQuery().taskId(task.getId()).count());
    }
}
