package flowable.viability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import java.util.ArrayList;
import java.util.List;

import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.engine.impl.rules.RulesDeployer;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ViabilityTest {
    private static final String PROCESS_KEY = "test-viabilidad";

    private ProcessEngine engine;
    private RuntimeService runtimeService;

    @BeforeEach
    void setUp() {
        // Motor en memoria (H2), una base nueva por test
        StandaloneInMemProcessEngineConfiguration config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:viabilidad-" + System.nanoTime() + ";DB_CLOSE_DELAY=1000");
        // Flowable no despliega archivos .drl salvo que se registre el RulesDeployer
        config.setCustomPostDeployers(new ArrayList<>(List.of(new RulesDeployer())));
        engine = config.buildProcessEngine();
        runtimeService = engine.getRuntimeService();

        // El .drl tiene que ir en el mismo deployment que el BPMN
        engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/viabilidad-test.bpmn20.xml")
                .addClasspathResource("rules/viability.drl")
                .deploy();
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void primeraSolicitudEsViable() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(PROCESS_KEY);

        assertFalse(instance.isEnded());
        assertEquals(Boolean.TRUE, runtimeService.getVariable(instance.getId(), "isViable"));
        assertWaitingAtRegistrarPropuesta(instance);
    }

    @Test
    void conCuatroCursosActivosSigueSiendoViable() {
        startCoursesWithAccess(4);

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(PROCESS_KEY);

        assertFalse(instance.isEnded());
        assertWaitingAtRegistrarPropuesta(instance);
    }

    @Test
    void conCincoCursosActivosNoEsViable() {
        startCoursesWithAccess(5);

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(PROCESS_KEY);

        assertTrue(instance.isEnded(), "Con 5 cursos activos la solicitud debe ir por el camino No");
        assertEquals(Boolean.FALSE, engine.getHistoryService().createHistoricVariableInstanceQuery()
                .processInstanceId(instance.getId())
                .variableName("isViable")
                .singleResult()
                .getValue());
    }

    @Test
    void lasSolicitudesEnEvaluacionNoCuentan() {
        // Cinco solicitudes que todavía no llegaron a "Habilitar acceso de la empresa"
        for (int i = 0; i < 5; i++) {
            runtimeService.startProcessInstanceByKey(PROCESS_KEY);
        }

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(PROCESS_KEY);

        assertFalse(instance.isEnded(), "Las solicitudes sin curso creado no son cursos en ejecución");
    }

    @Test
    void lasInstanciasFinalizadasNoCuentan() {
        startCoursesWithAccess(5);
        // Se termina una: quedan 4 activas
        String id = runtimeService.createProcessInstanceQuery().processDefinitionKey(PROCESS_KEY)
                .list().get(0).getId();
        runtimeService.deleteProcessInstance(id, "test");

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(PROCESS_KEY);

        assertFalse(instance.isEnded());
    }

    // Simula cursos que ya pasaron por "Habilitar acceso de la empresa": CreateCourseOnAcceptance guarda cursoId
    private void startCoursesWithAccess(int amount) {
        for (int i = 0; i < amount; i++) {
            ProcessInstance instance = runtimeService.startProcessInstanceByKey(PROCESS_KEY);
            assertFalse(instance.isEnded(), "La instancia previa " + (i + 1) + " debería quedar activa");
            runtimeService.setVariable(instance.getId(), "cursoId", (long) (i + 1));
        }
    }

    private void assertWaitingAtRegistrarPropuesta(ProcessInstance instance) {
        assertEquals("registrarPropuesta", engine.getTaskService().createTaskQuery()
                .processInstanceId(instance.getId())
                .singleResult()
                .getTaskDefinitionKey());
    }
}
