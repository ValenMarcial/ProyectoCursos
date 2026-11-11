package web.backend.service;

/** Error de la API de empresas. El motivo trae el mensaje que ve la empresa. */
public class EmpresaException extends RuntimeException {
    public enum Motivo {
        DATOS_INVALIDOS("Ingresá el usuario y la clave."),
        CREDENCIALES_INVALIDAS("Usuario o clave incorrectos."),
        CURSO_NO_ENCONTRADO("No existe el curso."),
        FLOWABLE_NO_RESPONDE("No se pudieron consultar las actividades porque Flowable no responde.");

        private final String mensaje;

        Motivo(String mensaje) {
            this.mensaje = mensaje;
        }

        public String getMensaje() {
            return mensaje;
        }
    }

    private final Motivo motivo;

    public EmpresaException(Motivo motivo) {
        this(motivo, null);
    }

    public EmpresaException(Motivo motivo, Throwable cause) {
        super(motivo.getMensaje(), cause);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
