package flowable.report;

import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;

/**
 * Valida el único campo del formulario de "Elaborar informe de resultados". La ruta del PDF
 * no se valida acá: la genera la web al subir el archivo y la guarda SaveReport.
 */
public class ValidateReport implements TaskListener {
    private static final long serialVersionUID = 1L;

    @Override
    public void notify(DelegateTask task) {
        Object report = task.getVariable("informeResultados");
        if (!(report instanceof String) || ((String) report).isBlank()) {
            throw new FlowableIllegalArgumentException("El informe de resultados es obligatorio.");
        }
    }
}
