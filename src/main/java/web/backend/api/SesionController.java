package web.backend.api;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import web.backend.api.dto.IniciarSesionRequest;
import web.backend.api.dto.MensajeErrorResponse;
import web.backend.api.dto.SesionResponse;
import web.backend.service.SesionException;
import web.backend.service.SesionException.Motivo;
import web.backend.service.SesionService;
import web.backend.service.UsuarioSesion;

/**
 * Login, consulta y cierre de la sesión del personal.
 * Solo traduce entre HTTP y SesionService: las reglas viven en el servicio.
 */
@RestController
@RequestMapping(SesionController.RUTA)
public class SesionController {
    /** Ruta del recurso; la usan también el interceptor y su configuración. */
    public static final String RUTA = "/api/sesion";
    /** Atributo de la sesión HTTP donde queda el usuario autenticado. */
    public static final String ATRIBUTO_USUARIO = "usuarioSesion";

    private final SesionService sesiones;

    public SesionController(SesionService sesiones) {
        this.sesiones = sesiones;
    }

    /** Valida las credenciales, guarda el usuario en la sesión y lo devuelve. */
    @PostMapping
    public SesionResponse iniciar(
            @Valid @RequestBody IniciarSesionRequest request, HttpServletRequest httpRequest) {
        UsuarioSesion usuario = sesiones.iniciar(request.getUsuario(), request.getClave());
        HttpSession sesion = httpRequest.getSession();
        // Un id nuevo al autenticar evita que un id fijado antes del login quede con sesión (session fixation).
        httpRequest.changeSessionId();
        sesion.setAttribute(ATRIBUTO_USUARIO, usuario);
        return respuesta(usuario);
    }

    /** Devuelve el usuario actual. Sin sesión no llega acá: lo corta SesionInterceptor. */
    @GetMapping
    public SesionResponse actual(@SessionAttribute(ATRIBUTO_USUARIO) UsuarioSesion usuario) {
        return respuesta(usuario);
    }

    /** Cierra la sesión. */
    @DeleteMapping
    public ResponseEntity<Void> cerrar(HttpSession sesion) {
        sesion.invalidate();
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(SesionException.class)
    public ResponseEntity<MensajeErrorResponse> manejarError(SesionException exception) {
        return error(exception.getMotivo());
    }

    // Se atrapan acá para responder {"message"} en vez del error estándar de Spring,
    // y para que el cuerpo del login (con la clave) nunca termine en un mensaje de error.
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<MensajeErrorResponse> manejarPedidoInvalido() {
        return error(Motivo.DATOS_INVALIDOS);
    }

    private static SesionResponse respuesta(UsuarioSesion usuario) {
        return new SesionResponse(usuario.getUsuario(), usuario.getNombre(), usuario.getGrupos());
    }

    private static ResponseEntity<MensajeErrorResponse> error(Motivo motivo) {
        return ResponseEntity.status(estado(motivo)).body(new MensajeErrorResponse(motivo.getMensaje()));
    }

    private static HttpStatus estado(Motivo motivo) {
        switch (motivo) {
            case DATOS_INVALIDOS:
                return HttpStatus.BAD_REQUEST;
            case CREDENCIALES_INVALIDAS:
            case SIN_SESION:
                return HttpStatus.UNAUTHORIZED;
            case FLOWABLE_NO_RESPONDE:
                return HttpStatus.BAD_GATEWAY;
            default:
                throw new IllegalArgumentException("Motivo sin estado HTTP: " + motivo);
        }
    }
}
