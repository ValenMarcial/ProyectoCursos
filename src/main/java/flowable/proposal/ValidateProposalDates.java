package flowable.proposal;

import java.util.Date;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;

public class ValidateProposalDates implements TaskListener {
    private static final long serialVersionUID = 1L;

    @Override
    public void notify(DelegateTask delegateTask) {
        Long budget = (Long) delegateTask.getVariable("presupuesto");
        Date startDate = (Date) delegateTask.getVariable("fechaInicioPosible");
        Date endDate = (Date) delegateTask.getVariable("fechaFinPropuesta");

        if (budget == null) {
            throw new FlowableException("El presupuesto es obligatorio y debe ser un número mayor o igual a 0.");
        }
        if (budget < 0) {
            throw new FlowableException("El presupuesto debe ser mayor o igual a 0.");
        }

        if (startDate == null || endDate == null) {
            throw new FlowableException("Las fechas posibles de inicio y finalización son obligatorias.");
        }

        if (endDate.before(startDate)) {
            throw new FlowableException("La fecha de finalización debe ser posterior o igual a la fecha de inicio.");
        }
    }
}
