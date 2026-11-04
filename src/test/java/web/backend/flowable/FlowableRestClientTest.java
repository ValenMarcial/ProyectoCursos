package web.backend.flowable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
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
                        + "{\"name\":\"empresaId\",\"value\":42,\"type\":\"long\"},"
                        + "{\"name\":\"nombre\",\"value\":\"Curso\",\"type\":\"string\"},"
                        + "{\"name\":\"descripcion\",\"value\":\"Capacitación\",\"type\":\"string\"}"
                        + "]}"))
                .andRespond(withSuccess("{\"id\":\"process-1\"}", MediaType.APPLICATION_JSON));

        IniciarSolicitudRequest request = new IniciarSolicitudRequest();
        request.setEmpresaId(42L);
        request.setNombre(" Curso ");
        request.setDescripcion(" Capacitación ");

        assertEquals("process-1", client.iniciarSolicitud("solicitud-demo", request));
    }

    @Test
    void incluyeElPlazoOpcionalParaPruebas() {
        server.expect(requestTo(BASE_URL + "/runtime/process-instances"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{"
                        + "\"message\":\"msgSolicitud\","
                        + "\"businessKey\":\"solicitud-prueba\","
                        + "\"variables\":["
                        + "{\"name\":\"empresaId\",\"value\":42,\"type\":\"long\"},"
                        + "{\"name\":\"nombre\",\"value\":\"Curso\",\"type\":\"string\"},"
                        + "{\"name\":\"descripcion\",\"value\":\"Capacitación\",\"type\":\"string\"},"
                        + "{\"name\":\"plazoRecordatorio\",\"value\":\"PT1M\",\"type\":\"string\"}"
                        + "]}"))
                .andRespond(withSuccess("{\"id\":\"process-2\"}", MediaType.APPLICATION_JSON));

        IniciarSolicitudRequest request = new IniciarSolicitudRequest();
        request.setEmpresaId(42L);
        request.setNombre("Curso");
        request.setDescripcion("Capacitación");
        request.setPlazoRecordatorio("PT1M");

        assertEquals("process-2", client.iniciarSolicitud("solicitud-prueba", request));
    }
}
