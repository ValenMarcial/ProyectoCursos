package flowable.classroom;

import java.util.List;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Reservar aula": simula la llamada al web service de aulas (enunciado 4.1.3). Un aula está ocupada
 * mientras alguna instancia activa la tenga en aulaAsignada. Sin aulas libres lanza el error SIN_AULAS
 * (escenario 7).
 */
public class ReserveClassroom implements JavaDelegate {
    public static final String ERROR_CODE = "SIN_AULAS";
    static final List<String> AULAS = List.of("Aula 1", "Aula 2");
    private static final Logger LOG = LoggerFactory.getLogger(ReserveClassroom.class);

    @Override
    public void execute(DelegateExecution execution) {
        RuntimeService runtimeService = CommandContextUtil.getProcessEngineConfiguration().getRuntimeService();
        LOG.info("[WS Aulas] Consultando disponibilidad para {}", execution.getProcessInstanceId());

        String aula = AULAS.stream()
                .filter(candidata -> runtimeService.createProcessInstanceQuery()
                        .variableValueEquals("aulaAsignada", candidata).count() == 0)
                .findFirst()
                .orElseThrow(() -> new BpmnError(ERROR_CODE, "No hay aulas físicas disponibles"));

        execution.setVariable("aulaAsignada", aula);
        LOG.info("[WS Aulas] {} reservada para {}", aula, execution.getProcessInstanceId());
    }
}
