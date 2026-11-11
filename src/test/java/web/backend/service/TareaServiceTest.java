package web.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import web.backend.api.dto.PaginaResponse;
import web.backend.api.dto.TareaResponse;
import web.backend.api.dto.VariableResponse;
import web.backend.flowable.FlowableRespuestaException;
import web.backend.flowable.FlowableTareaClient;
import web.backend.service.TareaException.Motivo;

@ExtendWith(MockitoExtension.class)
class TareaServiceTest {
    private static final List<String> ADMINISTRACION = List.of("Administracion");
    private static final UsuarioSesion JEFE =
            new UsuarioSesion("jefeAdministrativo", "Jefe administrativo", ADMINISTRACION);
    private static final UsuarioSesion SIN_GRUPOS = new UsuarioSesion("matiasP", "Matías", List.of());

    @Mock
    private FlowableTareaClient flowable;

    private TareaService service;

    @BeforeEach
    void setUp() {
        service = new TareaService(flowable);
    }

    @Test
    void misTareasSeBuscanPorAsignadoConLaPaginaPorDefecto() {
        PaginaResponse<TareaResponse> pagina = pagina(1, tarea("jefeAdministrativo"));
        when(flowable.buscarTareas("jefeAdministrativo", null, null, 0, 50)).thenReturn(pagina);

        assertSame(pagina, service.listar(JEFE, "mias", null, null));
    }

    @Test
    void lasTareasDelGrupoSeBuscanPorLosGruposDeLaSesion() {
        PaginaResponse<TareaResponse> pagina = pagina(0);
        when(flowable.buscarTareas(null, ADMINISTRACION, null, 10, 100)).thenReturn(pagina);

        assertSame(pagina, service.listar(JEFE, "grupo", 10, 100));
    }

    @Test
    void sinGruposElAlcanceGrupoDaListaVaciaSinLlamarAFlowable() {
        PaginaResponse<TareaResponse> pagina = service.listar(SIN_GRUPOS, "grupo", null, null);

        assertTrue(pagina.getData().isEmpty());
        assertEquals(0, pagina.getTotal());
        assertEquals(0, pagina.getStart());
        assertEquals(50, pagina.getSize());
        verifyNoInteractions(flowable);
    }

    @Test
    void elAlcanceTieneQueSerMiasOGrupoEnMinuscula() {
        for (String alcance : new String[] {null, "", "MIAS", "Grupo", "todas"}) {
            TareaException error = assertThrows(TareaException.class,
                    () -> service.listar(JEFE, alcance, null, null));
            assertEquals(Motivo.ALCANCE_INVALIDO, error.getMotivo());
        }
        verifyNoInteractions(flowable);
    }

    @Test
    void laPaginaTieneLimites() {
        int[][] invalidas = {{-1, 50}, {0, 0}, {0, 101}};
        for (int[] pagina : invalidas) {
            TareaException error = assertThrows(TareaException.class,
                    () -> service.listar(JEFE, "mias", pagina[0], pagina[1]));
            assertEquals(Motivo.PAGINA_INVALIDA, error.getMotivo());
        }
        verifyNoInteractions(flowable);
    }

