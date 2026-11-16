package flowable.proposal;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;

/**
 * Cuenta las versiones de la propuesta. La propuesta original es la versión 1 (implícita)
 * y cada mensaje msgModificaciones genera la siguiente: 2 en la primera, 3 en la segunda, etc.
 * La web la lee en "Revisar propuesta" para numerar el PDF.
 */
public class IncrementProposalVersion implements ExecutionListener {
    private static final long serialVersionUID = 1L;

    @Override
    public void notify(DelegateExecution execution) {
        Object actual = execution.getVariable("versionPropuesta");
        long version = actual instanceof Number ? ((Number) actual).longValue() : 1L;
        execution.setVariable("versionPropuesta", version + 1);
    }
}
