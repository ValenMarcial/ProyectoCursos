package simulador;

import java.util.Map;

/** Instancia del proceso "Proyecto Cursos" tal como la devuelve la API REST de Flowable. */
public final class Solicitud {
    private final String id;
    private final String businessKey;
    private final Map<String, Object> variables;

    public Solicitud(String id, String businessKey, Map<String, Object> variables) {
        this.id = id;
        this.businessKey = businessKey;
        this.variables = variables;
    }

    public String id() {
        return id;
    }

    public String businessKey() {
        return businessKey;
    }

    public Map<String, Object> variables() {
        return variables;
    }

    public String nombre() {
        Object nombre = variables.get("nombre");
        return nombre == null ? "(sin nombre)" : nombre.toString();
    }

    @Override
    public String toString() {
        return id + "  " + nombre() + (businessKey == null ? "" : "  [" + businessKey + "]");
    }
}
