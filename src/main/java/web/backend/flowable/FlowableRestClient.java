package web.backend.flowable;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import web.backend.api.dto.IniciarSolicitudRequest;

@Component
public class FlowableRestClient {
    /** Usuario de Flowable. */
    private static final String RUTA_USUARIO = "/identity/users/{usuario}";
    private static final String RUTA_GRUPOS = "/identity/groups";
    /** Máximo de grupos por usuario. */
    private static final int MAXIMO_GRUPOS = 100;

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public FlowableRestClient(
            RestTemplate restTemplate,
            @Value("${flowable.rest.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

        public String iniciarSolicitud(
            String businessKey, Long solicitudId, Long empresaId, IniciarSolicitudRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "msgSolicitud");
        body.put("businessKey", businessKey);
        body.put("variables", variables(solicitudId, empresaId, request));

        try {
            Map<?, ?> response = restTemplate.postForObject(
                    baseUrl + "/runtime/process-instances", body, Map.class);
            Object id = response == null ? null : response.get("id");
            if (id instanceof String) {
                return (String) id;
            }
            throw new FlowableStartException("Flowable no devolvió el ID del proceso iniciado.", true);
        } catch (HttpClientErrorException exception) {
            throw new FlowableStartException("Flowable rechazó el inicio de la solicitud.", false, exception);
        } catch (RestClientException exception) {
            throw new FlowableStartException("No se pudo confirmar el inicio del proceso en Flowable.", true,
                    exception);
        }
    }

    public String buscarInstanciaPorBusinessKey(String businessKey) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/runtime/process-instances")
                .queryParam("businessKey", businessKey)
                .build()
                .encode()
                .toUriString();
        Map<?, ?> response = restTemplate.getForObject(url, Map.class);
        Object data = response == null ? null : response.get("data");
        if (!(data instanceof List<?>)) {
            return null;
        }
        for (Object item : (List<?>) data) {
            if (item instanceof Map<?, ?>) {
                Object id = ((Map<?, ?>) item).get("id");
                if (id instanceof String) {
                    return (String) id;
                }
            }
        }
        return null;
    }

    /** Valida usuario y clave contra Flowable. */
    public boolean credencialesValidas(String usuario, String clave) {
        HttpHeaders headers = new HttpHeaders();
        // Credenciales del usuario en lugar de las de admin
        headers.setBasicAuth(usuario, clave, StandardCharsets.UTF_8);
        try {
            restTemplate.exchange(urlUsuario(usuario), HttpMethod.GET, new HttpEntity<>(headers), Void.class);
            return true;
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                return false;
            }
            // 403: autenticado, sin permiso para la API REST
            if (exception.getStatusCode() == HttpStatus.FORBIDDEN) {
                return true;
            }
            throw exception;
        }
    }

    /** Devuelve el nombre para mostrar: nombre y apellido del usuario, consultados con la cuenta admin. */
    public String buscarNombre(String usuario) {
        Map<?, ?> response = restTemplate.getForObject(urlUsuario(usuario), Map.class);
        List<String> partes = new ArrayList<>();
        for (String campo : new String[] {"firstName", "lastName"}) {
            Object valor = response == null ? null : response.get(campo);
            if (valor instanceof String && !((String) valor).trim().isEmpty()) {
                partes.add(((String) valor).trim());
            }
        }
        return String.join(" ", partes);
    }

    /** Devuelve los ids de los grupos del usuario, consultados con la cuenta admin. */
    public List<String> buscarGrupos(String usuario) {
        URI url = UriComponentsBuilder.fromHttpUrl(baseUrl + RUTA_GRUPOS)
                .queryParam("member", "{usuario}")
                .queryParam("size", MAXIMO_GRUPOS)
                .encode()
                .buildAndExpand(usuario)
                .toUri();
        Map<?, ?> response = restTemplate.getForObject(url, Map.class);
        Object data = response == null ? null : response.get("data");
        List<String> grupos = new ArrayList<>();
        if (!(data instanceof List<?>)) {
            return grupos;
        }
        for (Object item : (List<?>) data) {
            if (item instanceof Map<?, ?>) {
                Object id = ((Map<?, ?>) item).get("id");
                if (id instanceof String) {
                    grupos.add((String) id);
                }
            }
        }
        return grupos;
    }

    private URI urlUsuario(String usuario) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl + RUTA_USUARIO)
                .encode()
                .buildAndExpand(usuario)
                .toUri();
    }

    private List<Map<String, Object>> variables(
            Long solicitudId, Long empresaId, IniciarSolicitudRequest request) {
        List<Map<String, Object>> variables = new ArrayList<>();
        agregarVariable(variables, "solicitudId", solicitudId, "long");
        agregarVariable(variables, "empresaNombre", request.getEmpresaNombre().trim(), "string");
        agregarVariable(variables, "empresaId", empresaId, "long");
        agregarVariable(variables, "tematica", request.getTematica().trim(), "string");
        agregarVariable(variables, "descripcion", request.getDescripcion().trim(), "string");
        agregarVariable(variables, "contactoNombre", request.getContactoNombre().trim(), "string");
        agregarVariable(variables, "contactoEmail", request.getContactoEmail().trim(), "string");
        agregarVariable(variables, "participantesEstimados", request.getParticipantesEstimados(), "integer");
        agregarVariable(variables, "fechasPreferidas", request.getFechasPreferidas().trim(), "string");
        if (request.getContactoTelefono() != null && !request.getContactoTelefono().trim().isEmpty()) {
            agregarVariable(variables, "contactoTelefono", request.getContactoTelefono().trim(), "string");
        }
        if (request.getPlazoRecordatorio() != null && !request.getPlazoRecordatorio().trim().isEmpty()) {
            agregarVariable(variables, "plazoRecordatorio", request.getPlazoRecordatorio().trim(), "string");
        }
        return variables;
    }

    private void agregarVariable(List<Map<String, Object>> variables, String name, Object value, String type) {
        Map<String, Object> variable = new LinkedHashMap<>();
        variable.put("name", name);
        variable.put("value", value);
        variable.put("type", type);
        variables.add(variable);
    }
}
