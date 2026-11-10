package web.backend.service;

/**
 * Error al iniciar o usar la sesión del personal.
 * El motivo trae el mensaje que ve la persona, así los textos quedan en un solo lugar.
 */
public class SesionException extends RuntimeException {
    /** Causas posibles, cada una con su mensaje en español para la web. */
    public enum Motivo {
        DATOS_INVALIDOS("Ingresá el usuario y la clave. El usuario no puede contener \":\"."),
        CREDENCIALES_INVALIDAS("Usuario o clave incorrectos."),
        SIN_SESION("No hay una sesión iniciada."),
        FLOWABLE_NO_RESPONDE("No se pudo validar el usuario porque Flowable no responde.");

        private final String mensaje;

        Motivo(String mensaje) {
            this.mensaje = mensaje;
        }

        public String getMensaje() {
            return mensaje;
        }
    }

    private final Motivo motivo;

    public SesionException(Motivo motivo) {
        super(motivo.getMensaje());
        this.motivo = motivo;
    }

    public SesionException(Motivo motivo, Throwable cause) {
        super(motivo.getMensaje(), cause);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
