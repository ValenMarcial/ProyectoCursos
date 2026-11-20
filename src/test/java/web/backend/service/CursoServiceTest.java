package web.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import web.backend.api.dto.CursoFinalizadoResponse;
import web.backend.persistence.CursoRepository;
import web.backend.persistence.CursoRepository.CursoConInforme;
import web.backend.service.TareaException.Motivo;

class CursoServiceTest {
    private static final UsuarioSesion EMPLEADO =
            new UsuarioSesion("empleadoAdministrativo", "Empleado", List.of("Administracion"));
    private static final UsuarioSesion DOCENTE = new UsuarioSesion("docente1", "Docente", List.of("Docentes"));
    private static final CursoFinalizadoResponse CURSO = new CursoFinalizadoResponse(10L, "BPMN", "Tech S.A.",
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1), true);

    private CursoRepository repository;
    private InformeService informes;
    private CursoService service;

    @BeforeEach
    void setUp() {
        repository = mock(CursoRepository.class);
        informes = mock(InformeService.class);
        service = new CursoService(repository, informes);
    }

    @Test
    void administracionVeLosCursosFinalizados() {
        when(repository.finalizados()).thenReturn(List.of(CURSO));

        assertEquals(List.of(CURSO), service.finalizados(EMPLEADO));
    }

    @Test
    void otroGrupoNoVeLosCursosNiLosInformes() {
        assertEquals(Motivo.SOLO_ADMINISTRACION,
                assertThrows(TareaException.class, () -> service.finalizados(DOCENTE)).getMotivo());
        assertEquals(Motivo.SOLO_ADMINISTRACION,
                assertThrows(TareaException.class, () -> service.informe(DOCENTE, 10L)).getMotivo());
        verifyNoInteractions(repository, informes);
    }

    @Test
    void elInformeDeUnCursoFinalizadoSeEntrega() {
        Path pdf = Path.of("cursos/proc-10/a.pdf");
        when(repository.finalizado(10L))
                .thenReturn(Optional.of(new CursoConInforme(CURSO, "proc-10", "cursos/proc-10/a.pdf")));
        when(informes.archivo("proc-10", "cursos/proc-10/a.pdf")).thenReturn(Optional.of(pdf));

        assertEquals(pdf, service.informe(EMPLEADO, 10L));
    }

    @Test
    void unCursoNoFinalizadoOSinArchivoDa404() {
        when(repository.finalizado(12L)).thenReturn(Optional.empty());
        when(repository.finalizado(10L))
                .thenReturn(Optional.of(new CursoConInforme(CURSO, "proc-10", "cursos/proc-10/a.pdf")));
        when(informes.archivo(anyString(), anyString())).thenReturn(Optional.empty());

        assertEquals(Motivo.INFORME_NO_ENCONTRADO,
                assertThrows(TareaException.class, () -> service.informe(EMPLEADO, 12L)).getMotivo());
        assertEquals(Motivo.INFORME_NO_ENCONTRADO,
                assertThrows(TareaException.class, () -> service.informe(EMPLEADO, 10L)).getMotivo());
    }
}
