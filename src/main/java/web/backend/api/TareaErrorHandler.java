package web.backend.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import web.backend.api.dto.MensajeErrorResponse;
import web.backend.flowable.FlowableRespuestaException;
import web.backend.service.TareaException;
import web.backend.service.TareaException.Motivo;

/**
 * Respuestas de error de los endpoints de tareas, instancias y cursos (limitado a esos controllers).
 * - Errores del backend: {"message": "..."} con el estado de cada motivo.
 * - Errores que respondió el motor: estado, cuerpo y content-type exactos, sin tocarlos.
 */
@RestControllerAdvice(assignableTypes = {TareaController.class, InstanciaController.class, CursoController.class})
public class TareaErrorHandler {

    /** Error de las reglas del backend: devuelve {"message"} con el estado del motivo. */
    @ExceptionHandler(TareaException.class)
    public ResponseEntity<MensajeErrorResponse> manejarError(TareaException exception) {
        return ResponseEntity.status(estado(exception.getMotivo()))
                .body(new MensajeErrorResponse(exception.getMessage()));
    }

    /** Cuerpo que no es JSON o no tiene la forma esperada: 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MensajeErrorResponse> manejarCuerpoInvalido() {
        return error(Motivo.CUERPO_INVALIDO);
    }

    /** start o size que no son números: página inválida. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<MensajeErrorResponse> manejarParametroInvalido() {
        return error(Motivo.PAGINA_INVALIDA);
    }

    /** Pedido sin el campo "archivo": 400 con el mensaje del informe. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<MensajeErrorResponse> manejarArchivoFaltante() {
        return error(Motivo.INFORME_INVALIDO);
    }

    /** Archivo que pasa el límite de Spring: 413. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<MensajeErrorResponse> manejarArchivoMuyGrande() {
        return error(Motivo.INFORME_MUY_GRANDE);
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
            case TAREA_SIN_INFORME:
            case INFORME_INVALIDO:
                return HttpStatus.BAD_REQUEST;
            case INFORME_MUY_GRANDE:
                return HttpStatus.PAYLOAD_TOO_LARGE;
            case INFORME_NO_GUARDADO:
                return HttpStatus.INTERNAL_SERVER_ERROR;
            case USUARIO_INVALIDO:
                return HttpStatus.UNAUTHORIZED;
            case NO_AUTORIZADA:
            case NO_ASIGNADA:
            case SOLO_ADMINISTRACION:
                return HttpStatus.FORBIDDEN;
            case TAREA_NO_ENCONTRADA:
            case INSTANCIA_NO_ENCONTRADA:
            case INFORME_NO_ENCONTRADO:
                return HttpStatus.NOT_FOUND;
            case FLOWABLE_NO_RESPONDE:
                return HttpStatus.BAD_GATEWAY;
            default:
                throw new IllegalArgumentException("Motivo sin estado HTTP: " + motivo);
        }
    }
}
