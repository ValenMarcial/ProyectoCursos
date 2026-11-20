package web.backend.api;

import java.io.IOException;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartFile;

import web.backend.api.dto.CompletarTareaRequest;
import web.backend.api.dto.PaginaResponse;
import web.backend.api.dto.RutaInformeResponse;
import web.backend.api.dto.TareaResponse;
import web.backend.service.InformeService;
import web.backend.service.TareaException;
import web.backend.service.TareaException.Motivo;
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
    private final InformeService informes;

    public TareaController(TareaService tareas, InformeService informes) {
        this.tareas = tareas;
        this.informes = informes;
    }

    /** GET /api/tareas?alcance=mias|grupo[&start&size]: tareas del usuario o de sus grupos. */
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

    /** POST /api/tareas/{id}/reclamar: asigna la tarea al usuario de la sesión. */
    @PostMapping("/{id}/reclamar")
    public ResponseEntity<Void> reclamar(
            @SessionAttribute(SesionController.ATRIBUTO_USUARIO) UsuarioSesion usuario,
            @PathVariable String id) {
        tareas.reclamar(usuario, id);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/tareas/{id}/completar: completa la tarea con las variables recibidas. */
    @PostMapping("/{id}/completar")
    public ResponseEntity<Void> completar(
            @SessionAttribute(SesionController.ATRIBUTO_USUARIO) UsuarioSesion usuario,
            @PathVariable String id,
            @RequestBody(required = false) CompletarTareaRequest request) {
        tareas.completar(usuario, id, request == null ? null : request.getVariables());
        return ResponseEntity.noContent().build();
    }

    /** POST /api/tareas/{id}/informe: guarda el PDF del informe y devuelve su ruta. */
    @PostMapping(path = "/{id}/informe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RutaInformeResponse subirInforme(
            @SessionAttribute(SesionController.ATRIBUTO_USUARIO) UsuarioSesion usuario,
            @PathVariable String id,
            @RequestParam("archivo") MultipartFile archivo) {
        try {
            return new RutaInformeResponse(informes.guardar(usuario, id, archivo.getBytes()));
        } catch (IOException exception) {
            throw new TareaException(Motivo.INFORME_NO_GUARDADO, exception);
        }
    }

    /** GET /api/tareas/{id}/informe: el PDF del informe de la instancia de la tarea. */
    @GetMapping("/{id}/informe")
    public ResponseEntity<Resource> verInforme(
            @SessionAttribute(SesionController.ATRIBUTO_USUARIO) UsuarioSesion usuario,
            @PathVariable String id) {
        return RespuestaPdf.informe(informes.ubicar(usuario, id));
    }
}
