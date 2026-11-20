package web.backend.service;

import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Service;

import web.backend.api.dto.CursoFinalizadoResponse;
import web.backend.persistence.CursoRepository;
import web.backend.persistence.CursoRepository.CursoConInforme;
import web.backend.service.TareaException.Motivo;

/** Informes de los cursos finalizados, solo para el grupo Administracion. */
@Service
public class CursoService {
    /** Grupo de Flowable que puede consultar los informes. */
    static final String GRUPO_ADMINISTRACION = "Administracion";

    private final CursoRepository cursos;
    private final InformeService informes;

    public CursoService(CursoRepository cursos, InformeService informes) {
        this.cursos = cursos;
        this.informes = informes;
    }

    public List<CursoFinalizadoResponse> finalizados(UsuarioSesion usuario) {
        exigirAdministracion(usuario);
        return cursos.finalizados();
    }

    /** El PDF del informe de un curso finalizado. */
    public Path informe(UsuarioSesion usuario, long cursoId) {
        exigirAdministracion(usuario);
        CursoConInforme curso = cursos.finalizado(cursoId)
                .orElseThrow(() -> new TareaException(Motivo.INFORME_NO_ENCONTRADO));
        return informes.archivo(curso.instanciaId, curso.rutaInforme)
                .orElseThrow(() -> new TareaException(Motivo.INFORME_NO_ENCONTRADO));
    }

    private static void exigirAdministracion(UsuarioSesion usuario) {
        if (usuario == null || !usuario.getGrupos().contains(GRUPO_ADMINISTRACION)) {
            throw new TareaException(Motivo.SOLO_ADMINISTRACION);
        }
    }
}
