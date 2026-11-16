package web.backend.flowable;

import org.springframework.http.MediaType;

/**
 * Error que respondió el motor (400 de negocio, 404, 409, 500...). Guarda el estado, el cuerpo y
 * el content-type tal cual llegaron, para que la API los reenvíe a la web sin tocarlos.
 * No se usa para el 401/403 del motor (falla de la cuenta del backend) ni para errores de
 * conexión: esos llegan como RestClientException y terminan en 502.
 */
public class FlowableRespuestaException extends RuntimeException {
    private final int estado;
    private final byte[] cuerpo;
    private final MediaType tipoContenido;

    /**
     * Recibe el estado HTTP del motor, el cuerpo en bytes (sin decodificar, para no alterarlo),
     * el content-type (puede ser null si el motor no lo mandó) y la excepción original.
     */
    public FlowableRespuestaException(int estado, byte[] cuerpo, MediaType tipoContenido, Throwable causa) {
        super("Flowable respondió " + estado + ".", causa);
        this.estado = estado;
        this.cuerpo = cuerpo == null ? new byte[0] : cuerpo.clone();
        this.tipoContenido = tipoContenido;
    }

    /** Estado HTTP que respondió el motor. */
    public int getEstado() {
        return estado;
    }

    /** Cuerpo exacto de la respuesta del motor (una copia, para que nadie lo modifique). */
    public byte[] getCuerpo() {
        return cuerpo.clone();
    }

    /** Content-type de la respuesta del motor, o null si no vino. */
    public MediaType getTipoContenido() {
        return tipoContenido;
    }
}
