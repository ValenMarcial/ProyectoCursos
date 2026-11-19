package flowable.viability;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import java.util.Collection;

public class ViabilityResult implements ExecutionListener {
    @Override
    public void notify(DelegateExecution execution) {
        Collection<?> rawResult = (Collection<?>) execution.getVariable("isViable");
        
        // Viable si Drools insertó Boolean.TRUE
        boolean isViable = rawResult != null && rawResult.contains(Boolean.TRUE);
        
        execution.setVariable("isViable", isViable);
    }
}