package web.backend.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import web.backend.api.dto.PaginaResponse;
import web.backend.api.dto.TareaResponse;
import web.backend.service.TareaService;
import web.backend.service.UsuarioSesion;

/**
 * Tareas del proceso para el personal logueado. Solo traduce entre HTTP y TareaService: los
 * permisos y las reglas viven en el servicio y los errores los responde TareaErrorHandler.
 * Sin sesión no llega acá: lo corta SesionInterceptor.
 */
@RestController
@RequestMapping(TareaController.RUTA)
public class TareaController {
    /** Ruta del recurso. */
    public static final String RUTA = "/api/tareas";

    private final TareaService tareas;

    public TareaController(TareaService tareas) {
        this.tareas = tareas;
    }

    /**
     * GET /api/tareas?alcance=mias|grupo[&start&size]: tareas asignadas al usuario (mias) o sin
     * asignar de sus grupos (grupo). Devuelve {data, total, start, size}.
     * Los parámetros van opcionales a propósito: si faltan, el servicio responde con su propio
     * mensaje (alcance inválido o valores por defecto de la página).
     */
    @GetMapping
    public PaginaResponse<TareaResponse> listar(
            @SessionAttribute(SesionController.ATRIBUTO_USUARIO) UsuarioSesion usuario,
            @RequestParam(required = false) String alcance,
            @RequestParam(required = false) Integer start,
            @RequestParam(required = false) Integer size) {
        return tareas.listar(usuario, alcance, start, size);
    }

    /** GET /api/tareas/{id}: una tarea, si es del usuario o de uno de sus grupos. */
    @GetMapping("/{id}")
    public TareaResponse obtener(
            @SessionAttribute(SesionController.ATRIBUTO_USUARIO) UsuarioSesion usuario,
            @PathVariable String id) {
        return tareas.obtener(usuario, id);
    }
}
