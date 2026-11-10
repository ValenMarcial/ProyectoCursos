package flowable.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.engine.impl.rules.RulesDeployer;
import org.flowable.engine.runtime.Execution;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Gateway "Recursos necesarios" sobre el BPMN real: completa "Evaluar Recursos"
 * y verifica qué ramas se activan según las variables que manda la pantalla.
 */
class ResourcesGatewayModelTest {
    private static final String EVALUAR_RECURSOS = "sid-F3381B5B-C506-4AC2-A09D-747447EFB31B";
    private static final String RESERVAR_AULA = "sid-065649F8-9F01-4C34-B290-C37474CC479D";
    private static final String AULA_VIRTUAL = "sid-C3399A6A-261E-4D23-9AF8-F9F58D4A81CB";
    private static final String EQUIPAMIENTO = "sid-6AAC5E2F-BF84-4C7C-8963-7D1F6CBEF790";

    private ProcessEngine engine;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private HistoryService historyService;

    @BeforeEach
    void setUp() {
        StandaloneInMemProcessEngineConfiguration config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:gateway-recursos-" + System.nanoTime() + ";DB_CLOSE_DELAY=1000");
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
    void conSoloAulaVirtualSeActivaUnicamenteEsaRama() {
        String instancia = llegarAEvaluarRecursos();

        taskService.complete(evaluarRecursos(instancia).getId(),
                Map.of("aulaFisica", false, "aulaVirtual", true, "equipamiento", false));

        assertEquals(Set.of(AULA_VIRTUAL), accionesDeRecursos(instancia));
    }

    @Test
    void conSoloEquipamientoSeActivaUnicamenteEsaRama() {
        String instancia = llegarAEvaluarRecursos();

        taskService.complete(evaluarRecursos(instancia).getId(),
                Map.of("aulaFisica", false, "aulaVirtual", false, "equipamiento", true));

        assertEquals(Set.of(EQUIPAMIENTO), accionesDeRecursos(instancia));
    }

    @Test
    void conAulaFisicaYVirtualSeActivanLasDos() {
        String instancia = llegarAEvaluarRecursos();

        taskService.complete(evaluarRecursos(instancia).getId(),
                Map.of("aulaFisica", true, "aulaVirtual", true, "equipamiento", false));

        assertEquals(Set.of(RESERVAR_AULA, AULA_VIRTUAL), accionesDeRecursos(instancia));
    }

    @Test
    void lasVariablesQueNoVienenCuentanComoFalse() {
        String instancia = llegarAEvaluarRecursos();

        taskService.complete(evaluarRecursos(instancia).getId(), Map.of("aulaVirtual", true));

        assertEquals(Set.of(AULA_VIRTUAL), accionesDeRecursos(instancia));
    }

    @Test
    void sinNingunRecursoRechazaYLaTareaSigueActiva() {
        String instancia = llegarAEvaluarRecursos();
        String tarea = evaluarRecursos(instancia).getId();

        assertThrows(FlowableIllegalArgumentException.class, () -> taskService.complete(tarea,
                Map.of("aulaFisica", false, "aulaVirtual", false, "equipamiento", false)));
        assertEquals(tarea, evaluarRecursos(instancia).getId());
    }

    /** Inicia una solicitud y la mueve a "Evaluar Recursos", salteando la negociación. */
    private String llegarAEvaluarRecursos() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("solicitudId", 1L);
        variables.put("empresaNombre", "Tech Solutions S.A.");
        variables.put("tematica", "BPMN");
        variables.put("participantesEstimados", 10L);
        String instancia = runtimeService.startProcessInstanceByMessage("msgSolicitud", variables).getId();

        List<String> actuales = runtimeService.createExecutionQuery().processInstanceId(instancia).list().stream()
                .map(Execution::getActivityId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(instancia)
                .moveActivityIdsToSingleActivityId(actuales, EVALUAR_RECURSOS)
                .changeState();
        return instancia;
    }

    private Task evaluarRecursos(String instancia) {
        return taskService.createTaskQuery().processInstanceId(instancia)
                .taskDefinitionKey(EVALUAR_RECURSOS).singleResult();
    }

    /** Acciones de recursos que se ejecutaron o están activas en la instancia. */
    private Set<String> accionesDeRecursos(String instancia) {
        Set<String> acciones = Set.of(RESERVAR_AULA, AULA_VIRTUAL, EQUIPAMIENTO);
        return historyService.createHistoricActivityInstanceQuery().processInstanceId(instancia).list().stream()
                .map(actividad -> actividad.getActivityId())
                .filter(acciones::contains)
                .collect(Collectors.toSet());
    }
}
