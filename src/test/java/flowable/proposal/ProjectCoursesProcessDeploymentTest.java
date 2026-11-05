package flowable.proposal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.flowable.engine.ProcessEngine;
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
}