package web.backend.flowable;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import web.backend.api.dto.ActividadResponse;
import web.backend.api.dto.CompletarTareaRequest;
import web.backend.api.dto.PaginaResponse;
import web.backend.api.dto.TareaResponse;
import web.backend.api.dto.VariableResponse;

/**
 * Consultas y acciones (reclamar, completar) sobre tareas y variables en la API REST de Flowable,
 * con la cuenta admin del RestTemplate.
 * Flowable no sabe quién usa la web: los permisos los aplica TareaService con el usuario de la
 * sesión. Es una clase aparte de FlowableRestClient (solicitudes y sesión) para no mezclar temas;
 * usa el mismo bean RestTemplate y la misma base-url.
 *
 * Errores: una respuesta de error del motor sale como FlowableRespuestaException con estado y
 * cuerpo intactos. Un 401/403 del motor significa que falló la cuenta del backend: se registra
 * en el log y sale como RestClientException, igual que un error de conexión.
 */
@Component
public class FlowableTareaClient {
    /** Key del proceso en el BPMN. */
    public static final String PROCESO = "process-proyecto-curso";

    private static final Logger LOG = LoggerFactory.getLogger(FlowableTareaClient.class);
    private static final String RUTA_TAREAS = "/runtime/tasks";
    private static final String RUTA_TAREA = "/runtime/tasks/{id}";
    private static final String RUTA_VINCULOS = "/runtime/tasks/{id}/identitylinks";
    private static final String RUTA_VARIABLES = "/runtime/process-instances/{id}/variables";
    private static final String RUTA_ACTIVIDADES = "/history/historic-activity-instances";
    /** Tipos de actividad que se muestran a la empresa. */
    private static final Set<String> TIPOS_VISIBLES = Set.of("userTask", "receiveTask", "serviceTask",
            "scriptTask", "intermediateCatchEvent");
    /** Máximo de actividades en curso por instancia. */
    private static final int MAXIMO_ACTIVIDADES = 100;
    /** Tipo de vínculo de Flowable que marca a un grupo (o usuario) como candidato de la tarea. */
    private static final String VINCULO_CANDIDATO = "candidate";
    /** Orden de las tareas: las más viejas primero. */
    private static final String ORDEN_CAMPO = "createTime";
    private static final String ORDEN_SENTIDO = "asc";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    /** Recibe el RestTemplate con la cuenta admin de Flowable y la URL base de la API REST. */
    public FlowableTareaClient(
            RestTemplate restTemplate,
            @Value("${flowable.rest.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /** Busca tareas activas del proceso por asignado o por grupos candidatos, paginadas. */
    public PaginaResponse<TareaResponse> buscarTareas(
            String asignado, List<String> gruposCandidatos, String instanciaId, int start, int size) {
        boolean conAsignado = asignado != null && !asignado.isBlank();
        boolean conGrupos = gruposCandidatos != null && !gruposCandidatos.isEmpty();
        if (conAsignado == conGrupos) {
            throw new IllegalArgumentException("Se busca por usuario asignado o por grupos, uno de los dos.");
        }
        UriComponentsBuilder url = UriComponentsBuilder.fromHttpUrl(baseUrl + RUTA_TAREAS)
                .queryParam("processDefinitionKey", PROCESO);
        Map<String, Object> valores = new HashMap<>();
        if (conAsignado) {
            url.queryParam("assignee", "{asignado}");
            valores.put("asignado", asignado);
        } else {
            url.queryParam("candidateGroups", "{grupos}").queryParam("unassigned", true);
            valores.put("grupos", String.join(",", gruposCandidatos));
        }
        if (instanciaId != null) {
            url.queryParam("processInstanceId", "{instancia}");
            valores.put("instancia", instanciaId);
        }
        URI uri = url.queryParam("sort", ORDEN_CAMPO)
                .queryParam("order", ORDEN_SENTIDO)
                .queryParam("start", start)
                .queryParam("size", size)
                .encode()
                .buildAndExpand(valores)
                .toUri();

        Map<?, ?> respuesta = consultar("listar tareas", () -> restTemplate.getForObject(uri, Map.class));
        List<TareaResponse> tareas = new ArrayList<>();
        for (Map<?, ?> tarea : elementos(respuesta == null ? null : respuesta.get("data"))) {
            tareas.add(tarea(tarea));
        }
        Object total = respuesta == null ? null : respuesta.get("total");
        return new PaginaResponse<>(tareas, total instanceof Number ? ((Number) total).longValue() : 0,
                start, size);
    }

    /** Busca una tarea activa del proceso por id. */
    public Optional<TareaResponse> obtenerTarea(String tareaId) {
        URI uri = url(RUTA_TAREA, tareaId);
        Map<?, ?> tarea;
        try {
            tarea = consultar("consultar una tarea", () -> restTemplate.getForObject(uri, Map.class));
        } catch (FlowableRespuestaException exception) {
            if (exception.getEstado() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw exception;
        }
        // Filtra por key: el id de la definición es "<key>:<versión>:<id>"
        Object definicion = tarea == null ? null : tarea.get("processDefinitionId");
        if (!(definicion instanceof String) || !((String) definicion).startsWith(PROCESO + ":")) {
            return Optional.empty();
        }
        return Optional.of(tarea(tarea));
    }

    /** Devuelve los ids de los grupos candidatos de la tarea (vínculos "candidate" con grupo). */
    public List<String> gruposCandidatos(String tareaId) {
        URI uri = url(RUTA_VINCULOS, tareaId);
        List<?> vinculos = consultar("consultar los candidatos de una tarea",
                () -> restTemplate.getForObject(uri, List.class));
        List<String> grupos = new ArrayList<>();
        for (Map<?, ?> vinculo : elementos(vinculos)) {
            Object grupo = vinculo.get("group");
            if (VINCULO_CANDIDATO.equals(vinculo.get("type")) && grupo instanceof String) {
                grupos.add((String) grupo);
            }
        }
        return grupos;
    }

    /** Devuelve todas las variables de la instancia, sin filtrar. */
    public List<VariableResponse> variablesDeInstancia(String instanciaId) {
        URI uri = url(RUTA_VARIABLES, instanciaId);
        List<?> variables = consultar("consultar las variables de una instancia",
                () -> restTemplate.getForObject(uri, List.class));
        List<VariableResponse> resultado = new ArrayList<>();
        for (Map<?, ?> variable : elementos(variables)) {
            resultado.add(new VariableResponse(
                    texto(variable, "name"), texto(variable, "type"), variable.get("value")));
        }
        return resultado;
    }

    /** Devuelve las actividades sin terminar de la instancia, incluidas las de ramas paralelas. */
    public List<ActividadResponse> actividadesEnCurso(String instanciaId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + RUTA_ACTIVIDADES)
                .queryParam("processInstanceId", "{id}")
                .queryParam("finished", false)
                .queryParam("sort", "startTime")
                .queryParam("order", "asc")
                .queryParam("size", MAXIMO_ACTIVIDADES)
                .encode()
                .buildAndExpand(instanciaId)
                .toUri();
        Map<?, ?> respuesta = consultar("consultar las actividades de una instancia",
                () -> restTemplate.getForObject(uri, Map.class));
        List<ActividadResponse> resultado = new ArrayList<>();
        for (Map<?, ?> actividad : elementos(respuesta == null ? null : respuesta.get("data"))) {
            if (TIPOS_VISIBLES.contains(texto(actividad, "activityType"))) {
                resultado.add(new ActividadResponse(texto(actividad, "activityName"),
                        texto(actividad, "activityType"), texto(actividad, "startTime")));
            }
        }
        return resultado;
    }

    /** Reclama la tarea para el usuario. */
    public void reclamarTarea(String tareaId, String usuario) {
        if (usuario == null || usuario.isBlank()) {
            throw new IllegalArgumentException("Un claim sin assignee libera la tarea: hace falta el usuario.");
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("action", "claim");
        cuerpo.put("assignee", usuario);
        accion("reclamar una tarea", tareaId, cuerpo);
    }

    /** Completa la tarea con las variables recibidas. */
    public void completarTarea(String tareaId, List<CompletarTareaRequest.Variable> variables) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (CompletarTareaRequest.Variable variable : variables) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", variable.getName());
            item.put("type", variable.getType());
            item.put("value", variable.getValue());
            lista.add(item);
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("action", "complete");
        cuerpo.put("variables", lista);
        accion("completar una tarea", tareaId, cuerpo);
    }

    /** Manda una acción (claim, complete) a la tarea. */
    private void accion(String descripcion, String tareaId, Map<String, Object> cuerpo) {
        URI uri = url(RUTA_TAREA, tareaId);
        consultar(descripcion, () -> restTemplate.postForEntity(uri, cuerpo, Void.class));
    }

    /** Ejecuta una consulta y traduce los errores del motor. */
    private static <T> T consultar(String descripcion, Supplier<T> consulta) {
        try {
            return consulta.get();
        } catch (HttpStatusCodeException exception) {
            HttpStatus estado = exception.getStatusCode();
            if (estado == HttpStatus.UNAUTHORIZED || estado == HttpStatus.FORBIDDEN) {
                // El motor rechazó la cuenta del backend
                LOG.error("Flowable rechazó la cuenta del backend ({}) al {}. Revisar FLOWABLE_USER y "
                        + "FLOWABLE_PASSWORD.", estado.value(), descripcion);
                throw new RestClientException("Flowable rechazó la cuenta del backend (" + estado.value() + ").");
            }
            throw new FlowableRespuestaException(estado.value(), exception.getResponseBodyAsByteArray(),
                    exception.getResponseHeaders() == null ? null : exception.getResponseHeaders().getContentType(),
                    exception);
        }
    }

    private URI url(String ruta, String id) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl + ruta)
                .encode()
                .buildAndExpand(id)
                .toUri();
    }

    /** Convierte una tarea del JSON de Flowable en la respuesta de la API. */
    private static TareaResponse tarea(Map<?, ?> tarea) {
        return new TareaResponse(texto(tarea, "id"), texto(tarea, "name"), texto(tarea, "assignee"),
                texto(tarea, "taskDefinitionKey"), texto(tarea, "processInstanceId"),
                texto(tarea, "createTime"), texto(tarea, "dueDate"));
    }

    /** Devuelve los objetos JSON de una lista; cualquier otra cosa se ignora. */
    private static List<Map<?, ?>> elementos(Object lista) {
        List<Map<?, ?>> resultado = new ArrayList<>();
        if (lista instanceof List<?>) {
            for (Object item : (List<?>) lista) {
                if (item instanceof Map<?, ?>) {
                    resultado.add((Map<?, ?>) item);
                }
            }
        }
        return resultado;
    }

    /** Devuelve el campo como texto, o null si falta o no es texto. */
    private static String texto(Map<?, ?> objeto, String campo) {
        Object valor = objeto.get(campo);
        return valor instanceof String ? (String) valor : null;
    }
}
