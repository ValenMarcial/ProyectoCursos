package web.backend.service;

/**
 * Error al consultar o usar tareas del proceso. Mismo patrón que SesionException: el motivo
 * trae el mensaje que ve la persona, así los textos quedan en un solo lugar. El estado HTTP
 * de cada motivo lo decide TareaErrorHandler.
 */
public class TareaException extends RuntimeException {
    /** Causas posibles, cada una con su mensaje en español para la web. */
    public enum Motivo {
        ALCANCE_INVALIDO("El alcance tiene que ser \"mias\" o \"grupo\"."),
        PAGINA_INVALIDA("La página no es válida: start tiene que ser 0 o más y size, de 1 a "
                + TareaService.TAMANIO_MAXIMO + "."),
        // El mismo mensaje para "no existe" y "no es tuya": no se revela que la tarea existe.
        TAREA_NO_ENCONTRADA("La tarea no existe o no tenés permiso para verla."),
        INSTANCIA_NO_ENCONTRADA("La instancia no existe o no tenés tareas activas en ella."),
        FLOWABLE_NO_RESPONDE("No se pudo consultar las tareas porque Flowable no responde.");

        private final String mensaje;

        Motivo(String mensaje) {
            this.mensaje = mensaje;
        }

        public String getMensaje() {
            return mensaje;
        }
    }

    private final Motivo motivo;

    public TareaException(Motivo motivo) {
        super(motivo.getMensaje());
        this.motivo = motivo;
    }

    public TareaException(Motivo motivo, Throwable cause) {
        super(motivo.getMensaje(), cause);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
