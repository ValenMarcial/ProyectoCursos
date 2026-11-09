package materiales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.runtime.Execution;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Consultas 4.3 sobre historial real (H2), con el reloj del motor controlado para fijar las duraciones. */
class AnalisisTest {
    private static final long MIN = 60_000L;
    private static final long T0 = 1_700_000_000_000L;

    private static ProcessEngine motor;

    private static Analisis analisis;

    @BeforeAll
    static void arrancar() {
        motor = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:analisis;DB_CLOSE_DELAY=-1")
                .setAsyncExecutorActivate(false)
                .buildProcessEngine();
        motor.getRepositoryService().createDeployment().addClasspathResource("curso-analisis.bpmn20.xml").deploy();
        analisis = new Analisis(motor.getHistoryService());
    }

    @AfterAll
    static void cerrar() {
        motor.close();
    }

    @BeforeEach
    void limpiar() {
        motor.getRuntimeService().createProcessInstanceQuery().list()
                .forEach(i -> motor.getRuntimeService().deleteProcessInstance(i.getId(), "limpieza"));
        motor.getHistoryService().createHistoricProcessInstanceQuery().list()
                .forEach(i -> motor.getHistoryService().deleteHistoricProcessInstance(i.getId()));
    }

    private void reloj(long minutos) {
        motor.getProcessEngineConfiguration().getClock().setCurrentTime(new Date(T0 + minutos * MIN));
    }

    private String iniciar(String resultado, boolean fisica, boolean virtual, boolean equipamiento) {
        return motor.getRuntimeService().startProcessInstanceByKey("process-proyecto-curso",
                Map.of("resultado", resultado, "aulaFisica", fisica, "aulaVirtual", virtual,
                        "equipamiento", equipamiento)).getId();
    }

    private void completarTarea(String instancia, String actividad, String usuario) {
        Task t = motor.getTaskService().createTaskQuery().processInstanceId(instancia).taskDefinitionKey(actividad)
                .singleResult();
        motor.getTaskService().claim(t.getId(), usuario);
        motor.getTaskService().complete(t.getId());
    }

    private void recibirNomina(String instancia) {
        Execution e = motor.getRuntimeService().createExecutionQuery().processInstanceId(instancia)
                .activityId(Analisis.RECIBIR_NOMINA).singleResult();
        motor.getRuntimeService().trigger(e.getId());
    }

    /** Un curso que completa la preparación: inscripciones 30 min, materiales 60 min, recursos 45 min. */
    private String cursoCompleto(String resultado, long desde) {
        reloj(desde);
        String i = iniciar(resultado, true, true, false);
        reloj(desde + 5);
        completarTarea(i, Analisis.EVALUAR_RECURSOS, "admin1");
        reloj(desde + 10);
        recibirNomina(i);
        reloj(desde + 15);
        completarTarea(i, Analisis.ACCIONES_RECURSOS.get(0), "admin2");
        reloj(desde + 30);
        completarTarea(i, Analisis.LISTA_DEFINITIVA, "admin1");
        reloj(desde + 45);
        completarTarea(i, Analisis.ACCIONES_RECURSOS.get(1), "admin2");
        reloj(desde + 60);
        completarTarea(i, Analisis.PREPARAR_MATERIALES, "admin1");
        return i;
    }

    @Test
    void resultadosCuentaSoloTerminadasYCalculaElPorcentajeSobreElTotal() {
        cursoCompleto("finalizado", 0);
        cursoCompleto("finalizado", 100);
        iniciar("en_curso", false, false, false);   // sigue activa: no cuenta
        String cancelado = iniciar("cancelado", false, false, false);
        Execution e = motor.getRuntimeService().createExecutionQuery().processInstanceId(cancelado)
                .messageEventSubscriptionName("msgCancel").singleResult();
        motor.getRuntimeService().messageEventReceived("msgCancel", e.getId());

        Map<String, Integer> r = analisis.resultados();
        assertEquals(2, r.get("finalizado"));
        assertEquals(1, r.get("cancelado"));
        assertEquals(0, r.get("rechazado"));
        assertEquals(3, r.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(66.7, Analisis.porcentaje(2, r), 0.05);
    }

    @Test
    void duracionesPromedioDeCadaRama() {
        cursoCompleto("finalizado", 0);
        Analisis.Duraciones d = analisis.duraciones();
        assertEquals(30 * MIN, d.inscripciones.longValue());
        assertEquals(60 * MIN, d.materiales.longValue());
        assertEquals(45 * MIN, d.recursos.longValue());
        assertEquals(1, d.cursosRecursos);
    }

    @Test
    void duracionesExcluyenLaPreparacionCancelada() {
        cursoCompleto("finalizado", 0);
        // Segundo curso: materiales ya terminó (10 min), pero la empresa cancela antes de cerrar la preparación.
        reloj(200);
        String i = iniciar("cancelado", true, false, false);
        reloj(210);
        completarTarea(i, Analisis.PREPARAR_MATERIALES, "admin1");
        reloj(220);
        Execution e = motor.getRuntimeService().createExecutionQuery().processInstanceId(i)
                .messageEventSubscriptionName("msgCancel").singleResult();
        motor.getRuntimeService().messageEventReceived("msgCancel", e.getId());

        Analisis.Duraciones d = analisis.duraciones();
        assertEquals(1, d.cursosMateriales);
        assertEquals(60 * MIN, d.materiales.longValue());
    }

    @Test
    void sinCursosNoHayPromedios() {
        Analisis.Duraciones d = analisis.duraciones();
        assertNull(d.materiales);
        assertEquals("sin datos", Analisis.Duraciones.formato(d.materiales));
    }

    @Test
    void tareasPorUsuarioSoloCuentaLasCompletadas() {
        cursoCompleto("finalizado", 0);     // admin1: evaluar, lista, materiales (3); admin2: aula física y virtual (2)
        reloj(300);
        String i = iniciar("cancelado", true, false, false);
        completarTarea(i, Analisis.PREPARAR_MATERIALES, "admin2");   // admin2: 3 en total
        // Esta tarea queda reclamada y se cancela con la preparación: no debe contarse.
        Task t = motor.getTaskService().createTaskQuery().processInstanceId(i)
                .taskDefinitionKey(Analisis.EVALUAR_RECURSOS).singleResult();
        motor.getTaskService().claim(t.getId(), "admin1");
        Execution e = motor.getRuntimeService().createExecutionQuery().processInstanceId(i)
                .messageEventSubscriptionName("msgCancel").singleResult();
        motor.getRuntimeService().messageEventReceived("msgCancel", e.getId());

        Map<String, Integer> t3 = analisis.tareasPorUsuario();
        assertEquals(3, t3.get("admin1"));
        assertEquals(3, t3.get("admin2"));
    }

    @Test
    void informeEnTextoIncluyeLasTresSecciones() {
        cursoCompleto("finalizado", 0);
        String texto = analisis.informe();
        assertTrue(texto.contains("4.3.1"));
        assertTrue(texto.contains("0 h 30 min 00 s"));
        assertTrue(texto.contains("admin1"));
        assertTrue(List.of("4.3.2", "4.3.3").stream().allMatch(texto::contains));
    }
}
