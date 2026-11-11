package web.backend.api.dto;

/**
 * Variable de una instancia del proceso con la forma {"name", "type", "value"}, la que espera
 * obtenerVariables en el adaptador. No lleva el "scope" que agrega Flowable.
 */
public class VariableResponse {
    private final String name;
    private final String type;
    private final Object value;

    /**
     * Recibe el nombre de la variable, su tipo en Flowable (string, integer, boolean, date...)
     * y su valor tal como lo devolvió el motor (puede ser null).
     */
    public VariableResponse(String name, String type, Object value) {
        this.name = name;
        this.type = type;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }
}
