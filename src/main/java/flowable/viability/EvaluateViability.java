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

        // Cursos en ejecución: instancias activas que ya tienen cursoId
        long othersInstancesActives = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(processDefinitionKey)
                .variableExists("cursoId")
                .count();

        // Hecho para Drools
        execution.setVariableLocal("amountCursos", new AmountCursos(othersInstancesActives));
    }
}
