package flowable.proposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.Association;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.SequenceFlow;
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

    @Test
    void inicializaCanceladaAlEntrarEnPrepararCurso() {
        SubProcess prepararCurso = getPrepararCurso();

        FlowableListener listener = prepararCurso.getExecutionListeners().stream()
                .filter(candidate -> "start".equals(candidate.getEvent()))
                .findFirst()
                .orElseThrow();

        assertEquals("${execution.setVariable('cancelada', false)}", listener.getImplementation());
        assertEquals("expression", listener.getImplementationType());
    }

    @Test
    void configuraLasCondicionesDelGatewayDeRecursos() {
        SubProcess prepararCurso = getPrepararCurso();
        UserTask evaluarRecursos = (UserTask) prepararCurso
                .getFlowElement("sid-F3381B5B-C506-4AC2-A09D-747447EFB31B");

        assertEquals(1, evaluarRecursos.getTaskListeners().size());
        assertEquals("complete", evaluarRecursos.getTaskListeners().get(0).getEvent());
        assertEquals("flowable.resources.ValidateResourceSelection",
                evaluarRecursos.getTaskListeners().get(0).getImplementation());
        assertEquals(3, evaluarRecursos.getFormProperties().size());
        assertEquals("aulaFisica", evaluarRecursos.getFormProperties().get(0).getId());
        assertEquals("boolean", evaluarRecursos.getFormProperties().get(0).getType());
        assertEquals("aulaVirtual", evaluarRecursos.getFormProperties().get(1).getId());
        assertEquals("boolean", evaluarRecursos.getFormProperties().get(1).getType());
        assertEquals("equipamiento", evaluarRecursos.getFormProperties().get(2).getId());
        assertEquals("boolean", evaluarRecursos.getFormProperties().get(2).getType());

        assertCondition(prepararCurso, "sid-19857014-988C-474C-919E-156D839EBF11", "${aulaFisica}");
        assertCondition(prepararCurso, "sid-26008547-815D-4F4A-BC87-8B1AE4E49010", "${aulaVirtual}");
        assertCondition(prepararCurso, "sid-136BD651-1667-4CC4-8A87-10A4944F19B0", "${equipamiento}");
    }

        @Test
        void conectaLasTareasDeAulaConSusDelegates() {
        SubProcess prepararCurso = getPrepararCurso();

        assertServiceTaskClass(prepararCurso, "sid-065649F8-9F01-4C34-B290-C37474CC479D",
            "flowable.classroom.ReserveClassroom");
        assertServiceTaskClass(prepararCurso, "sid-CF527638-CD0A-48BF-9EF6-DDB557838793",
            "flowable.classroom.ReleaseClassroom");
        }

        @Test
        void conectaLosEventosDeCompensacionConSusTareas() {
        SubProcess prepararCurso = getPrepararCurso();

        assertCompensationAssociation(prepararCurso, "sid-6EF58547-6206-4065-BC46-BDE7A86300E7",
            "sid-CF527638-CD0A-48BF-9EF6-DDB557838793");
        assertCompensationAssociation(prepararCurso, "sid-79D40E03-33A5-476E-AC89-3F44F73680A0",
            "sid-892710F9-B2AE-4002-82D0-131829D8FA29");
        assertCompensationAssociation(prepararCurso, "sid-0FB02D1D-DFB1-4222-BA0E-6D58C0E87619",
            "sid-3DEB48C5-A36A-4EBF-A77C-150D02FC969E");

        assertCompensationTask(prepararCurso, "sid-CF527638-CD0A-48BF-9EF6-DDB557838793");
        assertCompensationTask(prepararCurso, "sid-892710F9-B2AE-4002-82D0-131829D8FA29");
        assertCompensationTask(prepararCurso, "sid-3DEB48C5-A36A-4EBF-A77C-150D02FC969E");
        }

        private SubProcess getPrepararCurso() {
        ProcessDefinition processDefinition = engine.getRepositoryService()
            .createProcessDefinitionQuery()
            .processDefinitionKey("process-proyecto-curso")
            .singleResult();
        BpmnModel model = engine.getRepositoryService().getBpmnModel(processDefinition.getId());
        return (SubProcess) model.getMainProcess().getFlowElement("sid-9DE766AD-6C6B-4DA4-A60E-24BECC4FC4DF");
        }

        private static void assertServiceTaskClass(SubProcess process, String taskId, String expectedClass) {
        ServiceTask task = (ServiceTask) process.getFlowElement(taskId);
        assertEquals("class", task.getImplementationType());
        assertEquals(expectedClass, task.getImplementation());
        }

        private static void assertCompensationAssociation(SubProcess process, String sourceId, String targetId) {
        assertTrue(process.getArtifacts().stream()
            .filter(Association.class::isInstance)
            .map(Association.class::cast)
            .anyMatch(association -> sourceId.equals(association.getSourceRef())
                && targetId.equals(association.getTargetRef())),
            "Asociación de compensación " + sourceId + " -> " + targetId);
        }

        private static void assertCompensationTask(SubProcess process, String taskId) {
        Activity task = (Activity) process.getFlowElement(taskId);
        assertTrue(task.isForCompensation(), "Tarea de compensación " + task.getName());
        }

    private static void assertAssignee(BpmnModel model, String taskId, String expectedAssignee) {
        UserTask task = (UserTask) model.getMainProcess().getFlowElement(taskId);
        assertEquals(expectedAssignee, task.getAssignee(), "Responsable de " + task.getName());
    }

    private static void assertCandidateGroup(SubProcess process, String taskId) {
        UserTask task = (UserTask) process.getFlowElement(taskId);
        assertTrue(task.getCandidateGroups().contains("Administracion"), "Grupo candidato de " + task.getName());
    }

    private static void assertCondition(SubProcess process, String flowId, String expectedCondition) {
        SequenceFlow flow = (SequenceFlow) process.getFlowElement(flowId);
        assertEquals(expectedCondition, flow.getConditionExpression());
    }
}