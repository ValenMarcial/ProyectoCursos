package simulador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/** Prueba el simulador contra un Flowable falso: no hace falta levantar el entorno. */
class SimuladorTest {
    private static final String INSTANCIA = "inst-1";
    private static final String BASIC_ADMIN_TEST = "Basic YWRtaW46dGVzdA==";

    private HttpServer servidor;
    private Simulador simulador;

    /** Respuesta por "metodo ruta?consulta"; lo que no está devuelve una lista vacía. */
    private final Map<String, String> respuestas = new LinkedHashMap<>();
    /** Pedidos recibidos como "METODO ruta?consulta | cuerpo". */
    private final List<String> pedidos = new ArrayList<>();
    private final List<String> autorizaciones = new ArrayList<>();

    @BeforeEach
    void arrancar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            String consulta = intercambio.getRequestURI().getRawQuery();
            String clave = intercambio.getRequestMethod() + " " + intercambio.getRequestURI().getPath()
                    + (consulta == null ? "" : "?" + consulta);
            String cuerpoPedido = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            pedidos.add(clave + " | " + cuerpoPedido);
            autorizaciones.add(intercambio.getRequestHeaders().getFirst("Authorization"));

            String cuerpo = respuestas.getOrDefault(clave, "{\"data\":[]}");
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(clave.startsWith("PUT") ? 204 : 200, clave.startsWith("PUT") ? -1 : bytes.length);
            if (!clave.startsWith("PUT")) {
                intercambio.getResponseBody().write(bytes);
            }
            intercambio.close();
        });
        servidor.start();

        String url = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/flowable-ui/process-api";
        simulador = new Simulador(new FlowableClient(url, "admin", "test"));
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    private static String consultaMensaje(String mensaje) {
        return "GET /flowable-ui/process-api/runtime/executions?processInstanceId=" + INSTANCIA
                + "&messageEventSubscriptionName=" + mensaje;
    }

    private static String ejecucion(String id) {
        return "{\"data\":[{\"id\":\"" + id + "\"}]}";
    }

    @Test
    void listaLasSolicitudesConSusVariables() {
        respuestas.put("GET /flowable-ui/process-api/runtime/process-instances?processDefinitionKey=process-proyecto-curso"
                + "&includeProcessVariables=true&size=200&sort=startTime&order=desc",
                "{\"data\":[{\"id\":\"inst-1\",\"businessKey\":\"solicitud-abc\",\"variables\":["
                        + "{\"name\":\"empresaNombre\",\"value\":\"Acme SA\"},"
                        + "{\"name\":\"empresaId\",\"value\":7}]},"
                        + "{\"id\":\"inst-2\",\"businessKey\":null,\"variables\":[]}]}");

        List<Solicitud> solicitudes = simulador.solicitudes();

        assertEquals(2, solicitudes.size());
        assertEquals("inst-1", solicitudes.get(0).id());
        assertEquals("solicitud-abc", solicitudes.get(0).businessKey());
        assertEquals("Acme SA", solicitudes.get(0).nombre());
        assertEquals(7, ((Number) solicitudes.get(0).variables().get("empresaId")).intValue());
        assertEquals(null, solicitudes.get(1).businessKey());
        assertEquals("(sin nombre)", solicitudes.get(1).nombre());
    }

    @Test
    void enLaEsperaDeRespuestaSoloEstanHabilitadasAceptacionRechazoYModificaciones() {
        respuestas.put(consultaMensaje("msgAceptacion"), ejecucion("e1"));
        respuestas.put(consultaMensaje("msgRechazo"), ejecucion("e2"));
        respuestas.put(consultaMensaje("msgModificaciones"), ejecucion("e3"));

        assertEquals(List.of(Comunicacion.ACEPTACION, Comunicacion.RECHAZO, Comunicacion.MODIFICACIONES),
                simulador.habilitadas(INSTANCIA));
    }

    @Test
    void sinNadaEsperandoNoHayComunicacionesHabilitadas() {
        assertTrue(simulador.habilitadas(INSTANCIA).isEmpty());
    }

    @Test
    void duranteLaPreparacionEstanHabilitadasNominaYCancelacion() {
        respuestas.put("GET /flowable-ui/process-api/runtime/executions?processInstanceId=" + INSTANCIA
                + "&activityId=" + Comunicacion.NOMINA.actividadId(), ejecucion("e9"));
        respuestas.put(consultaMensaje("msgCancelacion"), ejecucion("e8"));

        assertEquals(List.of(Comunicacion.NOMINA, Comunicacion.CANCELACION), simulador.habilitadas(INSTANCIA));
    }

    @Test
    void laAceptacionSeEnviaComoMensajeConLaFechaAcordada() throws IOException {
        respuestas.put(consultaMensaje("msgAceptacion"), ejecucion("e1"));

        simulador.enviar(INSTANCIA, Comunicacion.ACEPTACION, Map.of("fechaInicio", " 2026-11-20T09:00:00 "));

        String put = pedidos.get(pedidos.size() - 1);
        assertTrue(put.startsWith("PUT /flowable-ui/process-api/runtime/executions/e1 | "), put);
        JsonNode cuerpo = new ObjectMapper().readTree(put.substring(put.indexOf(" | ") + 3));
        assertEquals("messageEventReceived", cuerpo.get("action").asText());
        assertEquals("msgAceptacion", cuerpo.get("messageName").asText());
        assertEquals("fechaInicio", cuerpo.get("variables").get(0).get("name").asText());
        // Se envía con zona horaria (la de esta máquina) para que Flowable no la lea como UTC
        assertEquals(java.time.LocalDateTime.parse("2026-11-20T09:00:00").atZone(java.time.ZoneId.systemDefault())
                        .toInstant(),
                java.time.OffsetDateTime.parse(cuerpo.get("variables").get(0).get("value").asText()).toInstant());
    }

    @Test
    void laFechaSinZonaSeInterpretaEnLaZonaLocalYLaQueTraeZonaSeRespeta() {
        java.time.ZoneId buenosAires = java.time.ZoneId.of("America/Argentina/Buenos_Aires");

        assertEquals("2026-11-20T09:00:00-03:00", Simulador.normalizarFecha("2026-11-20T09:00:00", buenosAires));
        assertEquals("2026-11-20T09:00:00Z", Simulador.normalizarFecha("2026-11-20T09:00:00Z", buenosAires));
        assertEquals("2026-11-20T09:00:00+01:00", Simulador.normalizarFecha("2026-11-20T09:00:00+01:00", buenosAires));
    }

    @Test
    void elRechazoSeEnviaSinVariables() throws IOException {
        respuestas.put(consultaMensaje("msgRechazo"), ejecucion("e2"));

        simulador.enviar(INSTANCIA, Comunicacion.RECHAZO, Map.of());

        String put = pedidos.get(pedidos.size() - 1);
        JsonNode cuerpo = new ObjectMapper().readTree(put.substring(put.indexOf(" | ") + 3));
        assertEquals("msgRechazo", cuerpo.get("messageName").asText());
        assertEquals(0, cuerpo.get("variables").size());
    }

    @Test
    void laNominaSeEnviaComoSenalAlReceiveTask() throws IOException {
        respuestas.put("GET /flowable-ui/process-api/runtime/executions?processInstanceId=" + INSTANCIA
                + "&activityId=" + Comunicacion.NOMINA.actividadId(), ejecucion("e9"));

        simulador.enviar(INSTANCIA, Comunicacion.NOMINA, Map.of("participantes", "Ana, Luis"));

        String put = pedidos.get(pedidos.size() - 1);
        assertTrue(put.startsWith("PUT /flowable-ui/process-api/runtime/executions/e9 | "), put);
        JsonNode cuerpo = new ObjectMapper().readTree(put.substring(put.indexOf(" | ") + 3));
        assertEquals("signal", cuerpo.get("action").asText());
        assertEquals("participantes", cuerpo.get("variables").get(0).get("name").asText());
        assertEquals("Ana, Luis", cuerpo.get("variables").get(0).get("value").asText());
    }

    @Test
    void noPermiteEnviarUnaComunicacionQueElProcesoNoEspera() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> simulador.enviar(INSTANCIA, Comunicacion.CANCELACION, Map.of()));

        assertTrue(error.getMessage().contains("no espera"), error.getMessage());
        assertTrue(pedidos.stream().noneMatch(p -> p.startsWith("PUT")), "no debe enviar nada a Flowable");
    }

    @Test
    void laAceptacionExigeUnaFechaIsoValida() {
        assertThrows(IllegalArgumentException.class,
                () -> simulador.enviar(INSTANCIA, Comunicacion.ACEPTACION, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> simulador.enviar(INSTANCIA, Comunicacion.ACEPTACION, Map.of("fechaInicio", "20/11/2026")));
        assertTrue(pedidos.isEmpty(), "con datos inválidos no debe consultar ni enviar nada");
    }

    @Test
    void lasModificacionesExigenObservaciones() {
        assertThrows(IllegalArgumentException.class,
                () -> simulador.enviar(INSTANCIA, Comunicacion.MODIFICACIONES, Map.of("observaciones", "  ")));
    }

    @Test
    void autenticaConLasCredencialesIndicadas() {
        simulador.habilitadas(INSTANCIA);

        assertTrue(autorizaciones.size() > 0);
        assertTrue(autorizaciones.stream().allMatch(BASIC_ADMIN_TEST::equals));
    }

    @Test
    void siFlowableNoEstaDisponibleElErrorEsClaro() {
        servidor.stop(0);

        FlowableClient.FlowableException error = assertThrows(FlowableClient.FlowableException.class,
                () -> simulador.habilitadas(INSTANCIA));

        assertTrue(error.getMessage().contains("No se pudo conectar"), error.getMessage());
    }

    @Test
    void laEtapaDependeDeLoQueElProcesoEspera() {
        assertEquals("Sin actividad pendiente", simulador.etapa(INSTANCIA).texto());
        assertEquals(Etapa.Turno.NADIE, simulador.etapa(INSTANCIA).turno());

        respuestas.put("GET /flowable-ui/process-api/runtime/tasks?processInstanceId=" + INSTANCIA + "&size=50",
                "{\"data\":[{\"name\":\"Archivar solicitud\"}]}");
        assertEquals("Pendiente de la institución: Archivar solicitud", simulador.etapa(INSTANCIA).texto());
        assertEquals(Etapa.Turno.INSTITUCION, simulador.etapa(INSTANCIA).turno());

        respuestas.put("GET /flowable-ui/process-api/runtime/executions?processInstanceId=" + INSTANCIA
                + "&messageEventSubscriptionName=msgCancelacion", ejecucion("c1"));
        assertEquals("Preparando curso", simulador.etapa(INSTANCIA).texto());

        respuestas.put("GET /flowable-ui/process-api/runtime/executions?processInstanceId=" + INSTANCIA
                + "&activityId=" + Comunicacion.NOMINA.actividadId(), ejecucion("n1"));
        assertEquals("Preparando curso: falta la nómina", simulador.etapa(INSTANCIA).texto());
        assertEquals(Etapa.Turno.EMPRESA, simulador.etapa(INSTANCIA).turno());

        respuestas.put(consultaMensaje("msgAceptacion"), ejecucion("a1"));
        assertEquals("Esperando respuesta de la empresa", simulador.etapa(INSTANCIA).texto());
    }
}
