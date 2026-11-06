package flowable.classroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReserveClassroomTest {
    private ProcessEngine engine;
    private RuntimeService runtimeService;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        StandaloneInMemProcessEngineConfiguration config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:aulas-" + System.nanoTime() + ";DB_CLOSE_DELAY=1000");
        engine = config.buildProcessEngine();
        runtimeService = engine.getRuntimeService();
        taskService = engine.getTaskService();
        engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/reserva-aula-test.bpmn20.xml")
                .deploy();
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void reservaAulasLibresEnOrden() {
        assertEquals("Aula 1", runtimeService.getVariable(start(), "aulaAsignada"));
        assertEquals("Aula 2", runtimeService.getVariable(start(), "aulaAsignada"));
    }

    @Test
    void sinAulasLibresLanzaElErrorSinAulas() {
        start();
        start();
        String tercera = start();

        assertEquals("sinAulasDisponibles", currentTask(tercera));
        assertFalse(runtimeService.hasVariable(tercera, "aulaAsignada"));
    }

    @Test
    void laCompensacionLiberaElAula() {
        String primera = start();
        start();

        taskService.complete(taskService.createTaskQuery().processInstanceId(primera).singleResult().getId());

        assertEquals("recursosLiberados", currentTask(primera));
        assertFalse(runtimeService.hasVariable(primera, "aulaAsignada"));
        assertEquals("Aula 1", runtimeService.getVariable(start(), "aulaAsignada"));
    }

    private String start() {
        return runtimeService.startProcessInstanceByKey("test-reserva-aula").getId();
    }

    private String currentTask(String instanceId) {
        return taskService.createTaskQuery().processInstanceId(instanceId).singleResult().getTaskDefinitionKey();
    }
}
