package flowable.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.flowable.engine.IdentityService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssignToLeastBusyAdministrativeTest {
    private ProcessEngine engine;
    private TaskService taskService;
    /** Lo que recibió la estrategia de desempate inyectada, en cada asignación. */
    private final List<List<String>> empatesRecibidos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        StandaloneInMemProcessEngineConfiguration config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:lista-" + System.nanoTime() + ";DB_CLOSE_DELAY=1000");
        config.setBeans(Map.of("asignador", new AssignToLeastBusyAdministrative(empatados -> {
            empatesRecibidos.add(empatados);
            return empatados.get(empatados.size() - 1);
        })));
        engine = config.buildProcessEngine();
        taskService = engine.getTaskService();
        engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/lista-definitiva-test.bpmn20.xml")
                .addClasspathResource("processes/lista-definitiva-desempate-test.bpmn20.xml")
                .deploy();

        // Mismos usuarios y grupos que FlowableSeed
        IdentityService identityService = engine.getIdentityService();
        identityService.saveGroup(identityService.newGroup("Administracion"));
        identityService.saveGroup(identityService.newGroup("Docentes"));
        for (String userId : new String[] {"jefeAdministrativo", "empleadoAdministrativo", "docente1"}) {
            identityService.saveUser(identityService.newUser(userId));
        }
        identityService.createMembership("jefeAdministrativo", "Administracion");
        identityService.createMembership("empleadoAdministrativo", "Administracion");
        identityService.createMembership("docente1", "Docentes");
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void asignaAlAdministrativoConMenosTareasAsignadas() {
        assignTasks("jefeAdministrativo", 1);
        assignTasks("empleadoAdministrativo", 2);

        assertEquals("jefeAdministrativo", startAndGetAssignee());
    }

    @Test
    void noCuentaLasTareasDelGrupoSinReclamar() {
        assignTasks("jefeAdministrativo", 1);
        for (int i = 0; i < 3; i++) {
            Task task = taskService.newTask();
            taskService.saveTask(task);
            taskService.addCandidateGroup(task.getId(), "Administracion");
        }

        assertEquals("empleadoAdministrativo", startAndGetAssignee());
    }

    @Test
    void enEmpateEligeAlAzarEntreLosMenosOcupados() {
        assignTasks("jefeAdministrativo", 1);
        assignTasks("empleadoAdministrativo", 1);

        assertTrue(Set.of("jefeAdministrativo", "empleadoAdministrativo").contains(startAndGetAssignee()));
    }

    @Test
    void enEmpateUsaLaEstrategiaConLosEmpatadosEnOrdenAlfabetico() {
        assignTasks("jefeAdministrativo", 1);
        assignTasks("empleadoAdministrativo", 1);

        String instanceId = engine.getRuntimeService().startProcessInstanceByKey("test-lista-definitiva-desempate").getId();

        assertEquals(List.of(List.of("empleadoAdministrativo", "jefeAdministrativo")), empatesRecibidos);
        assertEquals("jefeAdministrativo",
                taskService.createTaskQuery().processInstanceId(instanceId).singleResult().getAssignee());
    }

    @Test
    void soloConsideraMiembrosDeAdministracion() {
        // docente1 no tiene tareas, pero no es administrativo
        assignTasks("jefeAdministrativo", 1);
        assignTasks("empleadoAdministrativo", 1);

        assertTrue(Set.of("jefeAdministrativo", "empleadoAdministrativo").contains(startAndGetAssignee()));
    }

    @Test
    void lasTareasCompletadasNoCuentan() {
        assignTasks("jefeAdministrativo", 1);
        assignTasks("empleadoAdministrativo", 2);
        taskService.createTaskQuery().taskAssignee("empleadoAdministrativo").list()
                .forEach(task -> taskService.complete(task.getId()));

        assertEquals("empleadoAdministrativo", startAndGetAssignee());
    }

    @Test
    void sinIntegrantesDejaLaTareaSinAsignarPeroDisponibleParaElGrupo() {
        IdentityService identityService = engine.getIdentityService();
        identityService.deleteMembership("jefeAdministrativo", "Administracion");
        identityService.deleteMembership("empleadoAdministrativo", "Administracion");

        String instanceId = engine.getRuntimeService().startProcessInstanceByKey("test-lista-definitiva").getId();
        Task task = taskService.createTaskQuery().processInstanceId(instanceId).singleResult();

        assertNull(task.getAssignee());
        assertTrue(taskService.getIdentityLinksForTask(task.getId()).stream()
                .anyMatch(link -> "Administracion".equals(link.getGroupId())));
    }

    private void assignTasks(String userId, int amount) {
        for (int i = 0; i < amount; i++) {
            Task task = taskService.newTask();
            task.setAssignee(userId);
            taskService.saveTask(task);
        }
    }

    private String startAndGetAssignee() {
        String instanceId = engine.getRuntimeService().startProcessInstanceByKey("test-lista-definitiva").getId();
        return taskService.createTaskQuery().processInstanceId(instanceId).singleResult().getAssignee();
    }
}
