package flowable.viability;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.impl.util.CommandContextUtil;

public class EvaluateViability implements ExecutionListener {
    @Override
    public void notify(DelegateExecution execution) {
        String processDefinitionKey = execution.getProcessDefinitionId().split(":")[0];

        RuntimeService runtimeService = CommandContextUtil.getProcessEngineConfiguration().getRuntimeService();

        // Consulta al RuntimeService las instancias activas del proceso
        long totalActives = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(processDefinitionKey)
                .count();

        // Descuenta la instancia actual
        long othersInstancesActives = totalActives > 0 ? totalActives - 1 : 0;

        // Instanciamos el objeto AmountCursos con la cantidad de instancias activas para Drools
        execution.setVariableLocal("amountCursos", new AmountCursos(othersInstancesActives));
    }
}
