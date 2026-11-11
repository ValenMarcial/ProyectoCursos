package web.backend.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import web.backend.api.dto.MensajeErrorResponse;
import web.backend.flowable.FlowableRespuestaException;
import web.backend.service.TareaException;
import web.backend.service.TareaException.Motivo;

/**
 * Respuestas de error de los endpoints de tareas e instancias (limitado a esos controllers).
 * - Errores del backend: {"message": "..."} con el estado de cada motivo.
 * - Errores que respondió el motor: estado, cuerpo y content-type exactos, sin tocarlos.
 */
@RestControllerAdvice(assignableTypes = {TareaController.class, InstanciaController.class})
public class TareaErrorHandler {

    /** Error de las reglas del backend: devuelve {"message"} con el estado del motivo. */
    @ExceptionHandler(TareaException.class)
    public ResponseEntity<MensajeErrorResponse> manejarError(TareaException exception) {
        // getMessage y no el del motivo: puede traer un detalle (por ejemplo, la variable rechazada).
        return ResponseEntity.status(estado(exception.getMotivo()))
                .body(new MensajeErrorResponse(exception.getMessage()));
    }

    /** Cuerpo que no es JSON o no tiene la forma esperada: 400 con mensaje, en vez del error de Spring. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MensajeErrorResponse> manejarCuerpoInvalido() {
        return error(Motivo.CUERPO_INVALIDO);
    }

    /** start o size que no son números: se responde como página inválida, en vez del error de Spring. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<MensajeErrorResponse> manejarParametroInvalido() {
        return error(Motivo.PAGINA_INVALIDA);
    }

    /** Error que respondió el motor: se reenvía tal cual llegó. */
    @ExceptionHandler(FlowableRespuestaException.class)
    public ResponseEntity<byte[]> reenviarErrorDelMotor(FlowableRespuestaException exception) {
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(exception.getEstado());
        if (exception.getTipoContenido() != null) {
            respuesta.contentType(exception.getTipoContenido());
        }
        return respuesta.body(exception.getCuerpo());
    }

    private static ResponseEntity<MensajeErrorResponse> error(Motivo motivo) {
        return ResponseEntity.status(estado(motivo)).body(new MensajeErrorResponse(motivo.getMensaje()));
    }

    private static HttpStatus estado(Motivo motivo) {
        switch (motivo) {
            case ALCANCE_INVALIDO:
            case PAGINA_INVALIDA:
            case CUERPO_INVALIDO:
            case VARIABLE_INVALIDA:
                return HttpStatus.BAD_REQUEST;
            case USUARIO_INVALIDO:
                return HttpStatus.UNAUTHORIZED;
            case NO_AUTORIZADA:
            case NO_ASIGNADA:
                return HttpStatus.FORBIDDEN;
            case TAREA_NO_ENCONTRADA:
            case INSTANCIA_NO_ENCONTRADA:
                return HttpStatus.NOT_FOUND;
            case FLOWABLE_NO_RESPONDE:
                return HttpStatus.BAD_GATEWAY;
            default:
                throw new IllegalArgumentException("Motivo sin estado HTTP: " + motivo);
        }
    }
}
