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
        NO_AUTORIZADA("La tarea no es tuya ni de tu grupo."),
        NO_ASIGNADA("La tarea no está asignada a vos: reclamala primero."),
        CUERPO_INVALIDO("El cuerpo del pedido no es un JSON válido con la forma {\"variables\": [...]}."),
        // El detalle agrega el nombre de la variable rechazada.
        VARIABLE_INVALIDA("Variable no permitida o con tipo o valor incorrecto:"),
        // Una sesión sin usuario no debería existir; se corta antes de que un claim vacío libere la tarea.
        USUARIO_INVALIDO("No hay un usuario válido en la sesión."),
        TAREA_SIN_INFORME("La tarea no es la de elaborar el informe de resultados."),
        INFORME_INVALIDO("El informe tiene que ser un archivo PDF no vacío, enviado en el campo \"archivo\"."),
        INFORME_MUY_GRANDE("El informe supera el tamaño máximo de " + InformeService.MAXIMO_MB + " MB."),
        INFORME_NO_ENCONTRADO("El informe no existe: todavía no se subió el PDF o el archivo no está en el servidor."),
        INFORME_NO_GUARDADO("No se pudo guardar el informe en el servidor."),
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

    /**
     * Recibe el motivo y un detalle que se agrega al final del mensaje (por ejemplo, el nombre
     * de la variable rechazada).
     */
    public TareaException(Motivo motivo, String detalle) {
        super(motivo.getMensaje() + " " + detalle);
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
