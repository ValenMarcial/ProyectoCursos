package web.backend.service;

import java.util.Locale;

import web.backend.service.TareaException.Motivo;

/**
 * Qué tareas pide la persona: MIAS (asignadas a ella) o GRUPO (sin asignar, de alguno de sus
 * grupos). En la URL van en minúscula ("mias", "grupo"); la conversión se hace acá y no en el
 * controller, porque Spring por defecto exige el nombre exacto del enum.
 */
public enum Alcance {
    MIAS,
    GRUPO;

    /**
     * Convierte el texto de la URL en el alcance. Solo acepta "mias" o "grupo", en minúscula.
     * Lanza TareaException(ALCANCE_INVALIDO) si falta o no es ninguno de los dos.
     */
    public static Alcance desde(String texto) {
        for (Alcance alcance : values()) {
            if (alcance.name().toLowerCase(Locale.ROOT).equals(texto)) {
                return alcance;
            }
        }
        throw new TareaException(Motivo.ALCANCE_INVALIDO);
    }
}
