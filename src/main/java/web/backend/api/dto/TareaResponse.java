package web.backend.api.dto;

/**
 * Tarea de usuario tal como la recibe la web. Usa los mismos nombres de campo que Flowable
 * (los que ya lee normalizarTarea en el adaptador), así la web no cambia entre mock y backend.
 * Las fechas van como texto, en el formato exacto que devuelve el motor.
 */
public class TareaResponse {
    private final String id;
    private final String name;
    private final String assignee;
    private final String taskDefinitionKey;
    private final String processInstanceId;
    private final String createTime;
    private final String dueDate;

    /**
     * Recibe el id de la tarea, su nombre, el usuario asignado (null si nadie la tomó), el id
     * de la tarea en el BPMN, el id de la instancia y las fechas de creación y de vencimiento
     * (esta última null si no tiene).
     */
    public TareaResponse(String id, String name, String assignee, String taskDefinitionKey,
            String processInstanceId, String createTime, String dueDate) {
        this.id = id;
        this.name = name;
        this.assignee = assignee;
        this.taskDefinitionKey = taskDefinitionKey;
        this.processInstanceId = processInstanceId;
        this.createTime = createTime;
        this.dueDate = dueDate;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAssignee() {
        return assignee;
    }

    public String getTaskDefinitionKey() {
        return taskDefinitionKey;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public String getCreateTime() {
        return createTime;
    }

    public String getDueDate() {
        return dueDate;
    }
}
