package web.backend.flowable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import web.backend.api.dto.ActividadResponse;
import web.backend.api.dto.CompletarTareaRequest;
import web.backend.api.dto.PaginaResponse;
import web.backend.api.dto.TareaResponse;
import web.backend.api.dto.VariableResponse;

class FlowableTareaClientTest {
    private static final String BASE_URL = "http://flowable/flowable-ui/process-api";
    private static final String TAREAS = BASE_URL + "/runtime/tasks";
    /** Filtro y orden que llevan todas las búsquedas de tareas. */
    private static final String PROCESO = "?processDefinitionKey=process-proyecto-curso";
    private static final String ORDEN = "&sort=createTime&order=asc";
    private static final String TAREA_JSON = "{\"id\":\"7501\",\"name\":\"Evaluar Recursos\",\"assignee\":null,"
            + "\"taskDefinitionKey\":\"sid-F3381B5B\",\"processInstanceId\":\"7420\","
            + "\"processDefinitionId\":\"process-proyecto-curso:5:abc\","
            + "\"createTime\":\"2026-11-11T13:02:11.000+0000\",\"dueDate\":null,\"priority\":50}";
    private static final String ERROR_MOTOR =
            "{\"message\":\"Bad request\",\"exception\":\"Debe seleccionar al menos un recurso.\"}";

    private MockRestServiceServer server;
    private FlowableTareaClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        // La barra final se quita, igual que en FlowableRestClient.
        client = new FlowableTareaClient(restTemplate, BASE_URL + "/");
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void buscaLasTareasAsignadasConProcesoOrdenYPagina() {
        server.expect(requestTo(URI.create(TAREAS + PROCESO + "&assignee=jefeAdministrativo" + ORDEN
                        + "&start=0&size=50")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":[" + TAREA_JSON + "],\"total\":1,\"start\":0,\"size\":1}",
                        MediaType.APPLICATION_JSON));

        PaginaResponse<TareaResponse> pagina = client.buscarTareas("jefeAdministrativo", null, null, 0, 50);

        assertEquals(1, pagina.getTotal());
        assertEquals(0, pagina.getStart());
        // size es el pedido, no la cantidad que vino en esta página.
        assertEquals(50, pagina.getSize());
        TareaResponse tarea = pagina.getData().get(0);
        assertEquals("7501", tarea.getId());
        assertEquals("Evaluar Recursos", tarea.getName());
        assertNull(tarea.getAssignee());
        assertEquals("sid-F3381B5B", tarea.getTaskDefinitionKey());
        assertEquals("7420", tarea.getProcessInstanceId());
        assertEquals("2026-11-11T13:02:11.000+0000", tarea.getCreateTime());
        assertNull(tarea.getDueDate());
    }

    @Test
    void buscaLasTareasDelGrupoSinAsignarConLosGruposUnidosPorComa() {
        server.expect(requestTo(URI.create(TAREAS + PROCESO + "&candidateGroups=Administracion%2CDocentes"
                        + "&unassigned=true" + ORDEN + "&start=20&size=10")))
                .andRespond(withSuccess("{\"data\":[],\"total\":0}", MediaType.APPLICATION_JSON));

        PaginaResponse<TareaResponse> pagina =
                client.buscarTareas(null, List.of("Administracion", "Docentes"), null, 20, 10);

        assertEquals(0, pagina.getTotal());
        assertTrue(pagina.getData().isEmpty());
    }

    @Test
    void filtraPorInstanciaYCodificaLosValores() {
        server.expect(requestTo(URI.create(TAREAS + PROCESO + "&assignee=ana%26size%3D1000"
                        + "&processInstanceId=7420%2F1" + ORDEN + "&start=0&size=1")))
                .andRespond(withSuccess("{\"data\":[],\"total\":0}", MediaType.APPLICATION_JSON));

        client.buscarTareas("ana&size=1000", null, "7420/1", 0, 1);
    }