    @Test
    void sinRespuestaDeFlowableEsFlowableNoResponde() {
        when(flowable.buscarTareas(anyString(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new ResourceAccessException("Connection refused"));

        TareaException error = assertThrows(TareaException.class, () -> service.listar(JEFE, "mias", null, null));

        assertEquals(Motivo.FLOWABLE_NO_RESPONDE, error.getMotivo());
    }

    @Test
    void losErroresDelMotorSiguenDeLargo() {
        FlowableRespuestaException motor = new FlowableRespuestaException(500, new byte[0], null, null);
        when(flowable.buscarTareas(anyString(), any(), any(), anyInt(), anyInt())).thenThrow(motor);

        assertSame(motor, assertThrows(FlowableRespuestaException.class,
                () -> service.listar(JEFE, "mias", null, null)));
    }

    @Test
    void unaTareaPropiaSeVeSinConsultarLosCandidatos() {
        TareaResponse tarea = tarea("jefeAdministrativo");
        when(flowable.obtenerTarea("7501")).thenReturn(Optional.of(tarea));

        assertSame(tarea, service.obtener(JEFE, "7501"));
        verify(flowable, never()).gruposCandidatos(anyString());
    }

    @Test
    void unaTareaDeMiGrupoSeVeAunqueLaTengaUnCompanero() {
        TareaResponse tarea = tarea("empleadoAdministrativo");
        when(flowable.obtenerTarea("7501")).thenReturn(Optional.of(tarea));
        when(flowable.gruposCandidatos("7501")).thenReturn(List.of("Docentes", "Administracion"));

        assertSame(tarea, service.obtener(JEFE, "7501"));
    }

    @Test
    void unaTareaAjenaOInexistenteDaNoEncontrada() {
        when(flowable.obtenerTarea("7501")).thenReturn(Optional.of(tarea("docente1")));
        when(flowable.gruposCandidatos("7501")).thenReturn(List.of("Docentes"));
        when(flowable.obtenerTarea("9999")).thenReturn(Optional.empty());

        for (String id : new String[] {"7501", "9999"}) {
            TareaException error = assertThrows(TareaException.class, () -> service.obtener(JEFE, id));
            assertEquals(Motivo.TAREA_NO_ENCONTRADA, error.getMotivo());
        }
    }

    @Test
    void sinGruposUnaTareaAjenaNoConsultaLosCandidatos() {
        when(flowable.obtenerTarea("7501")).thenReturn(Optional.of(tarea(null)));

        assertThrows(TareaException.class, () -> service.obtener(SIN_GRUPOS, "7501"));
        verify(flowable, never()).gruposCandidatos(anyString());
    }

    @Test
    void lasVariablesSeFiltranPorLaListaCerrada() {
        when(flowable.buscarTareas("jefeAdministrativo", null, "7420", 0, 1)).thenReturn(pagina(1));
        when(flowable.variablesDeInstancia("7420")).thenReturn(List.of(
                new VariableResponse("tematica", "string", "BPMN"),
                new VariableResponse("solicitudId", "long", 7),
                new VariableResponse("aulaFisica", "boolean", true),
                new VariableResponse("cancelada", "boolean", false),
                new VariableResponse("motivoCierre", "string", "SIN_AULAS")));

        List<VariableResponse> variables = service.variables(JEFE, "7420");

        assertEquals(List.of("tematica", "aulaFisica", "motivoCierre"),
                variables.stream().map(VariableResponse::getName).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void lasVariablesSeVenConUnaTareaSinAsignarDelGrupo() {
        when(flowable.buscarTareas("jefeAdministrativo", null, "7420", 0, 1)).thenReturn(pagina(0));
        when(flowable.buscarTareas(null, ADMINISTRACION, "7420", 0, 1)).thenReturn(pagina(1));
        when(flowable.variablesDeInstancia("7420")).thenReturn(List.of());

        assertTrue(service.variables(JEFE, "7420").isEmpty());
    }

    @Test
    void sinTareasEnLaInstanciaDaNoEncontradaSinLeerVariables() {
        when(flowable.buscarTareas("jefeAdministrativo", null, "7420", 0, 1)).thenReturn(pagina(0));
        when(flowable.buscarTareas(null, ADMINISTRACION, "7420", 0, 1)).thenReturn(pagina(0));

        TareaException error = assertThrows(TareaException.class, () -> service.variables(JEFE, "7420"));

        assertEquals(Motivo.INSTANCIA_NO_ENCONTRADA, error.getMotivo());
        verify(flowable, never()).variablesDeInstancia(anyString());
    }

    @Test
    void sinGruposLasVariablesSoloMiranLasTareasAsignadas() {
        when(flowable.buscarTareas("matiasP", null, "7420", 0, 1)).thenReturn(pagina(0));

        assertThrows(TareaException.class, () -> service.variables(SIN_GRUPOS, "7420"));
        verify(flowable, never()).buscarTareas(any(), any(List.class), anyString(), anyInt(), anyInt());
    }

    private static TareaResponse tarea(String asignado) {
        return new TareaResponse("7501", "Evaluar Recursos", asignado, "sid-F3381B5B", "7420",
                "2026-11-11T13:02:11.000+0000", null);
    }

    private static PaginaResponse<TareaResponse> pagina(long total, TareaResponse... tareas) {
        return new PaginaResponse<>(List.of(tareas), total, 0, 50);
    }
}
