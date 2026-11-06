package simulador;

import java.util.Map;

/** Instancia del proceso "Proyecto Cursos" tal como la devuelve la API REST de Flowable. */
public final class Solicitud {
    private final String id;
    private final String businessKey;
    private final Map<String, Object> variables;
    private final Etapa etapa;

    public Solicitud(String id, String businessKey, Map<String, Object> variables) {
        this(id, businessKey, variables, null);
    }

    private Solicitud(String id, String businessKey, Map<String, Object> variables, Etapa etapa) {
        this.id = id;
        this.businessKey = businessKey;
        this.variables = variables;
        this.etapa = etapa;
    }

    /** Copia de la solicitud con la etapa en que está el proceso. */
    public Solicitud conEtapa(Etapa nueva) {
        return new Solicitud(id, businessKey, variables, nueva);
    }

    /** Etapa del proceso, o null si todavía no se consultó. */
    public Etapa etapa() {
        return etapa;
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

    /** Valor de una variable como texto, o "-" si la solicitud no la tiene. */
    public String variable(String nombre) {
        Object valor = variables.get(nombre);
        return valor == null ? "-" : valor.toString();
    }

    public String nombre() {
        // El backend guarda el nombre de la empresa en empresaNombre; "nombre" queda por compatibilidad
        for (String variable : new String[] {"empresaNombre", "nombre"}) {
            Object valor = variables.get(variable);
            if (valor != null) {
                return valor.toString();
            }
        }
        return "(sin nombre)";
    }

    @Override
    public String toString() {
        return id + "  " + nombre() + (businessKey == null ? "" : "  [" + businessKey + "]");
    }
}
