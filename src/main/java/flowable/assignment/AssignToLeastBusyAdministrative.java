package flowable.assignment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.idm.api.User;
import org.flowable.task.service.delegate.DelegateTask;

/**
 * Task Listener de creación de "Confeccionar lista definitiva" (enunciado 3.4): asigna la tarea
 * al integrante de Administracion con menos tareas pendientes asignadas,
 * desempatando con una estrategia. Flowable usa el constructor sin argumentos, que elige al azar;
 * los tests pueden pasar una estrategia fija.
 */
public class AssignToLeastBusyAdministrative implements TaskListener {
    private static final long serialVersionUID = 1L;
    private static final String GROUP = "Administracion";

    /** Recibe los empatados en orden alfabético y devuelve el elegido. */
    private final transient Function<List<String>, String> tieBreaker;

    public AssignToLeastBusyAdministrative() {
        this(tied -> tied.get(ThreadLocalRandom.current().nextInt(tied.size())));
    }

    AssignToLeastBusyAdministrative(Function<List<String>, String> tieBreaker) {
        this.tieBreaker = tieBreaker;
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        ProcessEngineConfigurationImpl config = CommandContextUtil.getProcessEngineConfiguration();
        TaskService taskService = config.getTaskService();

        List<String> administrativeUsers = config.getIdentityService().createUserQuery().memberOfGroup(GROUP).list().stream()
                .map(User::getId)
                .collect(Collectors.toList());
        if (administrativeUsers.isEmpty()) {
            return;
        }

        Map<String, Long> taskCounts = new HashMap<>();
        for (String userId : administrativeUsers) {
            taskCounts.put(userId, taskService.createTaskQuery().taskAssignee(userId).count());
        }
        long minimumTaskCount = taskCounts.values().stream().mapToLong(Long::longValue).min().getAsLong();

        List<String> leastBusyUsers = taskCounts.entrySet().stream()
                .filter(entry -> entry.getValue() == minimumTaskCount)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
        delegateTask.setAssignee(tieBreaker.apply(leastBusyUsers));
    }
}
