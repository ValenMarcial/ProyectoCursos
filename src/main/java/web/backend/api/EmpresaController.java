package web.backend.api;

import java.util.List;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import web.backend.api.dto.CursoEmpresaResponse;
import web.backend.api.dto.EmpresaSesionResponse;
import web.backend.api.dto.IniciarSesionRequest;
import web.backend.api.dto.MensajeErrorResponse;
import web.backend.service.EmpresaException;
import web.backend.service.EmpresaException.Motivo;
import web.backend.service.EmpresaService;
import web.backend.service.EmpresaSesion;

/**
 * API de las empresas (escenario 10): login, "Mis cursos" y detalle con actividades en curso.
 * Sin sesión de empresa no se llega acá (lo corta SesionInterceptor), salvo el login.
 */
@RestController
@RequestMapping(EmpresaController.RUTA)
public class EmpresaController {
    public static final String RUTA = "/api/empresa";
    public static final String RUTA_SESION = RUTA + "/sesion";
    /** Atributo de la sesión HTTP donde queda la empresa autenticada. */
    public static final String ATRIBUTO_EMPRESA = "empresaSesion";

    private final EmpresaService empresas;

    public EmpresaController(EmpresaService empresas) {
        this.empresas = empresas;
    }

    @PostMapping("/sesion")
    public EmpresaSesionResponse iniciar(@Valid @RequestBody IniciarSesionRequest request, HttpServletRequest http) {
        EmpresaSesion empresa = empresas.iniciar(request.getUsuario(), request.getClave());
        HttpSession sesion = http.getSession();
        http.changeSessionId();
        sesion.setAttribute(ATRIBUTO_EMPRESA, empresa);
        return respuesta(empresa);
    }

    @GetMapping("/sesion")
    public EmpresaSesionResponse actual(@SessionAttribute(ATRIBUTO_EMPRESA) EmpresaSesion empresa) {
        return respuesta(empresa);
    }

    @DeleteMapping("/sesion")
    public ResponseEntity<Void> cerrar(HttpSession sesion) {
        sesion.invalidate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cursos")
    public List<CursoEmpresaResponse> cursos(@SessionAttribute(ATRIBUTO_EMPRESA) EmpresaSesion empresa) {
        return empresas.cursos(empresa);
    }

    @GetMapping("/cursos/{id}")
    public CursoEmpresaResponse curso(@SessionAttribute(ATRIBUTO_EMPRESA) EmpresaSesion empresa,
            @PathVariable long id) {
        return empresas.curso(empresa, id);
    }

    @ExceptionHandler(EmpresaException.class)
    public ResponseEntity<MensajeErrorResponse> manejarError(EmpresaException exception) {
        return error(exception.getMotivo());
    }

    // El cuerpo del login (con la clave) nunca termina en un mensaje de error.
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<MensajeErrorResponse> manejarPedidoInvalido() {
        return error(Motivo.DATOS_INVALIDOS);
    }

    /** Un id que no es número se responde como curso inexistente. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<MensajeErrorResponse> manejarIdInvalido() {
        return error(Motivo.CURSO_NO_ENCONTRADO);
    }

    private static EmpresaSesionResponse respuesta(EmpresaSesion empresa) {
        return new EmpresaSesionResponse(empresa.getUsuario(), empresa.getEmpresa());
    }

    private static ResponseEntity<MensajeErrorResponse> error(Motivo motivo) {
        return ResponseEntity.status(estado(motivo)).body(new MensajeErrorResponse(motivo.getMensaje()));
    }

    private static HttpStatus estado(Motivo motivo) {
        switch (motivo) {
            case DATOS_INVALIDOS:
                return HttpStatus.BAD_REQUEST;
            case CREDENCIALES_INVALIDAS:
                return HttpStatus.UNAUTHORIZED;
            case CURSO_NO_ENCONTRADO:
                return HttpStatus.NOT_FOUND;
            case FLOWABLE_NO_RESPONDE:
                return HttpStatus.BAD_GATEWAY;
            default:
                throw new IllegalArgumentException("Motivo sin estado HTTP: " + motivo);
        }
    }
}
