package web.backend.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import web.backend.api.dto.VariableResponse;
import web.backend.service.TareaService;
import web.backend.service.UsuarioSesion;

/**
 * Datos de una instancia del proceso para el personal logueado. Usa TareaService porque el
 * permiso depende de las tareas: solo se ve una instancia en la que el usuario tiene una tarea
 * activa. Los errores los responde TareaErrorHandler.
 */
@RestController
@RequestMapping(InstanciaController.RUTA)
public class InstanciaController {
    /** Ruta del recurso. */
    public static final String RUTA = "/api/instancias";

    private final TareaService tareas;

    public InstanciaController(TareaService tareas) {
        this.tareas = tareas;
    }

    /** GET /api/instancias/{id}/variables: las variables permitidas, como [{name, type, value}]. */
    @GetMapping("/{id}/variables")
    public List<VariableResponse> variables(
            @SessionAttribute(SesionController.ATRIBUTO_USUARIO) UsuarioSesion usuario,
            @PathVariable String id) {
        return tareas.variables(usuario, id);
    }
}
