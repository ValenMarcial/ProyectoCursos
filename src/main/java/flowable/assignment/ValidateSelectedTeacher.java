package flowable.assignment;

import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.task.service.delegate.DelegateTask;

public class ValidateSelectedTeacher implements TaskListener {
    private static final long serialVersionUID = 1L;

    @Override
    public void notify(DelegateTask task) {
        Object selectedTeacher = task.getVariable("docenteSeleccionado");
        if (!(selectedTeacher instanceof String) || ((String) selectedTeacher).isBlank()) {
            throw new FlowableIllegalArgumentException("Debe indicar el ID del docente seleccionado.");
        }

        long matchingUsers = CommandContextUtil.getProcessEngineConfiguration().getIdentityService()
                .createUserQuery()
                .userId((String) selectedTeacher)
                .memberOfGroup("Docentes")
                .count();
        if (matchingUsers == 0) {
            throw new FlowableIllegalArgumentException("El docente seleccionado debe pertenecer al grupo Docentes.");
        }
    }
}
