package flowable.resources;

import java.util.List;

import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;

public class ValidateResourceSelection implements TaskListener {
    private static final long serialVersionUID = 1L;

    /** Variables que evalúan las condiciones del gateway "Recursos necesarios". */
    private static final List<String> RESOURCES = List.of("aulaFisica", "aulaVirtual", "equipamiento");

    @Override
    public void notify(DelegateTask task) {
        boolean hasSelectedResource = false;
        for (String resource : RESOURCES) {
            Object value = task.getVariable(resource);
            // El gateway falla si una condición usa una variable que no existe: lo que no vino cuenta como false.
            if (value == null) {
                task.setVariable(resource, false);
            }
            hasSelectedResource |= Boolean.TRUE.equals(value);
        }

        if (!hasSelectedResource) {
            throw new FlowableIllegalArgumentException("Debe seleccionar al menos un recurso.");
        }
    }
}
