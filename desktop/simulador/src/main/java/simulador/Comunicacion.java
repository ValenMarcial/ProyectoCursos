package simulador;

import java.util.Collections;
import java.util.List;

/**
 * Comunicaciones de la empresa que el simulador puede enviar al proceso.
 * Los nombres de mensajes y variables son los de docs/contrato.md.
 */
public enum Comunicacion {
    ACEPTACION("Aceptación de la propuesta", Via.MENSAJE, "msgAceptacion", null,
            List.of(new Campo("fechaInicio", "Fecha de inicio acordada (ISO-8601, ej. 2026-11-20T09:00:00)"))),
    RECHAZO("Rechazo de la propuesta", Via.MENSAJE, "msgRechazo", null,
            Collections.emptyList()),
    MODIFICACIONES("Solicitud de modificaciones", Via.MENSAJE, "msgModificaciones", null,
            List.of(new Campo("observaciones", "Observaciones de la empresa"))),
    // "Recibir nómina" es un receive task (no un evento de mensaje): se despierta con una señal
    // a la ejecución que está en esa actividad.
    NOMINA("Envío de la nómina de participantes", Via.SENAL, null, "sid-40B80C57-5A63-4D5C-A43B-710C8B140370",
            List.of(new Campo("participantes", "Participantes, separados por coma"))),
    CANCELACION("Cancelación durante la preparación", Via.MENSAJE, "msgCancelacion", null,
            Collections.emptyList());

    public enum Via { MENSAJE, SENAL }

    /** Dato que la empresa aporta con la comunicación; se envía como variable del proceso. */
    public static final class Campo {
        private final String variable;
        private final String descripcion;

        public Campo(String variable, String descripcion) {
            this.variable = variable;
            this.descripcion = descripcion;
        }

        public String variable() {
            return variable;
        }

        public String descripcion() {
            return descripcion;
        }
    }

    private final String titulo;
    private final Via via;
    private final String mensaje;
    private final String actividadId;
    private final List<Campo> campos;

    Comunicacion(String titulo, Via via, String mensaje, String actividadId, List<Campo> campos) {
        this.titulo = titulo;
        this.via = via;
        this.mensaje = mensaje;
        this.actividadId = actividadId;
        this.campos = campos;
    }

    public String titulo() {
        return titulo;
    }

    public Via via() {
        return via;
    }

    /** Nombre del mensaje de Flowable; null si la vía es una señal. */
    public String mensaje() {
        return mensaje;
    }

    /** Id de la actividad que espera la señal; null si la vía es un mensaje. */
    public String actividadId() {
        return actividadId;
    }

    public List<Campo> campos() {
        return campos;
    }
}
