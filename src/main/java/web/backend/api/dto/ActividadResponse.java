package web.backend.api.dto;

/** Actividad del proceso que está en curso: nombre del BPMN, tipo de Flowable y desde cuándo. */
public class ActividadResponse {
    public final String nombre;
    public final String tipo;
    public final String desde;

    public ActividadResponse(String nombre, String tipo, String desde) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.desde = desde;
    }
}
