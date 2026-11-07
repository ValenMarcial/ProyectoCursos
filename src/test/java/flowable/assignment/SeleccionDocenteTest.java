package flowable.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.Map;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.IdentityService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SeleccionDocenteTest {
    private ProcessEngine engine;
    private RuntimeService runtimeService;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        StandaloneInMemProcessEngineConfiguration config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:seleccion-docente-" + System.nanoTime() + ";DB_CLOSE_DELAY=1000");
        engine = config.buildProcessEngine();
        runtimeService = engine.getRuntimeService();
        taskService = engine.getTaskService();

        engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/docente-seleccion-test.bpmn20.xml")
                .deploy();
        IdentityService identityService = engine.getIdentityService();
        identityService.saveGroup(identityService.newGroup("Docentes"));
        identityService.saveGroup(identityService.newGroup("Administracion"));
        for (String userId : new String[] {"jefeDeEquipo", "docente2", "administrativo"}) {
            identityService.saveUser(identityService.newUser(userId));
        }
        identityService.createMembership("jefeDeEquipo", "Docentes");
        identityService.createMembership("docente2", "Docentes");
        identityService.createMembership("administrativo", "Administracion");
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void elJefePuedeSeleccionarseEnElFormulario() {
        assertSeleccionPorFormulario("jefeDeEquipo");
    }

    @Test
    void elJefePuedeSeleccionarAOtroDocenteEnElFormulario() {
        assertSeleccionPorFormulario("docente2");
    }

    @Test
    void rechazaFormularioSinDocente() {
        String instanceId = iniciarProceso();
        Task task = definirDocente(instanceId);

        assertThrows(FlowableException.class,
                () -> engine.getFormService().submitTaskFormData(task.getId(), Collections.emptyMap()));
        assertEquals(1, taskService.createTaskQuery().taskId(task.getId()).count());
    }

    @Test
    void permiteCompletarDesdeUnaAplicacionConLaVariable() {
        String instanceId = iniciarProceso();
        Task task = definirDocente(instanceId);

        taskService.complete(task.getId(), Map.of("docenteSeleccionado", "jefeDeEquipo"));

        assertDocenteAsignado(instanceId, "jefeDeEquipo");
    }

    @Test
    void rechazaUsuarioDeOtroGrupoYPermiteCorregirElFormulario() {
        String instanceId = iniciarProceso();
        Task task = definirDocente(instanceId);

        FlowableException error = assertThrows(FlowableException.class,
                () -> engine.getFormService().submitTaskFormData(task.getId(),
                        Map.of("docenteSeleccionado", "administrativo")));
        assertEquals("El docente seleccionado debe pertenecer al grupo Docentes.", error.getMessage());
        assertEquals(1, taskService.createTaskQuery().taskId(task.getId()).count());
        assertFalse(runtimeService.hasVariable(instanceId, "docenteSeleccionado"));

        engine.getFormService().submitTaskFormData(task.getId(), Map.of("docenteSeleccionado", "docente2"));
        assertDocenteAsignado(instanceId, "docente2");
    }

    @Test
    void rechazaUsuarioInexistenteDesdeLaApi() {
        String instanceId = iniciarProceso();
        Task task = definirDocente(instanceId);

        FlowableException error = assertThrows(FlowableException.class,
                () -> taskService.complete(task.getId(), Map.of("docenteSeleccionado", "inexistente")));
        assertEquals("El docente seleccionado debe pertenecer al grupo Docentes.", error.getMessage());
        assertEquals(1, taskService.createTaskQuery().taskId(task.getId()).count());
        assertFalse(runtimeService.hasVariable(instanceId, "docenteSeleccionado"));
    }

    @Test
    void rechazaValoresInvalidosDesdeLaApi() {
        String instanceId = iniciarProceso();
        Task task = definirDocente(instanceId);
        for (Map<String, Object> variables : java.util.List.of(
                Collections.<String, Object>emptyMap(),
                Map.<String, Object>of("docenteSeleccionado", "  "),
                Map.<String, Object>of("docenteSeleccionado", 42))) {
            FlowableException error = assertThrows(FlowableException.class,
                    () -> taskService.complete(task.getId(), variables));
            assertEquals("Debe indicar el ID del docente seleccionado.", error.getMessage());
            assertEquals(1, taskService.createTaskQuery().taskId(task.getId()).count());
        }
    }

    private void assertSeleccionPorFormulario(String docente) {
        String instanceId = iniciarProceso();
        Task task = definirDocente(instanceId);
        assertEquals("jefeDeEquipo", task.getAssignee());
        assertFalse(taskService.getIdentityLinksForTask(task.getId()).stream()
                .anyMatch(link -> "Docentes".equals(link.getGroupId())));

        engine.getFormService().submitTaskFormData(task.getId(), Map.of("docenteSeleccionado", docente));

        assertDocenteAsignado(instanceId, docente);
    }

    private String iniciarProceso() {
        return runtimeService.startProcessInstanceByKey("test-seleccion-docente").getId();
    }

    private Task definirDocente(String instanceId) {
        return taskService.createTaskQuery()
                .processInstanceId(instanceId)
                .taskDefinitionKey("definirDocente")
                .singleResult();
    }

    private void assertDocenteAsignado(String instanceId, String docente) {
        assertEquals(docente, runtimeService.getVariable(instanceId, "docenteSeleccionado"));
        Task task = taskService.createTaskQuery()
                .processInstanceId(instanceId)
                .taskDefinitionKey("desarrollarCurso")
                .singleResult();
        assertEquals(docente, task.getAssignee());
    }
}
