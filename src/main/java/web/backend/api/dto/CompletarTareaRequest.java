package web.backend.api.dto;

import java.util.List;

/**
 * Cuerpo de POST /api/tareas/{id}/completar: {"variables": [{"name", "type", "value"}]}.
 * Cualquier otro campo que mande la web (por ejemplo "action" o "assignee") se descarta al
 * leer el JSON: el backend arma de nuevo el pedido al motor.
 */
public class CompletarTareaRequest {
    private List<Variable> variables;

    /** Variables a guardar al completar; puede faltar si la tarea no pide datos. */
    public List<Variable> getVariables() {
        return variables;
    }

    public void setVariables(List<Variable> variables) {
        this.variables = variables;
    }

    /**
     * Una variable con la forma de Flowable. Qué nombres y tipos se aceptan lo decide
     * TareaService (lista de escritura).
     */
    public static class Variable {
        private String name;
        private String type;
        private Object value;

        /** Constructor vacío para que Jackson lea el JSON. */
        public Variable() {
        }

        /** Recibe el nombre, el tipo de Flowable (string, long, boolean, date) y el valor. */
        public Variable(String name, String type, Object value) {
            this.name = name;
            this.type = type;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }
    }
}
