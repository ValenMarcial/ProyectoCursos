package web.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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

import web.backend.api.dto.CompletarTareaRequest;
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
    private static final UsuarioSesion JEFE_DE_EQUIPO =
            new UsuarioSesion("jefeDeEquipo", "Jefe docente", List.of("Docentes"));
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

    @Test
    void reclamarUnaTareaDelGrupoUsaElUsuarioDeLaSesion() {
        when(flowable.obtenerTarea("7501")).thenReturn(Optional.of(tarea(null)));
        when(flowable.gruposCandidatos("7501")).thenReturn(List.of("Administracion"));

        service.reclamar(JEFE, "7501");

        verify(flowable).reclamarTarea("7501", "jefeAdministrativo");
    }

    /** Si la tiene un compañero del grupo se manda igual: el 409 lo da el motor. */
    @Test
    void reclamarUnaTareaTomadaPorUnCompaneroLaMandaAlMotor() {
        when(flowable.obtenerTarea("7501")).thenReturn(Optional.of(tarea("empleadoAdministrativo")));
        when(flowable.gruposCandidatos("7501")).thenReturn(List.of("Administracion"));
        FlowableRespuestaException conflicto = new FlowableRespuestaException(409, new byte[0], null, null);
        doThrow(conflicto).when(flowable).reclamarTarea("7501", "jefeAdministrativo");

        assertSame(conflicto, assertThrows(FlowableRespuestaException.class, () -> service.reclamar(JEFE, "7501")));
    }

    @Test
    void reclamarUnaTareaYaPropiaNoLlamaAlMotor() {
        when(flowable.obtenerTarea("7501")).thenReturn(Optional.of(tarea("jefeAdministrativo")));

        service.reclamar(JEFE, "7501");

        verify(flowable, never()).reclamarTarea(anyString(), anyString());
        verify(flowable, never()).gruposCandidatos(anyString());
    }

    @Test
    void reclamarUnaTareaAjenaDa403SinReclamar() {
        when(flowable.obtenerTarea("7501")).thenReturn(Optional.of(tarea(null)));
        when(flowable.gruposCandidatos("7501")).thenReturn(List.of("Docentes"));

        TareaException error = assertThrows(TareaException.class, () -> service.reclamar(JEFE, "7501"));

        assertEquals(Motivo.NO_AUTORIZADA, error.getMotivo());
        verify(flowable, never()).reclamarTarea(anyString(), anyString());
    }

    @Test
    void reclamarUnaTareaInexistenteDa404() {
        when(flowable.obtenerTarea("9999")).thenReturn(Optional.empty());

        TareaException error = assertThrows(TareaException.class, () -> service.reclamar(JEFE, "9999"));

        assertEquals(Motivo.TAREA_NO_ENCONTRADA, error.getMotivo());
    }

    /** Un claim con el usuario vacío liberaría la tarea: se corta antes de llamar a Flowable. */
    @Test
    void conElUsuarioVacioNoSeLlamaAFlowable() {
        UsuarioSesion vacio = new UsuarioSesion(" ", "", ADMINISTRACION);

        assertEquals(Motivo.USUARIO_INVALIDO,
                assertThrows(TareaException.class, () -> service.reclamar(vacio, "7501")).getMotivo());
        assertEquals(Motivo.USUARIO_INVALIDO,
                assertThrows(TareaException.class, () -> service.completar(vacio, "7501", null)).getMotivo());
        verifyNoInteractions(flowable);
    }

    @Test
    void completarUnaTareaPropiaMandaLasVariables() {
        List<CompletarTareaRequest.Variable> variables = List.of(
                new CompletarTareaRequest.Variable("aulaFisica", "boolean", true),
                new CompletarTareaRequest.Variable("presupuesto", "long", 1000),
                new CompletarTareaRequest.Variable("fechaInicioPosible", "date", "2027-03-01T00:00:00Z"),
                new CompletarTareaRequest.Variable("listaDefinitiva", "string", "Ana; Juan"));
        when(flowable.obtenerTarea("7501")).thenReturn(Optional.of(tarea("jefeAdministrativo")));

        service.completar(JEFE, "7501", variables);

        verify(flowable).completarTarea("7501", variables);
    }

    /**
     * "Definir Docente": el jefe de equipo manda el id de usuario del docente como texto. El
     * backend solo valida nombre y tipo; que sea del grupo Docentes lo valida el motor.
     */
    @Test
    void completarDefinirDocenteMandaElDocenteSeleccionado() {
        List<CompletarTareaRequest.Variable> variables =
                List.of(new CompletarTareaRequest.Variable("docenteSeleccionado", "string", "docente2"));
        when(flowable.obtenerTarea("7501")).thenReturn(Optional.of(tarea("jefeDeEquipo")));

        service.completar(JEFE_DE_EQUIPO, "7501", variables);

        verify(flowable).completarTarea("7501", variables);
    }

    @Test
    void completarSinVariablesMandaUnaListaVacia() {
        when(flowable.obtenerTarea("7501")).thenReturn(Optional.of(tarea("jefeAdministrativo")));

        service.completar(JEFE, "7501", null);

        verify(flowable).completarTarea("7501", List.of());
    }

    @Test
    void completarUnaTareaDelGrupoSinReclamarDa403ReclamalaPrimero() {
        when(flowable.obtenerTarea("7501")).thenReturn(Optional.of(tarea("empleadoAdministrativo")));
        when(flowable.gruposCandidatos("7501")).thenReturn(List.of("Administracion"));

        TareaException error = assertThrows(TareaException.class, () -> service.completar(JEFE, "7501", null));

        assertEquals(Motivo.NO_ASIGNADA, error.getMotivo());
        verify(flowable, never()).completarTarea(anyString(), any());
    }

    @Test
    void completarUnaTareaAjenaDa403() {
        when(flowable.obtenerTarea("7501")).thenReturn(Optional.of(tarea("docente1")));
        when(flowable.gruposCandidatos("7501")).thenReturn(List.of("Docentes"));

        TareaException error = assertThrows(TareaException.class, () -> service.completar(JEFE, "7501", null));

        assertEquals(Motivo.NO_AUTORIZADA, error.getMotivo());
    }

    /** Las variables se validan antes de consultar al motor, con el nombre en el mensaje. */
    @Test
    void lasVariablesFueraDeLaListaOConOtroTipoSeRechazan() {
        Object[][] invalidas = {
                {"resultado", "string", "finalizado"},        // la setea el motor
                {"aulaAsignada", "string", "Aula 1"},          // la setea ReserveClassroom
                {"docenteSeleccionado", "integer", 42},        // es el id de usuario: va como texto
                {"docenteSeleccionado", "string", 42},         // valor que no es texto
                {"docenteSeleccionado", "string", null},       // valor nulo
                {"presupuesto", "integer", 1000},              // tipo distinto del esperado
                {"presupuesto", "long", "mil"},                // valor que no es del tipo
                {"presupuesto", "long", 10.5},                 // long no acepta decimales
                {"aulaFisica", "boolean", "true"},             // booleano como texto
                {"fechaInicioPosible", "date", 20270301},      // fecha que no es texto
                {"programa", "string", null},                  // valor nulo
                {null, "string", "x"}};                        // sin nombre
        for (Object[] datos : invalidas) {
            List<CompletarTareaRequest.Variable> variables =
                    List.of(new CompletarTareaRequest.Variable((String) datos[0], (String) datos[1], datos[2]));

            TareaException error = assertThrows(TareaException.class,
                    () -> service.completar(JEFE, "7501", variables));

            assertEquals(Motivo.VARIABLE_INVALIDA, error.getMotivo());
            assertTrue(error.getMessage().endsWith(" " + datos[0]), error.getMessage());
        }
        verifyNoInteractions(flowable);
    }

    @Test
    void unaVariableRepetidaSeRechaza() {
        List<CompletarTareaRequest.Variable> variables = List.of(
                new CompletarTareaRequest.Variable("aulaFisica", "boolean", true),
                new CompletarTareaRequest.Variable("aulaFisica", "boolean", false));

        TareaException error = assertThrows(TareaException.class, () -> service.completar(JEFE, "7501", variables));

        assertEquals(Motivo.VARIABLE_INVALIDA, error.getMotivo());
        verifyNoInteractions(flowable);
    }

    private static TareaResponse tarea(String asignado) {
        return new TareaResponse("7501", "Evaluar Recursos", asignado, "sid-F3381B5B", "7420",
                "2026-11-11T13:02:11.000+0000", null);
    }

    private static PaginaResponse<TareaResponse> pagina(long total, TareaResponse... tareas) {
        return new PaginaResponse<>(List.of(tareas), total, 0, 50);
    }
}
