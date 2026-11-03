package flowable.viability;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import java.util.Collection;

public class ViabilityResult implements ExecutionListener {
    @Override
    public void notify(DelegateExecution execution) {
        Collection<?> rawResult = (Collection<?>) execution.getVariable("isViable");
        
        // Extrae si la colección insertada por Drools contiene Boolean.TRUE
        boolean isViable = rawResult != null && rawResult.contains(Boolean.TRUE);
        
        // Guarda la variable booleana pura en el proceso
        execution.setVariable("isViable", isViable);
    }
}