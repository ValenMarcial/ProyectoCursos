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
import org.springframework.web.client.RestClientException;

import shared.PasswordHash;
import web.backend.api.dto.ActividadResponse;
import web.backend.api.dto.CursoEmpresaResponse;
import web.backend.flowable.FlowableTareaClient;
import web.backend.persistence.EmpresaRepository;
import web.backend.persistence.EmpresaRepository.Cuenta;
import web.backend.persistence.EmpresaRepository.CursoConInstancia;
import web.backend.service.EmpresaException.Motivo;

class EmpresaServiceTest {
    private static final EmpresaSesion TECH = new EmpresaSesion(1L, "rrhh@tech.com", "Tech S.A.");
    private static final String HASH = PasswordHash.hash("clave-correcta");

    private EmpresaRepository repository;
    private FlowableTareaClient flowable;
    private InformeService informes;
    private EmpresaService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmpresaRepository.class);
        flowable = mock(FlowableTareaClient.class);
        informes = mock(InformeService.class);
        service = new EmpresaService(repository, flowable, informes);
        when(repository.buscarCuenta(anyString())).thenReturn(Optional.empty());
        when(repository.buscarCuenta("rrhh@tech.com"))
                .thenReturn(Optional.of(new Cuenta(1L, "rrhh@tech.com", HASH, "Tech S.A.")));
    }

    @Test
    void loginCorrectoSinImportarMayusculasNiEspaciosEnElUsuario() {
        EmpresaSesion sesion = service.iniciar("  RRHH@Tech.com ", "clave-correcta");

        assertEquals(1L, sesion.getEmpresaId());
        assertEquals("Tech S.A.", sesion.getEmpresa());
    }

    @Test
    void claveIncorrectaYUsuarioInexistenteDanElMismoError() {
        EmpresaException clave = assertThrows(EmpresaException.class,
                () -> service.iniciar("rrhh@tech.com", "otra"));
        EmpresaException usuario = assertThrows(EmpresaException.class,
                () -> service.iniciar("nadie@tech.com", "clave-correcta"));

        assertEquals(Motivo.CREDENCIALES_INVALIDAS, clave.getMotivo());
        assertEquals(Motivo.CREDENCIALES_INVALIDAS, usuario.getMotivo());
    }

    @Test
    void elDetalleTraeLasActividadesEnCurso() {
        when(repository.curso(1L, 10L)).thenReturn(Optional.of(curso("en_preparacion", "proc-10")));
        List<ActividadResponse> actividades = List.of(
                new ActividadResponse("Prepara materiales", "userTask", "2026-11-11T10:00:00.000+0000"),
                new ActividadResponse("Recibir nómina", "receiveTask", "2026-11-11T10:00:00.000+0000"));
        when(flowable.actividadesEnCurso("proc-10")).thenReturn(actividades);

        assertEquals(actividades, service.curso(TECH, 10L).actividades);
    }

    @Test
    void unCursoTerminadoNoConsultaAFlowable() {
        when(repository.curso(1L, 11L)).thenReturn(Optional.of(curso("finalizado", "proc-11")));

        assertEquals(List.of(), service.curso(TECH, 11L).actividades);
        verifyNoInteractions(flowable);
    }

    @Test
    void unCursoAjenoOInexistenteNoSeEncuentra() {
        when(repository.curso(1L, 20L)).thenReturn(Optional.empty());

        EmpresaException error = assertThrows(EmpresaException.class, () -> service.curso(TECH, 20L));
        assertEquals(Motivo.CURSO_NO_ENCONTRADO, error.getMotivo());
    }

    @Test
    void siFlowableNoRespondeAvisa() {
        when(repository.curso(1L, 10L)).thenReturn(Optional.of(curso("confirmado", "proc-10")));
        when(flowable.actividadesEnCurso("proc-10")).thenThrow(new RestClientException("caído"));

        EmpresaException error = assertThrows(EmpresaException.class, () -> service.curso(TECH, 10L));
        assertEquals(Motivo.FLOWABLE_NO_RESPONDE, error.getMotivo());
    }

    @Test
    void elInformeDeUnCursoFinalizadoSeEntrega() {
        Path pdf = Path.of("cursos/proc-11/b.pdf");
        when(repository.curso(1L, 11L)).thenReturn(Optional.of(cursoConInforme("finalizado")));
        when(informes.archivo("proc-11", "cursos/proc-11/b.pdf")).thenReturn(Optional.of(pdf));

        assertEquals(pdf, service.informe(TECH, 11L));
    }

    @Test
    void antesDeQueElAdministrativoLoEnvieElInformeNoEstaDisponible() {
        when(repository.curso(1L, 11L)).thenReturn(Optional.of(cursoConInforme("en_elaboracion_informe")));

        EmpresaException error = assertThrows(EmpresaException.class, () -> service.informe(TECH, 11L));
        assertEquals(Motivo.INFORME_NO_DISPONIBLE, error.getMotivo());
        verifyNoInteractions(informes);
    }

    @Test
    void sinArchivoEnElServidorElInformeNoEstaDisponible() {
        when(repository.curso(1L, 11L)).thenReturn(Optional.of(cursoConInforme("finalizado")));
        when(informes.archivo(anyString(), anyString())).thenReturn(Optional.empty());

        EmpresaException error = assertThrows(EmpresaException.class, () -> service.informe(TECH, 11L));
        assertEquals(Motivo.INFORME_NO_DISPONIBLE, error.getMotivo());
    }

    @Test
    void elInformeDeUnCursoAjenoNoSeEncuentra() {
        when(repository.curso(1L, 20L)).thenReturn(Optional.empty());

        EmpresaException error = assertThrows(EmpresaException.class, () -> service.informe(TECH, 20L));
        assertEquals(Motivo.CURSO_NO_ENCONTRADO, error.getMotivo());
    }

    private static CursoConInstancia cursoConInforme(String estado) {
        return new CursoConInstancia(new CursoEmpresaResponse(11L, "Cloud", "AWS", estado,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1), null), "proc-11", "cursos/proc-11/b.pdf");
    }

    private static CursoConInstancia curso(String estado, String instancia) {
        return new CursoConInstancia(new CursoEmpresaResponse(10L, "BPMN", "Modelado", estado,
                LocalDate.of(2027, 1, 15), LocalDate.of(2027, 2, 15), null), instancia);
    }
}
