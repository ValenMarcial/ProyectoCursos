package flowable.report;

import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;

/**
 * Valida el formulario de "Elaborar informe de resultados". El texto (informeResultados) es
 * obligatorio salvo que la persona haya adjuntado un PDF: en ese caso la web marca
 * informeAdjunto en verdadero y el texto puede faltar. Solo vale el booleano true; cualquier
 * otro valor (ausente, falso o de otro tipo) cuenta como "sin PDF adjunto".
 * La ruta del PDF no se valida acá: la genera la web al subir el archivo y la controla SaveReport.
 */
public class ValidateReport implements TaskListener {
    private static final long serialVersionUID = 1L;

    @Override
    public void notify(DelegateTask task) {
        if (Boolean.TRUE.equals(task.getVariable("informeAdjunto"))) {
            return;
        }
        Object report = task.getVariable("informeResultados");
        if (!(report instanceof String) || ((String) report).isBlank()) {
            throw new FlowableIllegalArgumentException(
                    "El informe de resultados es obligatorio si no se adjunta un PDF.");
        }
    }
}
