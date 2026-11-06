package flowable.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelerExportFixerTest {
    private ProcessEngine engine;

    @BeforeEach
    void setUp() {
        StandaloneInMemProcessEngineConfiguration config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:fixer-" + System.nanoTime() + ";DB_CLOSE_DELAY=1000");
        engine = config.buildProcessEngine();
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void convierteLasFlechasDeCompensacionDelModeloReal() throws Exception {
        // Como lo guarda el editor: las tres compensaciones de "Preparar curso" como sequenceFlow
        String fromEditor = asSequenceFlows(read("processes/ProyectoCursos.bpmn20.xml"));

        String fixed = ModelerExportFixer.fix(fromEditor);

        assertEquals(3, count(fixed, "<association "));
        assertEquals(count(fromEditor, "<sequenceFlow ") - 3, count(fixed, "<sequenceFlow "));
        // Las associations quedan al final de "Preparar curso", después del subproceso de Cancelación
        int cancelacionEnd = fixed.indexOf("</subProcess>", fixed.indexOf("name=\"Cancelación\""));
        int prepararEnd = fixed.indexOf("</subProcess>", cancelacionEnd + 1);
        int firstAssociation = fixed.indexOf("<association ");
        assertTrue(cancelacionEnd < firstAssociation && firstAssociation < prepararEnd);
        // El motor valida el esquema al desplegar, así que el orden dentro del subproceso es válido
        engine.getRepositoryService().createDeployment()
                .addString("ProyectoCursos.bpmn20.xml", fixed)
                .deploy();
    }

    @Test
    void restauraElNombreDelProceso() throws Exception {
        String fromEditor = read("processes/ProyectoCursos.bpmn20.xml")
                .replace("name=\"" + ModelerExportFixer.PROCESS_NAME + "\" isExecutable", "name=\"Institución de capacitación\" isExecutable");

        String fixed = ModelerExportFixer.fix(fromEditor);

        assertTrue(fixed.contains("<process id=\"process-proyecto-curso\" name=\"Proyecto Cursos\" isExecutable=\"true\">"));
        // La pool conserva su nombre
        assertTrue(fixed.contains("<participant id=\"sid-C6269A25-41A2-4867-A023-7D2DF142C698\" name=\"Institución de capacitación\""));
    }

    @Test
    void esIdempotente() throws Exception {
        String fixed = ModelerExportFixer.fix(asSequenceFlows(read("processes/ProyectoCursos.bpmn20.xml")));

        assertEquals(fixed, ModelerExportFixer.fix(fixed));
    }

    @Test
    void laCompensacionCorregidaSeEjecuta() throws Exception {
        String fixed = ModelerExportFixer.fix(asSequenceFlows(read("processes/reserva-aula-test.bpmn20.xml")));
        engine.getRepositoryService().createDeployment().addString("reserva-aula.bpmn20.xml", fixed).deploy();
        RuntimeService runtimeService = engine.getRuntimeService();
        TaskService taskService = engine.getTaskService();

        String instanceId = runtimeService.startProcessInstanceByKey("test-reserva-aula").getId();
        taskService.complete(taskService.createTaskQuery().processInstanceId(instanceId).singleResult().getId());

        assertEquals("recursosLiberados",
                taskService.createTaskQuery().processInstanceId(instanceId).singleResult().getTaskDefinitionKey());
        assertFalse(runtimeService.hasVariable(instanceId, "aulaAsignada"));
    }

    private static String asSequenceFlows(String xml) {
        return xml.replaceAll("<association (id=\"[^\"]+\" sourceRef=\"[^\"]+\" targetRef=\"[^\"]+\")[^>]*?(?:/>|></association>)",
                "<sequenceFlow $1></sequenceFlow>");
    }

    private static int count(String text, String token) {
        return text.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }

    private static String read(String resource) throws Exception {
        try (InputStream input = ModelerExportFixerTest.class.getClassLoader().getResourceAsStream(resource)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
