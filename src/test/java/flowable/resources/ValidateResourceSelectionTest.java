package flowable.resources;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;

class ValidateResourceSelectionTest {
    private ProcessEngine engine;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        StandaloneInMemProcessEngineConfiguration config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:recursos-" + System.nanoTime() + ";DB_CLOSE_DELAY=1000");
        engine = config.buildProcessEngine();
        taskService = engine.getTaskService();
        engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/resources-validation-test.bpmn20.xml")
                .deploy();
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void rechazaLaTareaCuandoNoSeSeleccionaNingunRecurso() {
        Task task = startEvaluationTask();

        assertThrows(FlowableException.class, () -> taskService.complete(task.getId(), new HashMap<>()));
        assertEquals(1, taskService.createTaskQuery().taskId(task.getId()).count());
    }

    @Test
    void aceptaCadaRecursoPorSeparadoYCombinaciones() {
        for (String selectedResource : new String[] {"aulaFisica", "aulaVirtual", "equipamiento"}) {
            Task task = startEvaluationTask();
            assertDoesNotThrow(() -> taskService.complete(task.getId(), Map.of(selectedResource, true)));
        }

        Task task = startEvaluationTask();
        Map<String, Object> resources = Map.of(
                "aulaFisica", true,
                "aulaVirtual", true,
                "equipamiento", true);

        assertDoesNotThrow(() -> taskService.complete(task.getId(), resources));
    }

    private Task startEvaluationTask() {
        String processInstanceId = engine.getRuntimeService()
                .startProcessInstanceByKey("test-validacion-recursos").getId();
        return taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
    }
}