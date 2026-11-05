package web.backend.flowable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import web.backend.api.dto.IniciarSolicitudRequest;

@Component
public class FlowableRestClient {
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
