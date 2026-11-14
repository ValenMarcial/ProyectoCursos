package flowable.proposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
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
import org.flowable.bpmn.model.TerminateEventDefinition;
import org.flowable.bpmn.model.ThrowEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.engine.impl.rules.RulesDeployer;
import org.flowable.job.api.Job;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProjectCoursesProcessDeploymentTest {
    private static final String PREPARAR_CURSO = "sid-9DE766AD-6C6B-4DA4-A60E-24BECC4FC4DF";
    private static final String EVALUAR_RECURSOS = "sid-F3381B5B-C506-4AC2-A09D-747447EFB31B";
    private static final String RESERVAR_AULA = "sid-065649F8-9F01-4C34-B290-C37474CC479D";
    private static final String AULA_VIRTUAL = "sid-C3399A6A-261E-4D23-9AF8-F9F58D4A81CB";
    private static final String EQUIPAMIENTO = "sid-6AAC5E2F-BF84-4C7C-8963-7D1F6CBEF790";
    private static final String LIBERAR_AULA = "sid-CF527638-CD0A-48BF-9EF6-DDB557838793";
    private static final String LIBERAR_AULA_VIRTUAL = "sid-892710F9-B2AE-4002-82D0-131829D8FA29";
    private static final String LIBERAR_EQUIPAMIENTO = "sid-3DEB48C5-A36A-4EBF-A77C-150D02FC969E";

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
        void persisteElRechazoDespuesDeMostrarloEnArchivarSolicitud() throws SQLException {
                prepararTablasDeResultados();
                String instancia = iniciarPropuesta();
                String ejecucion = engine.getRuntimeService().createExecutionQuery().processInstanceId(instancia)
                                .messageEventSubscriptionName("msgRechazo").singleResult().getId();
                engine.getRuntimeService().messageEventReceived("msgRechazo", ejecucion);

                assertResultadoAntesDeArchivar(instancia, "rechazado");
                completarTarea(instancia, "sid-DA4178CD-28E4-4E74-A085-B6D7BE829D62", Map.of());
                assertEquals("rechazado", consultarResultado("SELECT resultado FROM solicitudes WHERE id = 1"));
                assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instancia).count());
        }

        @Test
        void persisteLaFaltaDeRespuestaDespuesDeDosRecordatorios() throws SQLException {
                prepararTablasDeResultados();
                String instancia = iniciarPropuesta();
                for (int intento = 0; intento < 3; intento++) {
                        Job temporizador = engine.getManagementService().createTimerJobQuery()
                                        .processInstanceId(instancia).singleResult();
                        Job ejecutable = engine.getManagementService().moveTimerToExecutableJob(temporizador.getId());
                        engine.getManagementService().executeJob(ejecutable.getId());
                }

                assertEquals(2, engine.getRuntimeService().getVariable(instancia, "recordatoriosEnviados"));
                assertResultadoAntesDeArchivar(instancia, "cerrado_por_falta_de_respuesta");
                completarTarea(instancia, "sid-DA4178CD-28E4-4E74-A085-B6D7BE829D62", Map.of());
                assertEquals("cerrado_por_falta_de_respuesta", consultarResultado("SELECT resultado FROM solicitudes WHERE id = 1"));
                assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instancia).count());
        }

        @Test
        void persisteLaFinalizacionEnSolicitudYCurso() throws SQLException {
                prepararTablasDeResultados();
                String instancia = iniciarSolicitud();
                engine.getRuntimeService().setVariables(instancia, Map.of("solicitudId", 1L, "cursoId", 10L));
                moverDesdeRegistro(instancia, "sid-BA739FA3-D40A-4AFD-AD69-062B808ABF63");
                completarTarea(instancia, "sid-BA739FA3-D40A-4AFD-AD69-062B808ABF63", Map.of());

                assertEquals("finalizado", consultarResultado("SELECT resultado FROM solicitudes WHERE id = 1"));
                assertEquals("finalizado", consultarResultado("SELECT estado FROM cursos WHERE id = 10"));
                assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instancia).count());
        }

        @Test
        void persisteLaCancelacionEnSolicitudYCurso() throws SQLException {
                prepararTablasDeResultados();
                String instancia = iniciarPreparacion();
                engine.getRuntimeService().setVariables(instancia, Map.of("solicitudId", 1L, "cursoId", 10L));
                enviarCancelacion(instancia);

                assertCierreCancelado(instancia);
                assertEquals("cancelado", consultarResultado("SELECT resultado FROM solicitudes WHERE id = 1"));
                assertEquals("cancelado", consultarResultado("SELECT estado FROM cursos WHERE id = 10"));
        }

        @Test
        void persisteLaSolicitudNoViable() throws SQLException {
                prepararTablasDeResultados();
                for (int indice = 0; indice < 5; indice++) {
                        String activa = iniciarSolicitud();
                        engine.getRuntimeService().setVariable(activa, "cursoId", (long) indice + 20);
                }
                String instancia = engine.getRuntimeService().startProcessInstanceByMessage("msgSolicitud",
                                Map.of("solicitudId", 1L)).getId();

                assertEquals("no_viable", consultarResultado("SELECT resultado FROM solicitudes WHERE id = 1"));
                assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instancia).count());
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
    void elModeloRealDefineElInformeConUnFormularioYValidacion() {
        UserTask task = (UserTask) getProcessModel().getMainProcess()
                .getFlowElement("sid-8EE040F3-0293-488C-AE3F-3BD79DB56897");

        assertEquals(1, task.getFormProperties().size());
        assertEquals("informeResultados", task.getFormProperties().get(0).getId());
        assertEquals("string", task.getFormProperties().get(0).getType());
        assertTrue(task.getFormProperties().get(0).isRequired());
        assertEquals(2, task.getTaskListeners().size());
        assertEquals("complete", task.getTaskListeners().get(0).getEvent());
        assertEquals("flowable.report.ValidateReport", task.getTaskListeners().get(0).getImplementation());
        assertEquals("complete", task.getTaskListeners().get(1).getEvent());
        assertEquals("flowable.report.SaveReport", task.getTaskListeners().get(1).getImplementation());
    }

    @Test
    void elModeloRealValidaLaFechaDeFinAlDesarrollarElCurso() {
        UserTask task = (UserTask) getProcessModel().getMainProcess()
                .getFlowElement("sid-378C089F-415C-4EC6-B426-E2D989460B8C");

        assertTrue(task.getFormProperties().isEmpty());
        assertEquals(1, task.getTaskListeners().size());
        assertEquals("complete", task.getTaskListeners().get(0).getEvent());
        assertEquals("flowable.course.ValidateCourseEndDate",
                task.getTaskListeners().get(0).getImplementation());
    }

        @Test
        void revisarPropuestaTieneElMismoFormularioYValidacionQueRegistrar() {
                BpmnModel model = getProcessModel();
                UserTask registrar = (UserTask) model.getMainProcess()
                                .getFlowElement("sid-F96A6968-13D6-4EA4-929E-69230127C208");
                UserTask revisar = (UserTask) model.getMainProcess()
                                .getFlowElement("sid-BE500ABC-83C7-4A8A-9C83-23183950A2F6");

                List<String> ids = List.of("programa", "presupuesto", "fechaInicioPosible", "fechaFinPropuesta");
                List<String> tipos = List.of("string", "long", "date", "date");
                assertEquals(ids.size(), registrar.getFormProperties().size());
                assertEquals(ids.size(), revisar.getFormProperties().size());
                for (int index = 0; index < ids.size(); index++) {
                        assertEquals(ids.get(index), registrar.getFormProperties().get(index).getId());
                        assertEquals(ids.get(index), revisar.getFormProperties().get(index).getId());
                        assertEquals(tipos.get(index), registrar.getFormProperties().get(index).getType());
                        assertEquals(tipos.get(index), revisar.getFormProperties().get(index).getType());
                        assertEquals(registrar.getFormProperties().get(index).getName(),
                                        revisar.getFormProperties().get(index).getName());
                        assertTrue(registrar.getFormProperties().get(index).isRequired());
                        assertTrue(revisar.getFormProperties().get(index).isRequired());
                }
                assertEquals(1, revisar.getTaskListeners().size());
                assertEquals("complete", revisar.getTaskListeners().get(0).getEvent());
                assertEquals("flowable.proposal.ValidateProposalDates", revisar.getTaskListeners().get(0).getImplementation());
        }

    @Test
    void asignaAdministracionComoGrupoCandidatoDeLasTareasDePreparacion() {
        ProcessDefinition processDefinition = engine.getRepositoryService()
                .createProcessDefinitionQuery()
                .processDefinitionKey("process-proyecto-curso")
                .singleResult();
        BpmnModel model = engine.getRepositoryService().getBpmnModel(processDefinition.getId());
        SubProcess prepararCurso = (SubProcess) model.getMainProcess().getFlowElement(PREPARAR_CURSO);

        assertCandidateGroup(prepararCurso, "sid-67285B9F-672D-4038-A765-E67245B387D1");
        assertCandidateGroup(prepararCurso, "sid-809C7C67-70EC-4018-BBF7-6A27DD12D4BB");
        assertCandidateGroup(prepararCurso, EVALUAR_RECURSOS);
        assertCandidateGroup(prepararCurso, AULA_VIRTUAL);
        assertCandidateGroup(prepararCurso, EQUIPAMIENTO);

        ScriptTask liberarEquipamiento = (ScriptTask) prepararCurso
                .getFlowElement(LIBERAR_EQUIPAMIENTO);
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
    void asignaResultadoUnaSolaVezEnRechazoYEnFinNormal() {
        BpmnModel model = getProcessModel();

        List<FlowableListener> rechazo = model.getMainProcess()
                .getFlowElement("sid-2EDEE778-CCB8-4D86-B10B-F0CA6DE47860")
                .getExecutionListeners();
        assertEquals(1, rechazo.size());
        assertEquals("end", rechazo.get(0).getEvent());
        assertEquals("${execution.setVariable('resultado', 'rechazado')}",
                rechazo.get(0).getImplementation());
        assertTrue(model.getMainProcess()
                .getFlowElement("sid-748C8A20-2795-4083-8BFA-E3CE09B3AB95")
                .getExecutionListeners().isEmpty());

        List<FlowableListener> finNormal = model.getMainProcess()
                .getFlowElement("sid-343EF313-279B-45FF-9F2C-343A3C2804DB")
                .getExecutionListeners();
        assertEquals(1, finNormal.size());
        assertEquals("start", finNormal.get(0).getEvent());
        assertEquals("${execution.setVariable('resultado', 'finalizado')}",
                finNormal.get(0).getImplementation());
        assertTrue(model.getMainProcess()
                .getFlowElement("sid-74237920-0988-4552-A34F-AB4A19332A3F")
                .getExecutionListeners().isEmpty());
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
                .getFlowElement(EVALUAR_RECURSOS);

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

        assertServiceTaskClass(prepararCurso, RESERVAR_AULA,
            "flowable.classroom.ReserveClassroom");
        assertServiceTaskClass(prepararCurso, LIBERAR_AULA,
            "flowable.classroom.ReleaseClassroom");
        }

        @Test
        void conectaLosEventosDeCompensacionConSusTareas() {
        SubProcess prepararCurso = getPrepararCurso();
            BpmnModel model = getProcessModel();

            assertCompensationAssociation(prepararCurso, "sid-6EF58547-6206-4065-BC46-BDE7A86300E7",
                LIBERAR_AULA);
        assertCompensationAssociation(prepararCurso, "sid-79D40E03-33A5-476E-AC89-3F44F73680A0",
            LIBERAR_AULA_VIRTUAL);
        assertCompensationAssociation(prepararCurso, "sid-0FB02D1D-DFB1-4222-BA0E-6D58C0E87619",
            LIBERAR_EQUIPAMIENTO);

        assertCompensationTask(prepararCurso, LIBERAR_AULA);
        assertCompensationTask(prepararCurso, LIBERAR_AULA_VIRTUAL);
        assertCompensationTask(prepararCurso, LIBERAR_EQUIPAMIENTO);

        assertCompensationActivityRef(prepararCurso, "sid-31835240-F72D-4525-9FCE-6428CE669ED3",
                RESERVAR_AULA);
        assertCompensationActivityRef(prepararCurso, "sid-CE2ED1CB-BB53-499C-AB58-347BB0FA0C80",
                AULA_VIRTUAL);
        assertCompensationActivityRef(prepararCurso, "sid-3329D761-38E5-49B6-99FE-711F82A01284",
                EQUIPAMIENTO);
        assertCompensationActivityRef(model.getMainProcess(), "sid-88F847B3-5FE2-4251-A612-120F9DD46CA9",
                RESERVAR_AULA);
        assertCompensationActivityRef(model.getMainProcess(), "sid-CAF881B2-93D6-479B-9C01-806B1059E125",
                AULA_VIRTUAL);
        assertCompensationActivityRef(model.getMainProcess(), "sid-98B02508-C619-4111-AA31-8FC54D5F85B2",
                EQUIPAMIENTO);
        }

    @Test
    void configuraCapturaCompensacionYNotificacionParaSinAulas() {
        BpmnModel model = getProcessModel();
        SubProcess prepararCurso = getPrepararCurso();
        BoundaryEvent capturaEnReserva = (BoundaryEvent) prepararCurso
                .getFlowElement("sid-7A782D3B-FA48-4B06-9E9D-22C32BD6F574");
        EndEvent finSinAulas = (EndEvent) prepararCurso
                .getFlowElement("sid-481F402E-5806-475E-8AB2-1148DE4E7B9C");
        ScriptTask notificarCierre = (ScriptTask) model.getMainProcess()
                .getFlowElement("sid-5F2866C1-0E11-4CC6-BD34-2875F370E0D1");

        assertEquals(RESERVAR_AULA, capturaEnReserva.getAttachedToRefId());
        assertEquals("errorSIN_AULAS", errorDefinition(capturaEnReserva).getErrorCode());
        assertCompensationActivityRef(prepararCurso, "sid-1B42D920-0C4F-482C-9C3B-99E980DF50F1",
                RESERVAR_AULA);
        assertCompensationActivityRef(prepararCurso, "sid-AD35AD31-1F32-478B-BEBE-CA591851B965",
                AULA_VIRTUAL);
        assertCompensationActivityRef(prepararCurso, "sid-3215FB81-B8CC-4C88-B29E-7E3B40FB7A4E",
                EQUIPAMIENTO);
        assertTrue(finSinAulas.getEventDefinitions().stream().anyMatch(TerminateEventDefinition.class::isInstance));
        assertEquals(2, finSinAulas.getExecutionListeners().size());
        assertTrue(finSinAulas.getExecutionListeners().stream().anyMatch(listener ->
                "start".equals(listener.getEvent())
                        && "${execution.setVariable('cancelada', true)}".equals(listener.getImplementation())));
        assertTrue(finSinAulas.getExecutionListeners().stream().anyMatch(listener ->
                "start".equals(listener.getEvent())
                        && "${execution.setVariable('motivoCierre', 'SIN_AULAS')}".equals(listener.getImplementation())));
        assertNull(model.getMainProcess().getFlowElement("sid-6931943F-5BD2-45CD-BE56-52DE64896B0C"));
        assertNull(model.getMainProcess().getFlowElement("sid-E13FA448-268A-485E-B2D7-0A4F47B0CA58"));
        SequenceFlow haciaGateway = (SequenceFlow) model.getMainProcess()
                .getFlowElement("sid-7F9A54E9-3068-4959-A418-744D51117FC7");
        SequenceFlow haciaCierre = (SequenceFlow) model.getMainProcess()
                .getFlowElement("sid-E0E2CBD8-5B0B-406D-9E0B-F1AA7D68526C");
        assertEquals(prepararCurso.getId(), haciaGateway.getSourceRef());
        assertEquals(haciaGateway.getTargetRef(), haciaCierre.getSourceRef());
        assertEquals(notificarCierre.getId(), haciaCierre.getTargetRef());
        assertEquals("${cancelada}", haciaCierre.getConditionExpression());
        assertFlow(prepararCurso, "sid-C1AD5A67-E90B-46B4-80B1-E58A31C5774B",
                capturaEnReserva.getId(), "sid-1B42D920-0C4F-482C-9C3B-99E980DF50F1");
        assertFlow(prepararCurso, "sid-FEB357E8-1D07-4C42-92CB-572D8F4F5ED0",
                "sid-1B42D920-0C4F-482C-9C3B-99E980DF50F1", "sid-AD35AD31-1F32-478B-BEBE-CA591851B965");
        assertFlow(prepararCurso, "sid-61EABF9D-A1D6-4500-85D4-2F24C546B443",
                "sid-AD35AD31-1F32-478B-BEBE-CA591851B965", "sid-3215FB81-B8CC-4C88-B29E-7E3B40FB7A4E");
        assertFlow(prepararCurso, "sid-0CA04C12-36C6-46F0-BA41-799C1CE9B7E2",
                "sid-3215FB81-B8CC-4C88-B29E-7E3B40FB7A4E", finSinAulas.getId());
        assertEquals("if (execution.hasVariable(\"motivoCierre\")) { println \"Curso cancelado por falta de aulas disponibles: \" + execution.processInstanceId } else { println \"Curso cancelado: \" + execution.processInstanceId }; execution.setVariable(\"resultado\", \"cancelado\")",
                notificarCierre.getScript());
        assertTrue(model.getGraphicInfo(capturaEnReserva.getId()) != null);
        assertNull(model.getGraphicInfo("sid-6931943F-5BD2-45CD-BE56-52DE64896B0C"));
    }

    @Test
    void ejecutaSinAulasEnElBpmnRealYFinalizaNotificandoElCierre() {
        String primera = iniciarSolicitud();
        String segunda = iniciarSolicitud();
        String tercera = iniciarPreparacion();
        engine.getRuntimeService().setVariable(primera, "aulaAsignada", "Aula 1");
        engine.getRuntimeService().setVariable(segunda, "aulaAsignada", "Aula 2");

        Task evaluarRecursos = engine.getTaskService().createTaskQuery()
                .processInstanceId(tercera)
                .taskDefinitionKey(EVALUAR_RECURSOS)
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
        assertEquals("SIN_AULAS", engine.getHistoryService().createHistoricVariableInstanceQuery()
                .processInstanceId(tercera)
                .variableName("motivoCierre")
                .singleResult().getValue());
        assertEquals(true, engine.getHistoryService().createHistoricVariableInstanceQuery()
                .processInstanceId(tercera)
                .variableName("cancelada")
                .singleResult().getValue());
        assertHistoricActivity(tercera, "sid-7A782D3B-FA48-4B06-9E9D-22C32BD6F574");
        assertHistoricActivity(tercera, "sid-1B42D920-0C4F-482C-9C3B-99E980DF50F1");
        assertHistoricActivity(tercera, "sid-AD35AD31-1F32-478B-BEBE-CA591851B965");
        assertHistoricActivity(tercera, "sid-3215FB81-B8CC-4C88-B29E-7E3B40FB7A4E");
        assertHistoricActivity(tercera, "sid-481F402E-5806-475E-8AB2-1148DE4E7B9C");
        assertHistoricActivity(tercera, "sid-E72D5A77-AC5C-460D-B6D3-69530D2A39AD");
        assertHistoricActivity(tercera, "sid-5F2866C1-0E11-4CC6-BD34-2875F370E0D1");
        assertEquals(0, engine.getTaskService().createTaskQuery().processInstanceId(tercera).count());
        assertEquals(0, engine.getHistoryService().createHistoricActivityInstanceQuery()
                .processInstanceId(tercera)
                .activityId("sid-A3B3F53F-1D5E-42AA-AAC0-C69120F53E85")
                .count());
        assertEquals(0, engine.getHistoryService().createHistoricActivityInstanceQuery()
                .processInstanceId(tercera)
                .activityId(LIBERAR_AULA)
                .count());
    }

    @Test
    void cancelaSinRecursosNoEjecutaCompensaciones() {
        String instancia = iniciarPreparacion();

        enviarCancelacion(instancia);

        assertCierreCancelado(instancia);
        assertNoHistoricActivity(instancia, LIBERAR_AULA);
        assertNoHistoricActivity(instancia, LIBERAR_AULA_VIRTUAL);
        assertNoHistoricActivity(instancia, LIBERAR_EQUIPAMIENTO);
    }

    @Test
    void cancelaPreparacionYCompensaLosRecursosReservados() {
        String instancia = iniciarPreparacion();
        completarTarea(instancia, EVALUAR_RECURSOS, Map.of(
                "aulaFisica", true, "aulaVirtual", true, "equipamiento", true));
        assertEquals("Aula 1", engine.getRuntimeService().getVariable(instancia, "aulaAsignada"));
        completarTarea(instancia, AULA_VIRTUAL, Map.of());
        completarTarea(instancia, EQUIPAMIENTO, Map.of());

        enviarCancelacion(instancia);

        assertCierreCancelado(instancia);
        assertHistoricActivity(instancia, LIBERAR_AULA);
        assertHistoricActivity(instancia, LIBERAR_AULA_VIRTUAL);
        assertHistoricActivity(instancia, LIBERAR_EQUIPAMIENTO);
        String siguiente = iniciarPreparacion();
        completarTarea(siguiente, EVALUAR_RECURSOS, Map.of(
                "aulaFisica", true, "aulaVirtual", false, "equipamiento", false));
        assertEquals("Aula 1", engine.getRuntimeService().getVariable(siguiente, "aulaAsignada"));
    }

    @Test
    void cancelaConUnaSolaReservaYLiberaSoloEsa() {
        String instancia = iniciarPreparacion();
        completarTarea(instancia, EVALUAR_RECURSOS, Map.of(
                "aulaFisica", true,
                "aulaVirtual", false,
                "equipamiento", false));
        assertEquals("Aula 1", engine.getRuntimeService().getVariable(instancia, "aulaAsignada"));

        enviarCancelacion(instancia);

        assertCierreCancelado(instancia);
        assertHistoricActivity(instancia, LIBERAR_AULA);
        assertNoHistoricActivity(instancia, LIBERAR_AULA_VIRTUAL);
        assertNoHistoricActivity(instancia, LIBERAR_EQUIPAMIENTO);
    }

    @ParameterizedTest(name = "física={0}, virtual={1}, equipamiento={2}")
    @CsvSource({
            "false, true, false",
            "false, false, true",
            "true, true, false",
            "true, false, true",
            "false, true, true"
    })
    void cancelaCompensaLosRecursosReservadosEnCombinacionesParciales(
            boolean aulaFisica, boolean aulaVirtual, boolean equipamiento) {
        String instancia = iniciarPreparacion();
        completarTarea(instancia, EVALUAR_RECURSOS, Map.of(
                "aulaFisica", aulaFisica,
                "aulaVirtual", aulaVirtual,
                "equipamiento", equipamiento));
        if (aulaFisica) {
            assertEquals("Aula 1", engine.getRuntimeService().getVariable(instancia, "aulaAsignada"));
        }
        if (aulaVirtual) {
            completarTarea(instancia, AULA_VIRTUAL, Map.of());
        }
        if (equipamiento) {
            completarTarea(instancia, EQUIPAMIENTO, Map.of());
        }

        enviarCancelacion(instancia);

        assertCierreCancelado(instancia);
        assertHistoricActivityCount(instancia, LIBERAR_AULA, aulaFisica ? 1 : 0);
        assertHistoricActivityCount(instancia, LIBERAR_AULA_VIRTUAL, aulaVirtual ? 1 : 0);
        assertHistoricActivityCount(instancia, LIBERAR_EQUIPAMIENTO, equipamiento ? 1 : 0);
    }

    @Test
    void preparacionNormalContinuaHastaDefinirDocente() {
        String instancia = iniciarPreparacion();
        completarTarea(instancia, EVALUAR_RECURSOS, Map.of(
                "aulaFisica", true, "aulaVirtual", false, "equipamiento", false));
        completarTarea(instancia, "sid-809C7C67-70EC-4018-BBF7-6A27DD12D4BB", Map.of());
        String nomina = engine.getRuntimeService().createExecutionQuery().processInstanceId(instancia)
                .activityId("sid-40B80C57-5A63-4D5C-A43B-710C8B140370").singleResult().getId();
        engine.getRuntimeService().trigger(nomina);
        completarTarea(instancia, "sid-67285B9F-672D-4038-A765-E67245B387D1", Map.of());

        // "Preparación completa" es asíncrona: cada rama que llega deja un job para la unión.
        assertEquals(0, engine.getTaskService().createTaskQuery().processInstanceId(instancia)
                .taskDefinitionKey("sid-A3B3F53F-1D5E-42AA-AAC0-C69120F53E85").count());
        List<Job> jobs = engine.getManagementService().createJobQuery().processInstanceId(instancia).list();
        assertEquals(3, jobs.size());
        for (Job job : jobs) {
            engine.getManagementService().executeJob(job.getId());
        }

        assertEquals(false, engine.getRuntimeService().getVariable(instancia, "cancelada"));
        assertEquals(1, engine.getTaskService().createTaskQuery().processInstanceId(instancia)
                .taskDefinitionKey("sid-A3B3F53F-1D5E-42AA-AAC0-C69120F53E85").count());
        assertEquals(0, engine.getHistoryService().createHistoricActivityInstanceQuery()
                .processInstanceId(instancia).activityId("sid-5F2866C1-0E11-4CC6-BD34-2875F370E0D1").count());
    }

    private String iniciarPreparacion() {
        String instancia = iniciarSolicitud();
                moverDesdeRegistro(instancia, PREPARAR_CURSO);
                return instancia;
        }

        private void moverDesdeRegistro(String instancia, String destino) {
                Task tarea = engine.getTaskService().createTaskQuery().processInstanceId(instancia).singleResult();
        engine.getRuntimeService().createChangeActivityStateBuilder().processInstanceId(instancia)
                                .moveActivityIdTo(tarea.getTaskDefinitionKey(), destino)
                .changeState();
        }

        private String iniciarPropuesta() {
                String instancia = iniciarSolicitud();
                engine.getRuntimeService().setVariables(instancia, Map.of("solicitudId", 1L, "plazoRecordatorio", "PT1M"));
                completarTarea(instancia, "sid-F96A6968-13D6-4EA4-929E-69230127C208", Map.of(
                                "programa", "Programa", "presupuesto", 100L,
                                "fechaInicioPosible", Date.from(Instant.parse("2026-11-20T00:00:00Z")),
                                "fechaFinPropuesta", Date.from(Instant.parse("2026-11-21T00:00:00Z"))));
                return instancia;
        }

        private void assertResultadoAntesDeArchivar(String instancia, String resultado) throws SQLException {
                assertEquals(1, engine.getTaskService().createTaskQuery().processInstanceId(instancia)
                                .taskDefinitionKey("sid-DA4178CD-28E4-4E74-A085-B6D7BE829D62").count());
                assertEquals(resultado, engine.getRuntimeService().getVariable(instancia, "resultado"));
                assertNull(consultarResultado("SELECT resultado FROM solicitudes WHERE id = 1"));
        }

        private void prepararTablasDeResultados() throws SQLException {
                try (Connection conexion = engine.getProcessEngineConfiguration().getDataSource().getConnection();
                                Statement sentencia = conexion.createStatement()) {
                        sentencia.execute("CREATE TABLE solicitudes (id BIGINT PRIMARY KEY, resultado VARCHAR(40))");
                        sentencia.execute("CREATE TABLE cursos (id BIGINT PRIMARY KEY, estado VARCHAR(40))");
                        sentencia.execute("INSERT INTO solicitudes VALUES (1, NULL)");
                        sentencia.execute("INSERT INTO cursos VALUES (10, 'en_preparacion')");
                }
        }

        private String consultarResultado(String consulta) throws SQLException {
                try (Connection conexion = engine.getProcessEngineConfiguration().getDataSource().getConnection();
                                Statement sentencia = conexion.createStatement(); ResultSet resultado = sentencia.executeQuery(consulta)) {
                        resultado.next();
                        return resultado.getString(1);
                }
    }

    private void completarTarea(String instancia, String tareaId, Map<String, Object> variables) {
        Task tarea = engine.getTaskService().createTaskQuery().processInstanceId(instancia)
                .taskDefinitionKey(tareaId).singleResult();
        engine.getTaskService().complete(tarea.getId(), variables);
    }

    private void enviarCancelacion(String instancia) {
        String ejecucion = engine.getRuntimeService().createExecutionQuery().processInstanceId(instancia)
                .messageEventSubscriptionName("msgCancelacion").singleResult().getId();
        engine.getRuntimeService().messageEventReceived("msgCancelacion", ejecucion);
    }

    private void assertCierreCancelado(String instancia) {
        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instancia).count());
        assertEquals(0, engine.getTaskService().createTaskQuery().processInstanceId(instancia).count());
        assertEquals(true, engine.getHistoryService().createHistoricVariableInstanceQuery()
                .processInstanceId(instancia).variableName("cancelada").singleResult().getValue());
        assertEquals("cancelado", engine.getHistoryService().createHistoricVariableInstanceQuery()
                .processInstanceId(instancia).variableName("resultado").singleResult().getValue());
        assertHistoricActivity(instancia, "sid-E72D5A77-AC5C-460D-B6D3-69530D2A39AD");
        assertHistoricActivity(instancia, "sid-5F2866C1-0E11-4CC6-BD34-2875F370E0D1");
        assertEquals(0, engine.getHistoryService().createHistoricActivityInstanceQuery()
                .processInstanceId(instancia).activityId("sid-A3B3F53F-1D5E-42AA-AAC0-C69120F53E85").count());
    }

    private String iniciarSolicitud() {
        return engine.getRuntimeService().startProcessInstanceByMessage("msgSolicitud", Map.of())
                .getId();
    }

    private void assertHistoricActivity(String processInstanceId, String activityId) {
        assertHistoricActivityCount(processInstanceId, activityId, 1);
    }

    private void assertNoHistoricActivity(String processInstanceId, String activityId) {
        assertHistoricActivityCount(processInstanceId, activityId, 0);
    }

    private void assertHistoricActivityCount(String processInstanceId, String activityId, long expected) {
        assertEquals(expected, engine.getHistoryService().createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityId(activityId)
                .count(), "Conteo histórico inesperado para la actividad BPMN " + activityId
                + " (esperado: " + expected + ")");
    }

    private static ErrorEventDefinition errorDefinition(BoundaryEvent event) {
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
                .getFlowElement(PREPARAR_CURSO);
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