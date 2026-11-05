package simulador;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lógica del simulador: decide qué comunicaciones de la empresa están habilitadas para una
 * solicitud (según lo que el proceso está esperando) y las envía con sus datos.
 */
public class Simulador {
    private final FlowableClient flowable;

    public Simulador(FlowableClient flowable) {
        this.flowable = flowable;
    }

    public List<Solicitud> solicitudes() {
        return flowable.listarSolicitudes();
    }

    /** Comunicaciones que el proceso está esperando ahora mismo para esa solicitud. */
    public List<Comunicacion> habilitadas(String instanciaId) {
        List<Comunicacion> habilitadas = new ArrayList<>();
        for (Comunicacion comunicacion : Comunicacion.values()) {
            if (!ejecuciones(instanciaId, comunicacion).isEmpty()) {
                habilitadas.add(comunicacion);
            }
        }
        return habilitadas;
    }

    /**
     * Envía una comunicación a la instancia.
     *
     * @param datos valores de los campos de la comunicación, por nombre de variable
     * @throws IllegalArgumentException si falta un dato o no es válido
     * @throws IllegalStateException si el proceso no espera esa comunicación en este momento
     */
    public void enviar(String instanciaId, Comunicacion comunicacion, Map<String, String> datos) {
        Map<String, String> variables = validar(comunicacion, datos);

        List<String> ejecuciones = ejecuciones(instanciaId, comunicacion);
        if (ejecuciones.isEmpty()) {
            throw new IllegalStateException("La solicitud " + instanciaId + " no espera ahora: "
                    + comunicacion.titulo() + ".");
        }

        // Si hay más de una ejecución esperando (p. ej. paralelas), se entrega a la primera.
        String ejecucionId = ejecuciones.get(0);
        if (comunicacion.via() == Comunicacion.Via.MENSAJE) {
            flowable.enviarMensaje(ejecucionId, comunicacion.mensaje(), variables);
        } else {
            flowable.senalar(ejecucionId, variables);
        }
    }

    private List<String> ejecuciones(String instanciaId, Comunicacion comunicacion) {
        if (comunicacion.via() == Comunicacion.Via.MENSAJE) {
            return flowable.ejecucionesEsperandoMensaje(instanciaId, comunicacion.mensaje());
        }
        return flowable.ejecucionesEnActividad(instanciaId, comunicacion.actividadId());
    }

    private static Map<String, String> validar(Comunicacion comunicacion, Map<String, String> datos) {
        Map<String, String> variables = new LinkedHashMap<>();
        for (Comunicacion.Campo campo : comunicacion.campos()) {
            String valor = datos == null ? null : datos.get(campo.variable());
            if (valor == null || valor.trim().isEmpty()) {
                throw new IllegalArgumentException("Falta el dato '" + campo.variable() + "': "
                        + campo.descripcion() + ".");
            }
            valor = valor.trim();
            if (comunicacion == Comunicacion.ACEPTACION && campo.variable().equals("fechaInicio")) {
                try {
                    LocalDateTime.parse(valor);
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("fechaInicio debe tener formato ISO-8601, por ejemplo "
                            + "2026-11-20T09:00:00 (recibido: " + valor + ").");
                }
            }
            variables.put(campo.variable(), valor);
        }
        return variables;
    }
}
