package flowable.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValidateReportTest {
    private ProcessEngine engine;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        StandaloneInMemProcessEngineConfiguration config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:informe-" + System.nanoTime() + ";DB_CLOSE_DELAY=1000");
        engine = config.buildProcessEngine();
        taskService = engine.getTaskService();
        engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/informe-validacion-test.bpmn20.xml")
                .deploy();
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void rechazaInformeAusenteOVacio() {
        String instancia = iniciar();
        Task tarea = tarea(instancia);

        for (Map<String, Object> variables : java.util.List.of(
                Map.<String, Object>of(),
                Map.<String, Object>of("informeResultados", ""),
                Map.<String, Object>of("informeResultados", "   "))) {
            FlowableIllegalArgumentException error = assertThrows(FlowableIllegalArgumentException.class,
                    () -> taskService.complete(tarea.getId(), variables));
            assertEquals("El informe de resultados es obligatorio.", error.getMessage());
            assertEquals(1, taskService.createTaskQuery().taskId(tarea.getId()).count());
        }
    }

    @Test
    void rechazaInformeQueNoEsTexto() {
        String instancia = iniciar();
        Task tarea = tarea(instancia);

        for (Object valor : java.util.List.of(123, true, java.util.List.of("texto"))) {
            FlowableIllegalArgumentException error = assertThrows(FlowableIllegalArgumentException.class,
                    () -> taskService.complete(tarea.getId(), Map.of("informeResultados", valor)));
            assertEquals("El informe de resultados es obligatorio.", error.getMessage());
            assertEquals(1, taskService.createTaskQuery().taskId(tarea.getId()).count());
        }
    }

    @Test
    void aceptaInformeConContenido() {
        String instancia = iniciar();
        Task tarea = tarea(instancia);

        taskService.complete(tarea.getId(), Map.of(
                "informeResultados", "El curso se dictó con normalidad."));

        assertEquals(0, taskService.createTaskQuery().taskId(tarea.getId()).count());
    }

    private String iniciar() {
        return engine.getRuntimeService().startProcessInstanceByKey("test-validacion-informe").getId();
    }

    private Task tarea(String instancia) {
        return taskService.createTaskQuery().processInstanceId(instancia).singleResult();
    }
}
