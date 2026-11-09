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

        // Cursos en ejecución: instancias activas que ya pasaron por "Habilitar acceso de la empresa",
        // que es la que crea el curso y guarda cursoId. Las solicitudes en evaluación o negociación no
        // cuentan (aclaración del profesor). La instancia actual queda afuera sola: todavía no tiene cursoId.
        long othersInstancesActives = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(processDefinitionKey)
                .variableExists("cursoId")
                .count();

        // Instanciamos el objeto AmountCursos con la cantidad de instancias activas para Drools
        execution.setVariableLocal("amountCursos", new AmountCursos(othersInstancesActives));
    }
}
