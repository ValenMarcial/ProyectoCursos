package flowable.assignment;

import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;

public class CaptureDelegatedTeacher implements TaskListener {
    private static final long serialVersionUID = 1L;
    private static final String TEAM_LEAD = "jefeDeEquipo";
    private static final String SELECTED_TEACHER_VARIABLE = "docenteSeleccionado";

    @Override
    public void notify(DelegateTask delegateTask) {
        String delegatedUser = delegateTask.getAssignee();
        if (delegatedUser != null && !TEAM_LEAD.equals(delegatedUser)) {
            delegateTask.setVariable(SELECTED_TEACHER_VARIABLE, delegatedUser);
        }
    }
}
