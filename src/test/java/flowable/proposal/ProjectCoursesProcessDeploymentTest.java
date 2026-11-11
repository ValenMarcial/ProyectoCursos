package flowable.proposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Association;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.CompensateEventDefinition;
import org.flowable.bpmn.model.ErrorEventDefinition;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.GraphicInfo;
import org.flowable.bpmn.model.ScriptTask;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.ThrowEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.engine.impl.rules.RulesDeployer;
import org.flowable.task.api.Task;
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

        ScriptTask liberarEquipamiento = (ScriptTask) prepararCurso
                .getFlowElement("sid-3DEB48C5-A36A-4EBF-A77C-150D02FC969E");
        assertTrue(liberarEquipamiento.isForCompensation());
        assertEquals("println \"Equipamiento Liberado: \" + execution.processInstanceId",
                liberarEquipamiento.getScript());
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
    void marcaCanceladaAlFinalizarLaRamaDeCancelacion() {
        ProcessDefinition definition = engine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionKey("process-proyecto-curso").singleResult();
        BpmnModel model = engine.getRepositoryService().getBpmnModel(definition.getId());
        SubProcess prepararCurso = getPrepararCurso();
        SubProcess cancelacion = (SubProcess) prepararCurso
                .getFlowElement("sid-98039E66-3765-4C9A-8061-7280782D1D67");
        ScriptTask marcarCancelada = (ScriptTask) cancelacion
                .getFlowElement("sid-2FCA85E9-D3B7-4DBB-9C3A-83CBA2BAA625");
        SequenceFlow haciaMarcarCancelada = (SequenceFlow) cancelacion
                .getFlowElement("sid-08271985-A006-46DC-820A-3A8BD93CEB20");
        SequenceFlow haciaFin = (SequenceFlow) cancelacion
                .getFlowElement("sid-A826EAB1-FD26-47C1-B8AC-689102C64CB3");

        GraphicInfo tarea = model.getGraphicInfo("sid-2FCA85E9-D3B7-4DBB-9C3A-83CBA2BAA625");
        GraphicInfo subproceso = model.getGraphicInfo("sid-98039E66-3765-4C9A-8061-7280782D1D67");
        assertTrue(tarea.getX() >= subproceso.getX());
        assertTrue(tarea.getY() >= subproceso.getY());
        assertTrue(tarea.getX() + tarea.getWidth() <= subproceso.getX() + subproceso.getWidth());
        assertTrue(tarea.getY() + tarea.getHeight() <= subproceso.getY() + subproceso.getHeight());
        assertEquals(2, model.getFlowLocationGraphicInfo(haciaMarcarCancelada.getId()).size());
        assertEquals(2, model.getFlowLocationGraphicInfo(haciaFin.getId()).size());
        assertEquals("groovy", marcarCancelada.getScriptFormat());
        assertEquals("execution.setVariable(\"cancelada\", true)", marcarCancelada.getScript());
        assertEquals("sid-3329D761-38E5-49B6-99FE-711F82A01284", haciaMarcarCancelada.getSourceRef());
        assertEquals("sid-2FCA85E9-D3B7-4DBB-9C3A-83CBA2BAA625", haciaMarcarCancelada.getTargetRef());
        assertEquals("sid-2FCA85E9-D3B7-4DBB-9C3A-83CBA2BAA625", haciaFin.getSourceRef());
        assertEquals("sid-D2B77839-7DFB-495D-B2E2-43239376D0CC", haciaFin.getTargetRef());
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
            BpmnModel model = getProcessModel();

            assertCompensationAssociation(prepararCurso, "sid-6EF58547-6206-4065-BC46-BDE7A86300E7",
                "sid-CF527638-CD0A-48BF-9EF6-DDB557838793");
        assertCompensationAssociation(prepararCurso, "sid-79D40E03-33A5-476E-AC89-3F44F73680A0",
            "sid-892710F9-B2AE-4002-82D0-131829D8FA29");
        assertCompensationAssociation(prepararCurso, "sid-0FB02D1D-DFB1-4222-BA0E-6D58C0E87619",
            "sid-3DEB48C5-A36A-4EBF-A77C-150D02FC969E");

        assertCompensationTask(prepararCurso, "sid-CF527638-CD0A-48BF-9EF6-DDB557838793");
        assertCompensationTask(prepararCurso, "sid-892710F9-B2AE-4002-82D0-131829D8FA29");
        assertCompensationTask(prepararCurso, "sid-3DEB48C5-A36A-4EBF-A77C-150D02FC969E");

        assertCompensationActivityRef(prepararCurso, "sid-31835240-F72D-4525-9FCE-6428CE669ED3",
                "sid-065649F8-9F01-4C34-B290-C37474CC479D");
        assertCompensationActivityRef(prepararCurso, "sid-CE2ED1CB-BB53-499C-AB58-347BB0FA0C80",
                "sid-C3399A6A-261E-4D23-9AF8-F9F58D4A81CB");
        assertCompensationActivityRef(prepararCurso, "sid-3329D761-38E5-49B6-99FE-711F82A01284",
                "sid-6AAC5E2F-BF84-4C7C-8963-7D1F6CBEF790");
        assertCompensationActivityRef(model.getMainProcess(), "sid-88F847B3-5FE2-4251-A612-120F9DD46CA9",
                "sid-065649F8-9F01-4C34-B290-C37474CC479D");
        assertCompensationActivityRef(model.getMainProcess(), "sid-CAF881B2-93D6-479B-9C01-806B1059E125",
                "sid-C3399A6A-261E-4D23-9AF8-F9F58D4A81CB");
        assertCompensationActivityRef(model.getMainProcess(), "sid-98B02508-C619-4111-AA31-8FC54D5F85B2",
                "sid-6AAC5E2F-BF84-4C7C-8963-7D1F6CBEF790");
        }

    @Test
    void configuraCapturaCompensacionYNotificacionParaSinAulas() {
        BpmnModel model = getProcessModel();
        SubProcess prepararCurso = getPrepararCurso();
        BoundaryEvent capturaEnReserva = (BoundaryEvent) prepararCurso
                .getFlowElement("sid-7A782D3B-FA48-4B06-9E9D-22C32BD6F574");
        BoundaryEvent capturaEnPreparacion = (BoundaryEvent) model.getMainProcess()
                .getFlowElement("sid-6931943F-5BD2-45CD-BE56-52DE64896B0C");
        EndEvent propagarError = (EndEvent) prepararCurso
                .getFlowElement("sid-481F402E-5806-475E-8AB2-1148DE4E7B9C");
        ScriptTask notificarCierre = (ScriptTask) model.getMainProcess()
                .getFlowElement("sid-5F2866C1-0E11-4CC6-BD34-2875F370E0D1");
        SequenceFlow rutaAlCierre = (SequenceFlow) model.getMainProcess()
                .getFlowElement("sid-E13FA448-268A-485E-B2D7-0A4F47B0CA58");

        assertEquals("sid-065649F8-9F01-4C34-B290-C37474CC479D", capturaEnReserva.getAttachedToRefId());
        assertEquals("errorSIN_AULAS", errorDefinition(capturaEnReserva).getErrorCode());
        assertCompensationActivityRef(prepararCurso, "sid-1B42D920-0C4F-482C-9C3B-99E980DF50F1",
                "sid-065649F8-9F01-4C34-B290-C37474CC479D");
        assertCompensationActivityRef(prepararCurso, "sid-AD35AD31-1F32-478B-BEBE-CA591851B965",
                "sid-C3399A6A-261E-4D23-9AF8-F9F58D4A81CB");
        assertCompensationActivityRef(prepararCurso, "sid-3215FB81-B8CC-4C88-B29E-7E3B40FB7A4E",
                "sid-6AAC5E2F-BF84-4C7C-8963-7D1F6CBEF790");
        assertEquals("errorSIN_AULAS", errorDefinition(propagarError).getErrorCode());
        assertEquals("sid-9DE766AD-6C6B-4DA4-A60E-24BECC4FC4DF",
                capturaEnPreparacion.getAttachedToRefId());
        assertEquals("errorSIN_AULAS", errorDefinition(capturaEnPreparacion).getErrorCode());
        assertEquals("motivoCierre", errorDefinition(capturaEnPreparacion).getErrorVariableName());
        assertEquals(capturaEnPreparacion.getId(), rutaAlCierre.getSourceRef());
        assertEquals(notificarCierre.getId(), rutaAlCierre.getTargetRef());
        assertFlow(prepararCurso, "sid-C1AD5A67-E90B-46B4-80B1-E58A31C5774B",
                capturaEnReserva.getId(), "sid-1B42D920-0C4F-482C-9C3B-99E980DF50F1");
        assertFlow(prepararCurso, "sid-FEB357E8-1D07-4C42-92CB-572D8F4F5ED0",
                "sid-1B42D920-0C4F-482C-9C3B-99E980DF50F1", "sid-AD35AD31-1F32-478B-BEBE-CA591851B965");
        assertFlow(prepararCurso, "sid-61EABF9D-A1D6-4500-85D4-2F24C546B443",
                "sid-AD35AD31-1F32-478B-BEBE-CA591851B965", "sid-3215FB81-B8CC-4C88-B29E-7E3B40FB7A4E");
        assertFlow(prepararCurso, "sid-0CA04C12-36C6-46F0-BA41-799C1CE9B7E2",
                "sid-3215FB81-B8CC-4C88-B29E-7E3B40FB7A4E", propagarError.getId());
        assertEquals("if (execution.hasVariable(\"motivoCierre\")) { println \"Curso cancelado por falta de aulas disponibles: \" + execution.processInstanceId } else { println \"Curso cancelado: \" + execution.processInstanceId }; execution.setVariable(\"resultado\", \"cancelado\")",
                notificarCierre.getScript());
        assertTrue(model.getGraphicInfo(capturaEnReserva.getId()) != null);
        assertTrue(model.getGraphicInfo(capturaEnPreparacion.getId()) != null);
        assertTrue(model.getFlowLocationGraphicInfo(rutaAlCierre.getId()).size() >= 2);
    }

    @Test
    void ejecutaSinAulasEnElBpmnRealYFinalizaNotificandoElCierre() {
        String primera = iniciarSolicitud();
        String segunda = iniciarSolicitud();
        String tercera = iniciarSolicitud();
        engine.getRuntimeService().setVariable(primera, "aulaAsignada", "Aula 1");
        engine.getRuntimeService().setVariable(segunda, "aulaAsignada", "Aula 2");

        Task tareaActiva = engine.getTaskService().createTaskQuery()
                .processInstanceId(tercera)
                .singleResult();
        engine.getRuntimeService().createChangeActivityStateBuilder()
                .processInstanceId(tercera)
                .moveActivityIdTo(tareaActiva.getTaskDefinitionKey(),
                        "sid-C58276F7-2D24-45DB-B7DA-8FB3C5FE7050")
                .changeState();
        Task evaluarRecursos = engine.getTaskService().createTaskQuery()
                .processInstanceId(tercera)
                .taskDefinitionKey("sid-F3381B5B-C506-4AC2-A09D-747447EFB31B")
                .singleResult();
        engine.getTaskService().complete(evaluarRecursos.getId(), Map.of(
                "aulaFisica", true,
                "aulaVirtual", false,
                "equipamiento", false));

        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(tercera)
                .count());
        assertEquals("cancelado", engine.getHistoryService().createHistoricVariableInstanceQuery()
                .processInstanceId(tercera)
                .variableName("resultado")
                .singleResult()
                .getValue());
        assertTrue(engine.getHistoryService().createHistoricVariableInstanceQuery()
                .processInstanceId(tercera)
                .variableName("motivoCierre")
                .count() > 0);
        assertHistoricActivity(tercera, "sid-7A782D3B-FA48-4B06-9E9D-22C32BD6F574");
        assertHistoricActivity(tercera, "sid-1B42D920-0C4F-482C-9C3B-99E980DF50F1");
        assertHistoricActivity(tercera, "sid-AD35AD31-1F32-478B-BEBE-CA591851B965");
        assertHistoricActivity(tercera, "sid-3215FB81-B8CC-4C88-B29E-7E3B40FB7A4E");
        assertHistoricActivity(tercera, "sid-481F402E-5806-475E-8AB2-1148DE4E7B9C");
        assertHistoricActivity(tercera, "sid-6931943F-5BD2-45CD-BE56-52DE64896B0C");
        assertEquals(0, engine.getHistoryService().createHistoricActivityInstanceQuery()
                .processInstanceId(tercera)
                .activityId("sid-CF527638-CD0A-48BF-9EF6-DDB557838793")
                .count());
    }

    private String iniciarSolicitud() {
        return engine.getRuntimeService().startProcessInstanceByMessage("msgSolicitud", Map.of())
                .getId();
    }

    private void assertHistoricActivity(String processInstanceId, String activityId) {
        assertEquals(1, engine.getHistoryService().createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityId(activityId)
                .count(), "Se ejecutó la actividad BPMN " + activityId);
    }

    private static ErrorEventDefinition errorDefinition(BoundaryEvent event) {
        return event.getEventDefinitions().stream()
                .filter(ErrorEventDefinition.class::isInstance)
                .map(ErrorEventDefinition.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static ErrorEventDefinition errorDefinition(EndEvent event) {
        return event.getEventDefinitions().stream()
                .filter(ErrorEventDefinition.class::isInstance)
                .map(ErrorEventDefinition.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static void assertFlow(SubProcess process, String flowId, String sourceId, String targetId) {
        SequenceFlow flow = (SequenceFlow) process.getFlowElement(flowId);
        assertEquals(sourceId, flow.getSourceRef());
        assertEquals(targetId, flow.getTargetRef());
    }

        private SubProcess getPrepararCurso() {
        return (SubProcess) getProcessModel().getMainProcess()
                .getFlowElement("sid-9DE766AD-6C6B-4DA4-A60E-24BECC4FC4DF");
        }

        private BpmnModel getProcessModel() {
        ProcessDefinition processDefinition = engine.getRepositoryService()
            .createProcessDefinitionQuery()
            .processDefinitionKey("process-proyecto-curso")
            .singleResult();
        return engine.getRepositoryService().getBpmnModel(processDefinition.getId());
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

        private static void assertCompensationActivityRef(SubProcess process, String eventId, String activityId) {
        ThrowEvent event = (ThrowEvent) process.getFlowElement(eventId);
        assertCompensationActivityRef(event, activityId);
        }

        private static void assertCompensationActivityRef(org.flowable.bpmn.model.Process process, String eventId,
                String activityId) {
        ThrowEvent event = (ThrowEvent) process.getFlowElement(eventId);
        assertCompensationActivityRef(event, activityId);
        }

        private static void assertCompensationActivityRef(ThrowEvent event, String activityId) {
        CompensateEventDefinition definition = event.getEventDefinitions().stream()
                .filter(CompensateEventDefinition.class::isInstance)
                .map(CompensateEventDefinition.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(activityId, definition.getActivityRef(), "Actividad compensada por " + event.getName());
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