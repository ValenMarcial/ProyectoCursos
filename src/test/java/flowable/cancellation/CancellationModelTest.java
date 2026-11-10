package flowable.cancellation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.engine.impl.rules.RulesDeployer;
import org.flowable.engine.runtime.Execution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cancelación durante "Preparar curso" sobre el BPMN real: libera por compensación los recursos ya
 * conseguidos, registra resultado = cancelado y sale por "Notificar Cierre".
 */
class CancellationModelTest {
    private static final String PREPARAR_CURSO = "sid-9DE766AD-6C6B-4DA4-A60E-24BECC4FC4DF";
    private static final String EVALUAR_RECURSOS = "sid-F3381B5B-C506-4AC2-A09D-747447EFB31B";
    private static final String PREPARAR_MATERIALES = "sid-809C7C67-70EC-4018-BBF7-6A27DD12D4BB";
    private static final String RECIBIR_NOMINA = "sid-40B80C57-5A63-4D5C-A43B-710C8B140370";
    private static final String LISTA_DEFINITIVA = "sid-67285B9F-672D-4038-A765-E67245B387D1";
    private static final String AULA_VIRTUAL = "sid-C3399A6A-261E-4D23-9AF8-F9F58D4A81CB";
    private static final String EQUIPAMIENTO = "sid-6AAC5E2F-BF84-4C7C-8963-7D1F6CBEF790";
    private static final String LIBERAR_AULA = "sid-CF527638-CD0A-48BF-9EF6-DDB557838793";
    private static final String LIBERAR_AULA_VIRTUAL = "sid-892710F9-B2AE-4002-82D0-131829D8FA29";
    private static final String LIBERAR_EQUIPAMIENTO = "sid-3DEB48C5-A36A-4EBF-A77C-150D02FC969E";
    private static final String NOTIFICAR_CIERRE = "sid-5F2866C1-0E11-4CC6-BD34-2875F370E0D1";
    private static final String ENVIAR_CONFIRMACION = "sid-4D6E40C0-BF7F-46C4-AF01-7D3B3F96589A";
    private static final String DEFINIR_DOCENTE = "sid-A3B3F53F-1D5E-42AA-AAC0-C69120F53E85";
    private static final Set<String> LIBERACIONES = Set.of(LIBERAR_AULA, LIBERAR_AULA_VIRTUAL, LIBERAR_EQUIPAMIENTO);

    private ProcessEngine engine;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private HistoryService historyService;

