package web.backend.api.dto;

/**
 * Error para la web, con la forma {"message": "..."}. Es la misma forma que usa Flowable,
 * así el adaptador de la web traduce igual los errores del backend y los del motor.
 */
public class MensajeErrorResponse {
    private final String message;

    public MensajeErrorResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