    @Test
    void sinUsuarioNiGruposNoConsultaAFlowable() {
        assertThrows(IllegalArgumentException.class, () -> client.buscarTareas(null, null, null, 0, 50));
        assertThrows(IllegalArgumentException.class, () -> client.buscarTareas(" ", List.of(), null, 0, 50));
        assertThrows(IllegalArgumentException.class,
                () -> client.buscarTareas("ana", List.of("Administracion"), null, 0, 50));
    }

    @Test
    void obtieneUnaTareaDelProcesoConElIdCodificado() {
        server.expect(requestTo(URI.create(TAREAS + "/75%2F01")))
                .andRespond(withSuccess(TAREA_JSON, MediaType.APPLICATION_JSON));

        Optional<TareaResponse> tarea = client.obtenerTarea("75/01");

        assertTrue(tarea.isPresent());
        assertEquals("7501", tarea.get().getId());
    }

    @Test
    void unaTareaInexistenteODeOtroProcesoVuelveVacia() {
        server.expect(requestTo(URI.create(TAREAS + "/1")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Not found\",\"exception\":\"Could not find a task with id '1'.\"}"));
        server.expect(requestTo(URI.create(TAREAS + "/2")))
                .andRespond(withSuccess(TAREA_JSON.replace("process-proyecto-curso:5", "otro-proceso:1"),
                        MediaType.APPLICATION_JSON));

        assertFalse(client.obtenerTarea("1").isPresent());
        assertFalse(client.obtenerTarea("2").isPresent());
    }

    @Test
    void devuelveSoloLosGruposCandidatos() {
        server.expect(requestTo(URI.create(TAREAS + "/7501/identitylinks")))
                .andRespond(withSuccess("["
                        + "{\"user\":null,\"group\":\"Administracion\",\"type\":\"candidate\"},"
                        + "{\"user\":\"docente1\",\"group\":null,\"type\":\"candidate\"},"
                        + "{\"user\":\"jefeAdministrativo\",\"group\":null,\"type\":\"assignee\"},"
                        + "{\"user\":null,\"group\":\"Docentes\",\"type\":\"participant\"}]",
                        MediaType.APPLICATION_JSON));

        assertEquals(List.of("Administracion"), client.gruposCandidatos("7501"));
    }

    @Test
    void devuelveLasVariablesSinElScope() {
        server.expect(requestTo(URI.create(BASE_URL + "/runtime/process-instances/7420/variables")))
                .andRespond(withSuccess("["
                        + "{\"name\":\"tematica\",\"type\":\"string\",\"value\":\"BPMN\",\"scope\":\"global\"},"
                        + "{\"name\":\"aulaFisica\",\"type\":\"boolean\",\"value\":true,\"scope\":\"global\"}]",
                        MediaType.APPLICATION_JSON));

        List<VariableResponse> variables = client.variablesDeInstancia("7420");

        assertEquals(2, variables.size());
        assertEquals("tematica", variables.get(0).getName());
        assertEquals("string", variables.get(0).getType());
        assertEquals("BPMN", variables.get(0).getValue());
        assertEquals(true, variables.get(1).getValue());
    }

    @Test
    void losErroresDelMotorConservanEstadoCuerpoYTipo() {
        for (HttpStatus estado : new HttpStatus[] {
                HttpStatus.BAD_REQUEST, HttpStatus.CONFLICT, HttpStatus.INTERNAL_SERVER_ERROR}) {
            server.reset();
            server.expect(requestTo(URI.create(BASE_URL + "/runtime/process-instances/7420/variables")))
                    .andRespond(withStatus(estado).contentType(MediaType.APPLICATION_JSON).body(ERROR_MOTOR));

            FlowableRespuestaException error = assertThrows(FlowableRespuestaException.class,
                    () -> client.variablesDeInstancia("7420"));

            assertEquals(estado.value(), error.getEstado());
            assertArrayEquals(ERROR_MOTOR.getBytes(StandardCharsets.UTF_8), error.getCuerpo());
            assertEquals(MediaType.APPLICATION_JSON, error.getTipoContenido());
        }
    }

