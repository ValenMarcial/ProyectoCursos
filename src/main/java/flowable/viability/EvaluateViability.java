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

        // Consultar al RuntimeService las instancias activas del proceso, excluyendo la actual por su ID
        long othersInstancesActives = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(processDefinitionKey)
                .list()
                .stream()
                .filter(instance -> !instance.getId().equals(execution.getProcessInstanceId()))
                .count();

        // Instanciamos el objeto AmountCursos con la cantidad de instancias activas para Drools
        execution.setVariableLocal("amountCursos", new AmountCursos(othersInstancesActives));
    }
}
