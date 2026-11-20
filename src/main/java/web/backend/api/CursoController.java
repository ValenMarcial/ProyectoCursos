package web.backend.api;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import web.backend.api.dto.CursoFinalizadoResponse;
import web.backend.service.CursoService;
import web.backend.service.TareaException;
import web.backend.service.TareaException.Motivo;
import web.backend.service.UsuarioSesion;

/** Cursos finalizados y sus informes, para Administración. Los errores los responde TareaErrorHandler. */
@RestController
@RequestMapping(CursoController.RUTA)
public class CursoController {
    public static final String RUTA = "/api/cursos";

    private final CursoService cursos;

    public CursoController(CursoService cursos) {
        this.cursos = cursos;
    }

    /** GET /api/cursos/finalizados: cursos finalizados de todas las empresas, del más reciente al más viejo. */
    @GetMapping("/finalizados")
    public List<CursoFinalizadoResponse> finalizados(
            @SessionAttribute(SesionController.ATRIBUTO_USUARIO) UsuarioSesion usuario) {
        return cursos.finalizados(usuario);
    }

    /** GET /api/cursos/{id}/informe: el PDF del informe de un curso finalizado. */
    @GetMapping("/{id}/informe")
    public ResponseEntity<Resource> informe(
            @SessionAttribute(SesionController.ATRIBUTO_USUARIO) UsuarioSesion usuario,
            @PathVariable String id) {
        return RespuestaPdf.informe(cursos.informe(usuario, idDeCurso(id)));
    }

    private static long idDeCurso(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw new TareaException(Motivo.INFORME_NO_ENCONTRADO);
        }
    }
}