    @BeforeEach
    void setUp() {
        StandaloneInMemProcessEngineConfiguration config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:cancelacion-" + System.nanoTime() + ";DB_CLOSE_DELAY=1000");
        config.setCustomPostDeployers(new ArrayList<>(List.of(new RulesDeployer())));
        engine = config.buildProcessEngine();
        engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/ProyectoCursos.bpmn20.xml")
                .addClasspathResource("rules/viability.drl")
                .deploy();
        runtimeService = engine.getRuntimeService();
        taskService = engine.getTaskService();
        historyService = engine.getHistoryService();
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void liberaElAulaVirtualYElEquipamientoYTerminaComoCancelado() {
        String instancia = llegarAPrepararCurso();
        completar(instancia, EVALUAR_RECURSOS, Map.of("aulaFisica", false, "aulaVirtual", true, "equipamiento", true));
        completar(instancia, AULA_VIRTUAL, Map.of());
        completar(instancia, EQUIPAMIENTO, Map.of());

        cancelar(instancia);

        assertEquals(Set.of(LIBERAR_AULA_VIRTUAL, LIBERAR_EQUIPAMIENTO), liberacionesTerminadas(instancia));
        assertCanceladaSinTareasPendientes(instancia);
    }

    @Test
    void liberaElAulaReservada() {
        String instancia = llegarAPrepararCurso();
        completar(instancia, EVALUAR_RECURSOS, Map.of("aulaFisica", true, "aulaVirtual", false, "equipamiento", false));
        assertEquals("Aula 1", runtimeService.getVariable(instancia, "aulaAsignada"));

        cancelar(instancia);

        assertEquals(Set.of(LIBERAR_AULA), liberacionesTerminadas(instancia));
        assertFalse(historyService.createHistoricVariableInstanceQuery().processInstanceId(instancia)
                .variableName("aulaAsignada").list().stream().anyMatch(v -> v.getValue() != null));
        assertCanceladaSinTareasPendientes(instancia);
    }

    @Test
    void soloLiberaLoQueYaSeConsiguio() {
        String instancia = llegarAPrepararCurso();
        completar(instancia, EVALUAR_RECURSOS, Map.of("aulaFisica", false, "aulaVirtual", true, "equipamiento", true));
        completar(instancia, EQUIPAMIENTO, Map.of());

        cancelar(instancia);

        // El aula virtual quedó pendiente: no hay nada que liberar.
        assertEquals(Set.of(LIBERAR_EQUIPAMIENTO), liberacionesTerminadas(instancia));
        assertCanceladaSinTareasPendientes(instancia);
    }

    @Test
    void sinRecursosConseguidosNoLiberaNadaYTerminaComoCancelado() {
        String instancia = llegarAPrepararCurso();

        cancelar(instancia);

        assertTrue(liberacionesTerminadas(instancia).isEmpty());
        assertCanceladaSinTareasPendientes(instancia);
    }

    @Test
    void sinAulasLibresTambienTerminaEnNotificarCierre() {
        for (int i = 0; i < 2; i++) {
            completar(llegarAPrepararCurso(), EVALUAR_RECURSOS,
                    Map.of("aulaFisica", true, "aulaVirtual", false, "equipamiento", false));
        }
        String tercera = llegarAPrepararCurso();

        completar(tercera, EVALUAR_RECURSOS, Map.of("aulaFisica", true, "aulaVirtual", false, "equipamiento", false));

        assertCanceladaSinTareasPendientes(tercera);
    }

    @Test
    void sinCancelacionSigueConEnviarConfirmacion() {
        String instancia = llegarAPrepararCurso();
        completar(instancia, PREPARAR_MATERIALES, Map.of());
        Execution nomina = runtimeService.createExecutionQuery().processInstanceId(instancia)
                .activityId(RECIBIR_NOMINA).singleResult();
        runtimeService.trigger(nomina.getId(), Map.of("participantes", "Ana Pérez, Juan Gómez"));
        completar(instancia, LISTA_DEFINITIVA, Map.of());
        completar(instancia, EVALUAR_RECURSOS, Map.of("aulaFisica", false, "aulaVirtual", true, "equipamiento", false));
        completar(instancia, AULA_VIRTUAL, Map.of());

        Set<String> ejecutadas = actividades(instancia);
        assertTrue(ejecutadas.contains(ENVIAR_CONFIRMACION));
        assertFalse(ejecutadas.contains(NOTIFICAR_CIERRE));
        assertTrue(liberacionesTerminadas(instancia).isEmpty());
        assertEquals(DEFINIR_DOCENTE, taskService.createTaskQuery().processInstanceId(instancia).singleResult()
                .getTaskDefinitionKey());
    }

    /** Inicia una solicitud y la mueve a "Preparar curso", salteando la negociación. */
    private String llegarAPrepararCurso() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("solicitudId", 1L);
        variables.put("empresaNombre", "Tech Solutions S.A.");
        variables.put("tematica", "BPMN");
        String instancia = runtimeService.startProcessInstanceByMessage("msgSolicitud", variables).getId();

        List<String> actuales = runtimeService.createExecutionQuery().processInstanceId(instancia).list().stream()
                .map(Execution::getActivityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(instancia)
                .moveActivityIdsToSingleActivityId(actuales, PREPARAR_CURSO)
                .changeState();
        return instancia;
    }

    private void completar(String instancia, String tarea, Map<String, Object> variables) {
        taskService.complete(taskService.createTaskQuery().processInstanceId(instancia)
                .taskDefinitionKey(tarea).singleResult().getId(), variables);
    }

    private void cancelar(String instancia) {
        Execution suscripta = runtimeService.createExecutionQuery().processInstanceId(instancia)
                .messageEventSubscriptionName("msgCancelacion").singleResult();
        runtimeService.messageEventReceived("msgCancelacion", suscripta.getId());
    }

    private Set<String> actividades(String instancia) {
        return historyService.createHistoricActivityInstanceQuery().processInstanceId(instancia).list().stream()
                .map(HistoricActivityInstance::getActivityId)
                .collect(Collectors.toSet());
    }

    /** Liberaciones que corrieron hasta el final (no las que se cortaron por cerrar el subproceso). */
    private Set<String> liberacionesTerminadas(String instancia) {
        return historyService.createHistoricActivityInstanceQuery().processInstanceId(instancia).finished().list()
                .stream()
                .filter(a -> LIBERACIONES.contains(a.getActivityId()) && a.getDeleteReason() == null)
                .map(HistoricActivityInstance::getActivityId)
                .collect(Collectors.toSet());
    }

    private void assertCanceladaSinTareasPendientes(String instancia) {
        assertEquals(0, runtimeService.createProcessInstanceQuery().processInstanceId(instancia).count(),
                "La instancia debería haber terminado");
        assertEquals(0, taskService.createTaskQuery().processInstanceId(instancia).count());
        Set<String> ejecutadas = actividades(instancia);
        assertTrue(ejecutadas.contains(NOTIFICAR_CIERRE));
        assertFalse(ejecutadas.contains(ENVIAR_CONFIRMACION));
        assertEquals("cancelado", historyService.createHistoricVariableInstanceQuery().processInstanceId(instancia)
                .variableName("resultado").singleResult().getValue());
    }
}
