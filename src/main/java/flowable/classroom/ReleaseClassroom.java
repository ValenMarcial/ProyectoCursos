package flowable.classroom;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** "Liberar Aula" (compensación): simula la liberación en el web service de aulas. */
public class ReleaseClassroom implements JavaDelegate {
    private static final Logger LOG = LoggerFactory.getLogger(ReleaseClassroom.class);

    @Override
    public void execute(DelegateExecution execution) {
        LOG.info("[WS Aulas] {} liberada para {}", execution.getVariable("aulaAsignada"), execution.getProcessInstanceId());
        execution.removeVariable("aulaAsignada");
    }
}
