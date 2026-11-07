package flowable.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flowable.engine.IdentityService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CaptureDelegatedTeacherTest {
    private ProcessEngine engine;
    private RuntimeService runtimeService;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        StandaloneInMemProcessEngineConfiguration config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:delegacion-docente-" + System.nanoTime() + ";DB_CLOSE_DELAY=1000");
        engine = config.buildProcessEngine();
        runtimeService = engine.getRuntimeService();
        taskService = engine.getTaskService();

        engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/docente-delegacion-test.bpmn20.xml")
                .deploy();

        IdentityService identityService = engine.getIdentityService();
        identityService.saveGroup(identityService.newGroup("Docentes"));
        for (String userId : new String[] {"jefeDeEquipo", "docente1", "docente2"}) {
            identityService.saveUser(identityService.newUser(userId));
            identityService.createMembership(userId, "Docentes");
        }
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void delegacionGuardaElDocenteYLoAsignaAlDesarrolloDelCurso() {
        String processInstanceId = runtimeService.startProcessInstanceByKey("test-delegacion-docente").getId();
        Task definirDocente = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey("definirDocente")
                .singleResult();

        assertEquals("jefeDeEquipo", definirDocente.getAssignee());
        assertTrue(taskService.getIdentityLinksForTask(definirDocente.getId()).stream()
                .anyMatch(link -> "Docentes".equals(link.getGroupId())));

        taskService.delegateTask(definirDocente.getId(), "docente2");

        assertEquals("docente2", runtimeService.getVariable(processInstanceId, "docenteSeleccionado"));
        taskService.resolveTask(definirDocente.getId());
        taskService.complete(definirDocente.getId());

        Task desarrollarCurso = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey("desarrollarCurso")
                .singleResult();
        assertEquals("docente2", desarrollarCurso.getAssignee());
    }
}
