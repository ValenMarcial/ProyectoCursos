package web.backend.flowable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

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

    public String iniciarSolicitud(String businessKey, IniciarSolicitudRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "msgSolicitud");
        body.put("businessKey", businessKey);
        body.put("variables", variables(request));

        try {
            Map<?, ?> response = restTemplate.postForObject(
                    baseUrl + "/runtime/process-instances", body, Map.class);
            Object id = response == null ? null : response.get("id");
            if (id instanceof String) {
                return (String) id;
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Flowable no devolvió el ID del proceso iniciado.");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "No se pudo iniciar el proceso en Flowable.", exception);
        }
    }

    private List<Map<String, Object>> variables(IniciarSolicitudRequest request) {
        List<Map<String, Object>> variables = new ArrayList<>();
        agregarVariable(variables, "empresaId", request.getEmpresaId(), "long");
        agregarVariable(variables, "nombre", request.getNombre().trim(), "string");
        agregarVariable(variables, "descripcion", request.getDescripcion().trim(), "string");
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
