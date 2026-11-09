package flowable.resources;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;

public class ValidateResourceSelection implements TaskListener {
    private static final long serialVersionUID = 1L;

    @Override
    public void notify(DelegateTask task) {
        boolean hasSelectedResource = Boolean.TRUE.equals(task.getVariable("aulaFisica"))
                || Boolean.TRUE.equals(task.getVariable("aulaVirtual"))
                || Boolean.TRUE.equals(task.getVariable("equipamiento"));

        if (!hasSelectedResource) {
            throw new FlowableException("Debe seleccionar al menos un recurso.");
        }
    }
}