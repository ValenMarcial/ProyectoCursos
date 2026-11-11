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
    /** Key del proceso en el BPMN. Toda consulta se limita a las tareas de este proceso. */
    public static final String PROCESO = "process-proyecto-curso";

    private static final Logger LOG = LoggerFactory.getLogger(FlowableTareaClient.class);
    private static final String RUTA_TAREAS = "/runtime/tasks";
    // Los ids van como variables de la URI para que se codifiquen y no alteren la ruta.
    private static final String RUTA_TAREA = "/runtime/tasks/{id}";
    private static final String RUTA_VINCULOS = "/runtime/tasks/{id}/identitylinks";
    private static final String RUTA_VARIABLES = "/runtime/process-instances/{id}/variables";
    private static final String RUTA_ACTIVIDADES = "/history/historic-activity-instances";
    /**
     * Tipos de actividad que se muestran a la empresa. Quedan afuera compuertas, eventos de inicio
     * y fin, y el subproceso, que siguen "sin terminar" mientras corren sus actividades internas.
     */
    private static final Set<String> TIPOS_VISIBLES = Set.of("userTask", "receiveTask", "serviceTask",
            "scriptTask", "intermediateCatchEvent");
    /** Más que suficiente: una instancia tiene como mucho unas pocas actividades en paralelo. */
    private static final int MAXIMO_ACTIVIDADES = 100;
    /** Tipo de vínculo de Flowable que marca a un grupo (o usuario) como candidato de la tarea. */
    private static final String VINCULO_CANDIDATO = "candidate";
    /** Orden fijo para que la paginación sea estable: las tareas más viejas primero. */
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

    /**
     * Busca tareas activas del proceso, paginadas y ordenadas por fecha de creación.
     * Recibe el usuario asignado (o null), los grupos candidatos (o null), la instancia (o null)
     * y la página (start y size). Con grupos, solo trae tareas SIN asignar: las que ya tomó
     * alguien dejan de estar disponibles para el grupo.
     * Hace falta asignado o grupos: sin ninguno de los dos Flowable devolvería todas las tareas,
     * así que se corta antes con IllegalArgumentException. Por lo mismo no se aceptan grupos vacíos.
     * Devuelve la página con el total que informa Flowable y el size pedido.
     */
    public PaginaResponse<TareaResponse> buscarTareas(
            String asignado, List<String> gruposCandidatos, String instanciaId, int start, int size) {
        boolean conAsignado = asignado != null && !asignado.isBlank();
        boolean conGrupos = gruposCandidatos != null && !gruposCandidatos.isEmpty();
        if (conAsignado == conGrupos) {
            throw new IllegalArgumentException("Se busca por usuario asignado o por grupos, uno de los dos.");
        }
        UriComponentsBuilder url = UriComponentsBuilder.fromHttpUrl(baseUrl + RUTA_TAREAS)
                .queryParam("processDefinitionKey", PROCESO);
        // Los valores van como variables {..} para que encode() los codifique completos: un "&" o
        // un "+" en un usuario o grupo no agrega ni cambia parámetros de la consulta.
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
        // Siempre se manda size: si no, Flowable pagina de a 10 y la web perdería tareas.
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

    /**
     * Busca una tarea activa por id. Devuelve vacío si no existe (404 del motor) o si es de otro
     * proceso, para que quien llama no distinga entre los dos casos.
     */
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
        // El id de la definición tiene la forma "<key>:<versión>:<id>"; así se filtra por key.
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

    /**
     * Devuelve todas las variables de la instancia, sin filtrar, como {name, type, value}.
     * Qué variables puede ver la web lo decide TareaService.
     */
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

    /**
     * Devuelve las actividades sin terminar de la instancia, incluidas las de ramas paralelas, de
     * la más vieja a la más nueva. Se usa el historial (y no las tareas) porque también incluye
     * la recepción de la nómina y la espera de la fecha de inicio.
     */
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

    /**
     * Reclama la tarea para el usuario: manda {"action":"claim","assignee":usuario}.
     * Recibe el id de la tarea y el usuario. Un claim sin assignee LIBERA la tarea en Flowable,
     * así que con el usuario vacío se corta antes con IllegalArgumentException.
     * Si la tarea ya la tiene otra persona, el 409 del motor sale como FlowableRespuestaException.
     */
    public void reclamarTarea(String tareaId, String usuario) {
        if (usuario == null || usuario.isBlank()) {
            throw new IllegalArgumentException("Un claim sin assignee libera la tarea: hace falta el usuario.");
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("action", "claim");
        cuerpo.put("assignee", usuario);
        accion("reclamar una tarea", tareaId, cuerpo);
    }

    /**
     * Completa la tarea con las variables recibidas: manda {"action":"complete","variables":[...]}.
     * Recibe el id de la tarea y las variables ya validadas por TareaService (puede ser una lista
     * vacía). Los errores de negocio del motor (400) salen como FlowableRespuestaException.
     */
    public void completarTarea(String tareaId, List<CompletarTareaRequest.Variable> variables) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (CompletarTareaRequest.Variable variable : variables) {
            // Se arma cada variable de nuevo: solo viajan nombre, tipo y valor.
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

    /** Manda una acción (claim, complete) a la tarea. Flowable responde 200 sin datos útiles. */
    private void accion(String descripcion, String tareaId, Map<String, Object> cuerpo) {
        URI uri = url(RUTA_TAREA, tareaId);
        consultar(descripcion, () -> restTemplate.postForEntity(uri, cuerpo, Void.class));
    }

    /**
     * Ejecuta una consulta y traduce los errores del motor (ver la descripción de la clase).
     * Recibe una descripción para el log (sin datos de la cuenta) y la consulta a ejecutar.
     */
    private static <T> T consultar(String descripcion, Supplier<T> consulta) {
        try {
            return consulta.get();
        } catch (HttpStatusCodeException exception) {
            HttpStatus estado = exception.getStatusCode();
            if (estado == HttpStatus.UNAUTHORIZED || estado == HttpStatus.FORBIDDEN) {
                // No es un error de la persona: el motor rechazó la cuenta del backend. Se registra
                // solo el estado y la operación; el usuario y la clave no se escriben nunca.
                LOG.error("Flowable rechazó la cuenta del backend ({}) al {}. Revisar FLOWABLE_USER y "
                        + "FLOWABLE_PASSWORD.", estado.value(), descripcion);
                throw new RestClientException("Flowable rechazó la cuenta del backend (" + estado.value() + ").");
            }
            throw new FlowableRespuestaException(estado.value(), exception.getResponseBodyAsByteArray(),
                    exception.getResponseHeaders() == null ? null : exception.getResponseHeaders().getContentType(),
                    exception);
        }
    }

    // encode() antes de expandir codifica el id completo: un "/" en el id no cambia la ruta.
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
