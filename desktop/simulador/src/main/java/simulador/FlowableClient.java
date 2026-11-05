package simulador;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Cliente mínimo de la API REST de Flowable (solo lo que necesita el simulador). */
public class FlowableClient {
    public static final String PROCESS_KEY = "process-proyecto-curso";

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;
    private final String authorization;

    /** @param baseUrl por ejemplo http://127.0.0.1:8080/flowable-ui/process-api */
    public FlowableClient(String baseUrl, String usuario, String password) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        String credenciales = usuario + ":" + password;
        this.authorization = "Basic "
                + Base64.getEncoder().encodeToString(credenciales.getBytes(StandardCharsets.UTF_8));
    }

    /** Instancias del proceso que todavía no terminaron, con sus variables. */
    public List<Solicitud> listarSolicitudes() {
        JsonNode respuesta = get("/runtime/process-instances?processDefinitionKey=" + PROCESS_KEY
                + "&includeProcessVariables=true&size=200&sort=startTime&order=desc");
        List<Solicitud> solicitudes = new ArrayList<>();
        for (JsonNode instancia : respuesta.path("data")) {
            Map<String, Object> variables = new LinkedHashMap<>();
            for (JsonNode variable : instancia.path("variables")) {
                variables.put(variable.path("name").asText(), valor(variable.path("value")));
            }
            String businessKey = instancia.path("businessKey").isNull()
                    ? null : instancia.path("businessKey").asText(null);
            solicitudes.add(new Solicitud(instancia.path("id").asText(), businessKey, variables));
        }
        return solicitudes;
    }

    /** Ids de las ejecuciones de la instancia que están esperando el mensaje indicado. */
    public List<String> ejecucionesEsperandoMensaje(String instanciaId, String mensaje) {
        return ejecuciones("processInstanceId=" + enc(instanciaId) + "&messageEventSubscriptionName=" + enc(mensaje));
    }

    /** Ids de las ejecuciones de la instancia que están detenidas en la actividad indicada. */
    public List<String> ejecucionesEnActividad(String instanciaId, String actividadId) {
        return ejecuciones("processInstanceId=" + enc(instanciaId) + "&activityId=" + enc(actividadId));
    }

    /** Entrega un mensaje de evento a una ejecución que lo espera. */
    public void enviarMensaje(String ejecucionId, String mensaje, Map<String, String> variables) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("action", "messageEventReceived");
        cuerpo.put("messageName", mensaje);
        cuerpo.put("variables", variablesJson(variables));
        put("/runtime/executions/" + enc(ejecucionId), cuerpo);
    }

    /** Despierta una ejecución detenida en un receive task. */
    public void senalar(String ejecucionId, Map<String, String> variables) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("action", "signal");
        cuerpo.put("variables", variablesJson(variables));
        put("/runtime/executions/" + enc(ejecucionId), cuerpo);
    }

    private List<String> ejecuciones(String filtros) {
        JsonNode respuesta = get("/runtime/executions?" + filtros);
        List<String> ids = new ArrayList<>();
        for (JsonNode ejecucion : respuesta.path("data")) {
            ids.add(ejecucion.path("id").asText());
        }
        return ids;
    }

    private static List<Map<String, Object>> variablesJson(Map<String, String> variables) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Map.Entry<String, String> entrada : variables.entrySet()) {
            Map<String, Object> variable = new LinkedHashMap<>();
            variable.put("name", entrada.getKey());
            variable.put("value", entrada.getValue());
            variable.put("type", "string");
            lista.add(variable);
        }
        return lista;
    }

    private static Object valor(JsonNode nodo) {
        if (nodo.isNull() || nodo.isMissingNode()) {
            return null;
        }
        if (nodo.isNumber()) {
            return nodo.numberValue();
        }
        if (nodo.isBoolean()) {
            return nodo.booleanValue();
        }
        return nodo.asText();
    }

    private static String enc(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    private JsonNode get(String ruta) {
        HttpRequest pedido = base(ruta).GET().build();
        return enviar(pedido);
    }

    private void put(String ruta, Object cuerpo) {
        try {
            HttpRequest pedido = base(ruta)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)))
                    .build();
            enviar(pedido);
        } catch (IOException e) {
            throw new FlowableException("No se pudo armar el pedido a Flowable: " + e.getMessage(), e);
        }
    }

    private HttpRequest.Builder base(String ruta) {
        return HttpRequest.newBuilder(URI.create(baseUrl + ruta))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", authorization)
                .header("Accept", "application/json");
    }

    private JsonNode enviar(HttpRequest pedido) {
        try {
            HttpResponse<String> respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            int estado = respuesta.statusCode();
            if (estado == 401 || estado == 403) {
                throw new FlowableException("Flowable rechazó las credenciales (HTTP " + estado + ").");
            }
            if (estado < 200 || estado >= 300) {
                throw new FlowableException("Flowable respondió HTTP " + estado + ": " + respuesta.body());
            }
            String cuerpo = respuesta.body();
            return cuerpo == null || cuerpo.isBlank() ? json.createObjectNode() : json.readTree(cuerpo);
        } catch (IOException e) {
            throw new FlowableException("No se pudo conectar con Flowable en " + baseUrl + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FlowableException("Pedido a Flowable interrumpido.", e);
        }
    }

    /** Error al hablar con Flowable (conexión, credenciales o respuesta no exitosa). */
    public static class FlowableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public FlowableException(String mensaje) {
            super(mensaje);
        }

        public FlowableException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
