package flowable.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValidateCourseEndDateTest {
    private ProcessEngine engine;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        StandaloneInMemProcessEngineConfiguration config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:fecha-curso-" + System.nanoTime() + ";DB_CLOSE_DELAY=1000");
        engine = config.buildProcessEngine();
        taskService = engine.getTaskService();
        engine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/desarrollar-curso-fecha-test.bpmn20.xml")
                .deploy();
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void permiteFinalizarCuandoLaFechaDeFinYaPaso() {
        completarConFecha(LocalDate.now(ZoneOffset.UTC).minusDays(1));
    }

    @Test
    void permiteFinalizarElMismoDiaDeLaFechaDeFin() {
        completarConFecha(LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    void rechazaFinalizarAntesDeLaFechaDeFin() {
        String instancia = iniciar();
        Task tarea = tarea(instancia);
        LocalDate fechaFin = LocalDate.now(ZoneOffset.UTC).plusDays(1);

        FlowableIllegalArgumentException error = assertThrows(FlowableIllegalArgumentException.class,
                () -> taskService.complete(tarea.getId(), Map.of(
                        "fechaFinPropuesta", fecha(fechaFin))));

        assertEquals("El curso no puede finalizarse antes del "
                + fechaFin.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT))
                + " (fecha de fin programada).", error.getMessage());
        assertEquals(1, taskService.createTaskQuery().taskId(tarea.getId()).count());
    }

    @Test
    void fallaSiFaltaLaFechaDeFin() {
        String instancia = iniciar();
        Task tarea = tarea(instancia);

        assertThrows(FlowableException.class, () -> taskService.complete(tarea.getId(), Map.of()));
        assertEquals(1, taskService.createTaskQuery().taskId(tarea.getId()).count());
    }

    private void completarConFecha(LocalDate fechaFin) {
        String instancia = iniciar();
        Task tarea = tarea(instancia);

        taskService.complete(tarea.getId(), Map.of("fechaFinPropuesta", fecha(fechaFin)));

        assertEquals(0, taskService.createTaskQuery().taskId(tarea.getId()).count());
    }

    private Date fecha(LocalDate dia) {
        return Date.from(dia.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private String iniciar() {
        return engine.getRuntimeService().startProcessInstanceByKey("test-validacion-fecha-curso").getId();
    }

    private Task tarea(String instancia) {
        return taskService.createTaskQuery().processInstanceId(instancia).singleResult();
    }
}
