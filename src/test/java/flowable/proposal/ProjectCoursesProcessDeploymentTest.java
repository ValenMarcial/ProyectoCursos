package flowable.proposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.SubProcess;
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
        assertAssignee(model, "sid-DA4178CD-28E4-4E74-A085-B6D7BE829D62", "jefeAdministrativo");
        assertAssignee(model, "sid-BA739FA3-D40A-4AFD-AD69-062B808ABF63", "jefeAdministrativo");
        assertAssignee(model, "sid-8EE040F3-0293-488C-AE3F-3BD79DB56897", "jefeDeEquipo");
        assertAssignee(model, "sid-A3B3F53F-1D5E-42AA-AAC0-C69120F53E85", "jefeDeEquipo");
        assertAssignee(model, "sid-378C089F-415C-4EC6-B426-E2D989460B8C", "${docenteSeleccionado}");
    }

    @Test
    void elModeloRealDefineElDocenteConUnFormularioObligatorio() {
        ProcessDefinition definition = engine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionKey("process-proyecto-curso").singleResult();
        BpmnModel model = engine.getRepositoryService().getBpmnModel(definition.getId());
        UserTask task = (UserTask) model.getMainProcess()
                .getFlowElement("sid-A3B3F53F-1D5E-42AA-AAC0-C69120F53E85");

        assertTrue(task.getCandidateGroups().isEmpty());
        assertEquals(1, task.getTaskListeners().size());
        assertEquals("complete", task.getTaskListeners().get(0).getEvent());
        assertEquals("flowable.assignment.ValidateSelectedTeacher", task.getTaskListeners().get(0).getImplementation());
        assertEquals(1, task.getFormProperties().size());
        assertEquals("docenteSeleccionado", task.getFormProperties().get(0).getId());
        assertEquals("string", task.getFormProperties().get(0).getType());
        assertTrue(task.getFormProperties().get(0).isRequired());
    }

    @Test
    void asignaAdministracionComoGrupoCandidatoDeLasTareasDePreparacion() {
        ProcessDefinition processDefinition = engine.getRepositoryService()
                .createProcessDefinitionQuery()
                .processDefinitionKey("process-proyecto-curso")
                .singleResult();
        BpmnModel model = engine.getRepositoryService().getBpmnModel(processDefinition.getId());
        SubProcess prepararCurso = (SubProcess) model.getMainProcess().getFlowElement("sid-9DE766AD-6C6B-4DA4-A60E-24BECC4FC4DF");

        assertCandidateGroup(prepararCurso, "sid-67285B9F-672D-4038-A765-E67245B387D1");
        assertCandidateGroup(prepararCurso, "sid-809C7C67-70EC-4018-BBF7-6A27DD12D4BB");
        assertCandidateGroup(prepararCurso, "sid-F3381B5B-C506-4AC2-A09D-747447EFB31B");
        assertCandidateGroup(prepararCurso, "sid-C3399A6A-261E-4D23-9AF8-F9F58D4A81CB");
        assertCandidateGroup(prepararCurso, "sid-6AAC5E2F-BF84-4C7C-8963-7D1F6CBEF790");
        assertCandidateGroup(prepararCurso, "sid-3DEB48C5-A36A-4EBF-A77C-150D02FC969E");
    }

    private static void assertAssignee(BpmnModel model, String taskId, String expectedAssignee) {
        UserTask task = (UserTask) model.getMainProcess().getFlowElement(taskId);
        assertEquals(expectedAssignee, task.getAssignee(), "Responsable de " + task.getName());
    }

    private static void assertCandidateGroup(SubProcess process, String taskId) {
        UserTask task = (UserTask) process.getFlowElement(taskId);
        assertTrue(task.getCandidateGroups().contains("Administracion"), "Grupo candidato de " + task.getName());
    }
}