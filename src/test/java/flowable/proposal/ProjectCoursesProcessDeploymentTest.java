package flowable.proposal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.engine.impl.rules.RulesDeployer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProjectCoursesProcessDeploymentTest {
    private ProcessEngine engine;

    @BeforeEach
    void setUp() {
        StandaloneInMemProcessEngineConfiguration config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:proyecto-cursos-bpmn-" + System.nanoTime() + ";DB_CLOSE_DELAY=1000");
        config.setCustomPostDeployers(new ArrayList<>(List.of(new RulesDeployer())));
        engine = config.buildProcessEngine();
        engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/ProyectoCursos.bpmn20.xml")
                .addClasspathResource("rules/viability.drl")
                .deploy();
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void despliegaElProcesoBase() {
        assertEquals(1, engine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionKey("process-proyecto-curso")
                .count());
    }

    @Test
    void asignaLosResponsablesConfiguradosEnLasTareas() {
        ProcessDefinition processDefinition = engine.getRepositoryService()
                .createProcessDefinitionQuery()
                .processDefinitionKey("process-proyecto-curso")
                .singleResult();
        BpmnModel model = engine.getRepositoryService().getBpmnModel(processDefinition.getId());

        assertAssignee(model, "sid-BE500ABC-83C7-4A8A-9C83-23183950A2F6", "jefeAdministrativo");
        assertAssignee(model, "sid-BA739FA3-D40A-4AFD-AD69-062B808ABF63", "jefeAdministrativo");
        assertAssignee(model, "sid-8EE040F3-0293-488C-AE3F-3BD79DB56897", "jefeDeEquipo");
    }

    private static void assertAssignee(BpmnModel model, String taskId, String expectedAssignee) {
        UserTask task = (UserTask) model.getMainProcess().getFlowElement(taskId);
        assertEquals(expectedAssignee, task.getAssignee(), "Responsable de " + task.getName());
    }
}