    @Test
    void reclamaConElUsuarioComoAssignee() {
        server.expect(requestTo(URI.create(TAREAS + "/75%2F01")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"action\":\"claim\",\"assignee\":\"empleadoAdministrativo\"}", true))
                .andRespond(withSuccess());

        client.reclamarTarea("75/01", "empleadoAdministrativo");
    }

    /** Un claim sin assignee libera la tarea en Flowable: nunca se manda. */
    @Test
    void sinUsuarioNoReclama() {
        for (String usuario : new String[] {null, "", "  "}) {
            assertThrows(IllegalArgumentException.class, () -> client.reclamarTarea("7501", usuario));
        }
    }

    @Test
    void elConflictoDeReclamarConservaElCuerpoDelMotor() {
        String conflicto = "{\"message\":\"Task is already claimed\",\"exception\":\"Task '7501' is already claimed "
                + "by someone else.\"}";
        server.expect(requestTo(URI.create(TAREAS + "/7501")))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(conflicto));

        FlowableRespuestaException error = assertThrows(FlowableRespuestaException.class,
                () -> client.reclamarTarea("7501", "jefeAdministrativo"));

        assertEquals(409, error.getEstado());
        assertArrayEquals(conflicto.getBytes(StandardCharsets.UTF_8), error.getCuerpo());
    }

    @Test
    void completaConLasVariablesComoNombreTipoYValor() {
        server.expect(requestTo(URI.create(TAREAS + "/7501")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"action\":\"complete\",\"variables\":["
                        + "{\"name\":\"aulaFisica\",\"type\":\"boolean\",\"value\":true},"
                        + "{\"name\":\"presupuesto\",\"type\":\"long\",\"value\":1000}]}", true))
                .andRespond(withSuccess());

        client.completarTarea("7501", List.of(
                new CompletarTareaRequest.Variable("aulaFisica", "boolean", true),
                new CompletarTareaRequest.Variable("presupuesto", "long", 1000)));
    }

    @Test
    void completarSinVariablesMandaUnaListaVacia() {
        server.expect(requestTo(URI.create(TAREAS + "/7501")))
                .andExpect(content().json("{\"action\":\"complete\",\"variables\":[]}", true))
                .andRespond(withSuccess());

        client.completarTarea("7501", List.of());
    }

    /** Un 401/403 del motor es la cuenta del backend: no se reenvía, sale como falta de respuesta. */
    @Test
    void unRechazoDeLaCuentaDelBackendNoSeReenviaALaWeb() {
        for (HttpStatus estado : new HttpStatus[] {HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN}) {
            server.reset();
            server.expect(requestTo(URI.create(TAREAS + "/7501/identitylinks")))
                    .andRespond(withStatus(estado));

            RestClientException error = assertThrows(RestClientException.class,
                    () -> client.gruposCandidatos("7501"));

            assertFalse(error instanceof org.springframework.web.client.HttpStatusCodeException);
        }
    }

    @Test
    void actividadesEnCursoTraeSoloTareasYEsperasDeLaInstancia() {
        server.expect(requestTo(URI.create(BASE_URL + "/history/historic-activity-instances?processInstanceId=7420"
                        + "&finished=false&sort=startTime&order=asc&size=100")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":["
                        + "{\"activityName\":\"Preparar curso\",\"activityType\":\"subProcess\",\"startTime\":\"t0\"},"
                        + "{\"activityName\":\"Prepara materiales\",\"activityType\":\"userTask\",\"startTime\":\"t1\"},"
                        + "{\"activityName\":\"Recibir nómina\",\"activityType\":\"receiveTask\",\"startTime\":\"t1\"},"
                        + "{\"activityName\":null,\"activityType\":\"inclusiveGateway\",\"startTime\":\"t2\"}"
                        + "],\"total\":4}", MediaType.APPLICATION_JSON));

        List<ActividadResponse> actividades = client.actividadesEnCurso("7420");

        assertEquals(2, actividades.size());
        assertEquals("Prepara materiales", actividades.get(0).nombre);
        assertEquals("receiveTask", actividades.get(1).tipo);
        assertEquals("t1", actividades.get(1).desde);
    }
}
