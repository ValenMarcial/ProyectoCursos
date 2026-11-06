package flowable.assignment;

import java.util.Comparator;

import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.idm.api.User;
import org.flowable.task.service.delegate.DelegateTask;

/**
 * Task Listener de creación de "Confeccionar lista definitiva" (enunciado 3.4): asigna la tarea
 * al integrante de Administracion con menos tareas pendientes asignadas.
 */
public class AssignToLeastBusyAdministrative implements TaskListener {
    private static final long serialVersionUID = 1L;
    private static final String GROUP = "Administracion";

    @Override
    public void notify(DelegateTask delegateTask) {
        ProcessEngineConfigurationImpl config = CommandContextUtil.getProcessEngineConfiguration();
        TaskService taskService = config.getTaskService();

        // Solo cuentan las tareas asignadas al usuario: las que tienen al grupo como candidato
        // y nadie reclamó no tienen assignee. En empate, gana el primer ID en orden alfabético.
        config.getIdentityService().createUserQuery().memberOfGroup(GROUP).list().stream()
                .map(User::getId)
                .min(Comparator.comparingLong((String userId) -> taskService.createTaskQuery().taskAssignee(userId).count())
                        .thenComparing(Comparator.naturalOrder()))
                // Sin integrantes en el grupo, la tarea queda para que la reclame cualquiera de Administracion
                .ifPresent(delegateTask::setAssignee);
    }
}
