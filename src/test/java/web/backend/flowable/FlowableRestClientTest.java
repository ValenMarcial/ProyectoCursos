package web.backend.flowable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import web.backend.api.dto.IniciarSolicitudRequest;

class FlowableRestClientTest {
    private static final String BASE_URL = "http://flowable/flowable-ui/process-api";

    private MockRestServiceServer server;
    private FlowableRestClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new FlowableRestClient(restTemplate, BASE_URL);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void iniciaProcesoConElEventoMsgSolicitudYVariablesDeLaWeb() {
        server.expect(requestTo(BASE_URL + "/runtime/process-instances"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{"
                        + "\"message\":\"msgSolicitud\","
                        + "\"businessKey\":\"solicitud-demo\","
                        + "\"variables\":["
                        + "{\"name\":\"solicitudId\",\"value\":7,\"type\":\"long\"},"
                        + "{\"name\":\"empresaNombre\",\"value\":\"Empresa Ejemplo\",\"type\":\"string\"},"
                        + "{\"name\":\"empresaId\",\"value\":42,\"type\":\"long\"},"
                + "{\"name\":\"tematica\",\"value\":\"BPMN\",\"type\":\"string\"},"
                + "{\"name\":\"descripcion\",\"value\":\"Curso de prueba\",\"type\":\"string\"},"
                + "{\"name\":\"contactoNombre\",\"value\":\"Ana Pérez\",\"type\":\"string\"},"
                + "{\"name\":\"contactoEmail\",\"value\":\"ana@example.com\",\"type\":\"string\"},"
                + "{\"name\":\"participantesEstimados\",\"value\":12,\"type\":\"integer\"},"
                + "{\"name\":\"fechasPreferidas\",\"value\":\"Marzo 2027\",\"type\":\"string\"},"
                + "{\"name\":\"contactoTelefono\",\"value\":\"555-0101\",\"type\":\"string\"}"
                        + "]}"))
                .andRespond(withSuccess("{\"id\":\"process-1\"}", MediaType.APPLICATION_JSON));

        IniciarSolicitudRequest request = solicitudRequest();

        assertEquals("process-1", client.iniciarSolicitud("solicitud-demo", 7L, 42L, request));
    }

    @Test
    void incluyeElPlazoOpcionalParaPruebas() {
        server.expect(requestTo(BASE_URL + "/runtime/process-instances"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{"
                        + "\"message\":\"msgSolicitud\","
                        + "\"businessKey\":\"solicitud-prueba\","
                        + "\"variables\":["
                        + "{\"name\":\"solicitudId\",\"value\":7,\"type\":\"long\"},"
                        + "{\"name\":\"empresaNombre\",\"value\":\"Empresa Ejemplo\",\"type\":\"string\"},"
                        + "{\"name\":\"empresaId\",\"value\":42,\"type\":\"long\"},"
                        + "{\"name\":\"tematica\",\"value\":\"BPMN\",\"type\":\"string\"},"
                        + "{\"name\":\"descripcion\",\"value\":\"Curso de prueba\",\"type\":\"string\"},"
                        + "{\"name\":\"contactoNombre\",\"value\":\"Ana Pérez\",\"type\":\"string\"},"
                        + "{\"name\":\"contactoEmail\",\"value\":\"ana@example.com\",\"type\":\"string\"},"
                        + "{\"name\":\"participantesEstimados\",\"value\":12,\"type\":\"integer\"},"
                        + "{\"name\":\"fechasPreferidas\",\"value\":\"Marzo 2027\",\"type\":\"string\"},"
                        + "{\"name\":\"plazoRecordatorio\",\"value\":\"PT1M\",\"type\":\"string\"}"
                        + "]}"))
                .andRespond(withSuccess("{\"id\":\"process-2\"}", MediaType.APPLICATION_JSON));

                IniciarSolicitudRequest request = solicitudRequest();
                request.setContactoTelefono(" ");
        request.setPlazoRecordatorio("PT1M");

                assertEquals("process-2", client.iniciarSolicitud("solicitud-prueba", 7L, 42L, request));
                }

                @Test
                void identificaUnRechazoConfirmadoDeFlowable() {
                server.expect(requestTo(BASE_URL + "/runtime/process-instances"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withBadRequest());

                FlowableStartException exception = assertThrows(
                    FlowableStartException.class,
                    () -> client.iniciarSolicitud("solicitud-rechazada", 7L, 42L, solicitudRequest()));

                assertFalse(exception.isOutcomeUnknown());
                }

                @Test
                void conservaComoInciertoUnErrorDelServidorFlowable() {
                server.expect(requestTo(BASE_URL + "/runtime/process-instances"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withServerError());

                FlowableStartException exception = assertThrows(
                    FlowableStartException.class,
                    () -> client.iniciarSolicitud("solicitud-incierta", 7L, 42L, solicitudRequest()));

                org.junit.jupiter.api.Assertions.assertTrue(exception.isOutcomeUnknown());
                }

            @Test
            void buscaUnaInstanciaPorBusinessKeyParaReconciliarUnInicioIncierto() {
                server.expect(requestTo(BASE_URL + "/runtime/process-instances?businessKey=solicitud-demo"))
                        .andExpect(method(HttpMethod.GET))
                        .andRespond(withSuccess(
                                "{\"data\":[{\"id\":\"process-1\"}]}", MediaType.APPLICATION_JSON));

                assertEquals("process-1", client.buscarInstanciaPorBusinessKey("solicitud-demo"));
            }

    @Test
    void validaLasCredencialesConLaClaveDelPropioUsuario() {
        server.expect(requestTo(BASE_URL + "/identity/users/jefeAdministrativo"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic "
                        + HttpHeaders.encodeBasicAuth("jefeAdministrativo", "clave-correcta", StandardCharsets.UTF_8)))
                .andRespond(withSuccess("{\"id\":\"jefeAdministrativo\"}", MediaType.APPLICATION_JSON));

        assertTrue(client.credencialesValidas("jefeAdministrativo", "clave-correcta"));
    }

    @Test
    void unaClaveIncorrectaDevuelveCredencialesInvalidasCuandoFlowableResponde401() {
        server.expect(requestTo(BASE_URL + "/identity/users/jefeAdministrativo"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertFalse(client.credencialesValidas("jefeAdministrativo", "clave-incorrecta"));
    }

    @Test
    void un403DeFlowableCuentaComoCredencialesValidas() {
        server.expect(requestTo(BASE_URL + "/identity/users/usuarioSinApiRest"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertTrue(client.credencialesValidas("usuarioSinApiRest", "clave-correcta"));
    }

    @Test
    void unErrorDelServidorAlValidarCredencialesSePropaga() {
        server.expect(requestTo(BASE_URL + "/identity/users/jefeAdministrativo"))
                .andRespond(withServerError());

        assertThrows(RestClientException.class,
                () -> client.credencialesValidas("jefeAdministrativo", "clave-correcta"));
    }

    @Test
    void elUsuarioViajaCodificadoYNoCambiaLaRuta() {
        server.expect(requestTo(BASE_URL + "/identity/users/a%2F..%2Fb%20c"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertFalse(client.credencialesValidas("a/../b c", "clave"));
    }

    @Test
    void armaElNombreConNombreYApellidoDelUsuario() {
        server.expect(requestTo(BASE_URL + "/identity/users/jefeAdministrativo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"jefeAdministrativo\",\"firstName\":\"Jefe\",\"lastName\":\"administrativo\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals("Jefe administrativo", client.buscarNombre("jefeAdministrativo"));
    }

    @Test
    void elNombreOmiteLasPartesQueFlowableNoTiene() {
        server.expect(requestTo(BASE_URL + "/identity/users/matiasP"))
                .andRespond(withSuccess(
                        "{\"id\":\"matiasP\",\"firstName\":\"Matias\",\"lastName\":null}", MediaType.APPLICATION_JSON));

        assertEquals("Matias", client.buscarNombre("matiasP"));
    }

    @Test
    void devuelveLosIdsDeLosGruposDelUsuario() {
        server.expect(requestTo(BASE_URL + "/identity/groups?member=jefeAdministrativo&size=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"data\":[{\"id\":\"Administracion\",\"name\":\"Administracion de cursos\"},"
                                + "{\"id\":\"Docentes\",\"name\":\"Docentes de los cursos\"}],\"total\":2}",
                        MediaType.APPLICATION_JSON));

        assertEquals(List.of("Administracion", "Docentes"), client.buscarGrupos("jefeAdministrativo"));
    }

    @Test
    void unUsuarioSinGruposDevuelveUnaListaVacia() {
        server.expect(requestTo(BASE_URL + "/identity/groups?member=nuevo&size=100"))
                .andRespond(withSuccess("{\"data\":[],\"total\":0}", MediaType.APPLICATION_JSON));

        assertEquals(List.of(), client.buscarGrupos("nuevo"));
    }

                private IniciarSolicitudRequest solicitudRequest() {
                IniciarSolicitudRequest request = new IniciarSolicitudRequest();
                request.setEmpresaNombre(" Empresa Ejemplo ");
                request.setContactoNombre(" Ana Pérez ");
                request.setContactoEmail(" ana@example.com ");
                request.setContactoTelefono(" 555-0101 ");
                request.setTematica(" BPMN ");
                request.setDescripcion(" Curso de prueba ");
                request.setParticipantesEstimados(12);
                request.setFechasPreferidas(" Marzo 2027 ");
                return request;
    }
}
