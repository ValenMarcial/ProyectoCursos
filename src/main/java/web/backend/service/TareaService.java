package web.backend.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import web.backend.api.dto.CompletarTareaRequest;
import web.backend.api.dto.PaginaResponse;
import web.backend.api.dto.TareaResponse;
import web.backend.api.dto.VariableResponse;
import web.backend.flowable.FlowableTareaClient;
import web.backend.service.TareaException.Motivo;

/**
 * Reglas de acceso a las tareas del proceso. Flowable deja ver y usar cualquier tarea con la
 * cuenta admin, así que los permisos se aplican acá con el usuario y los grupos de la sesión.
 * Es genérico a propósito: sirve a la bandeja, a las pantallas de administración y a docentes.
 */
@Service
public class TareaService {
    static final int INICIO_POR_DEFECTO = 0;
    static final int TAMANIO_POR_DEFECTO = 50;
    static final int TAMANIO_MAXIMO = 100;

    /** Variables de la instancia que puede leer la web. */
    static final Set<String> VARIABLES_LECTURA = Set.of(
            // Solicitud de la empresa.
            "empresaNombre", "tematica", "descripcion", "contactoNombre", "contactoEmail",
            "contactoTelefono", "participantesEstimados", "fechasPreferidas",
            // Propuesta.
            "programa", "presupuesto", "fechaInicioPosible", "fechaFinPropuesta", "fechaInicio",
            "versionPropuesta",
            // Recursos.
            "aulaFisica", "aulaVirtual", "equipamiento",
            // Lista de participantes.
            "participantes", "listaDefinitiva",
            // Informe.
            "observaciones", "resultado", "informeResultados", "informeAdjunto", "rutaInforme",
            // Cierre.
            "motivoCierre");

    // Tipos de Flowable que acepta la lista de escritura.
    private static final String TIPO_TEXTO = "string";
    private static final String TIPO_ENTERO_LARGO = "long";
    private static final String TIPO_BOOLEANO = "boolean";
    private static final String TIPO_FECHA = "date";

    /** Variables que la web puede escribir al completar, con su tipo de Flowable. */
    static final Map<String, String> VARIABLES_ESCRITURA = Map.ofEntries(
            // Propuesta.
            Map.entry("programa", TIPO_TEXTO),
            Map.entry("presupuesto", TIPO_ENTERO_LARGO),
            Map.entry("fechaInicioPosible", TIPO_FECHA),
            Map.entry("fechaFinPropuesta", TIPO_FECHA),
            // Recursos.
            Map.entry("aulaFisica", TIPO_BOOLEANO),
            Map.entry("aulaVirtual", TIPO_BOOLEANO),
            Map.entry("equipamiento", TIPO_BOOLEANO),
            // Lista definitiva e informe.
            Map.entry("listaDefinitiva", TIPO_TEXTO),
            Map.entry("informeResultados", TIPO_TEXTO),
            Map.entry("informeAdjunto", TIPO_BOOLEANO),
            Map.entry("rutaInforme", TIPO_TEXTO),
            // Definir Docente.
            Map.entry("docenteSeleccionado", TIPO_TEXTO));

    private final FlowableTareaClient flowable;

    public TareaService(FlowableTareaClient flowable) {
        this.flowable = flowable;
    }

    /** Lista las tareas del usuario según el alcance ("mias" o "grupo"), paginadas. */
    public PaginaResponse<TareaResponse> listar(UsuarioSesion usuario, String alcance, Integer start, Integer size) {
        Alcance tipo = Alcance.desde(alcance);
        int inicio = start == null ? INICIO_POR_DEFECTO : start;
        int tamanio = size == null ? TAMANIO_POR_DEFECTO : size;
        if (inicio < 0 || tamanio < 1 || tamanio > TAMANIO_MAXIMO) {
            throw new TareaException(Motivo.PAGINA_INVALIDA);
        }
        if (tipo == Alcance.MIAS) {
            return conFlowable(() -> flowable.buscarTareas(usuario.getUsuario(), null, null, inicio, tamanio));
        }
        // Sin grupos no hay tareas de grupo
        if (usuario.getGrupos().isEmpty()) {
            return new PaginaResponse<>(Collections.emptyList(), 0, inicio, tamanio);
        }
        return conFlowable(() -> flowable.buscarTareas(null, usuario.getGrupos(), null, inicio, tamanio));
    }

    /** Devuelve una tarea si es del usuario o de uno de sus grupos. */
    public TareaResponse obtener(UsuarioSesion usuario, String tareaId) {
        return conFlowable(() -> {
            TareaResponse tarea = buscar(tareaId);
            if (esSuya(usuario, tarea) || esDeSuGrupo(usuario, tareaId)) {
                return tarea;
            }
            throw new TareaException(Motivo.TAREA_NO_ENCONTRADA);
        });
    }

    /** Reclama la tarea para el usuario de la sesión, si es de uno de sus grupos. */
    public void reclamar(UsuarioSesion usuario, String tareaId) {
        String yo = usuarioValido(usuario);
        conFlowable(() -> {
            TareaResponse tarea = buscar(tareaId);
            // Ya es suya: no hay nada que cambiar en el motor.
            if (yo.equals(tarea.getAssignee())) {
                return null;
            }
            if (!esDeSuGrupo(usuario, tareaId)) {
                throw new TareaException(Motivo.NO_AUTORIZADA);
            }
            flowable.reclamarTarea(tareaId, yo);
            return null;
        });
    }

    /** Completa la tarea asignada al usuario de la sesión con las variables recibidas. */
    public void completar(UsuarioSesion usuario, String tareaId, List<CompletarTareaRequest.Variable> variables) {
        String yo = usuarioValido(usuario);
        List<CompletarTareaRequest.Variable> validas = validarEscritura(variables);
        conFlowable(() -> {
            exigirAsignada(usuario, yo, tareaId);
            flowable.completarTarea(tareaId, validas);
            return null;
        });
    }

    /** Devuelve la tarea solo si está asignada al usuario de la sesión. */
    public TareaResponse tareaAsignada(UsuarioSesion usuario, String tareaId) {
        String yo = usuarioValido(usuario);
        return conFlowable(() -> exigirAsignada(usuario, yo, tareaId));
    }

    /** Busca la tarea y exige que esté asignada al usuario. */
    private TareaResponse exigirAsignada(UsuarioSesion usuario, String yo, String tareaId) {
        TareaResponse tarea = buscar(tareaId);
        if (!yo.equals(tarea.getAssignee())) {
            throw new TareaException(esDeSuGrupo(usuario, tareaId) ? Motivo.NO_ASIGNADA : Motivo.NO_AUTORIZADA);
        }
        return tarea;
    }

    /** Devuelve las variables permitidas de una instancia en la que el usuario tiene una tarea. */
    public List<VariableResponse> variables(UsuarioSesion usuario, String instanciaId) {
        return conFlowable(() -> {
            boolean tieneTarea = flowable.buscarTareas(usuario.getUsuario(), null, instanciaId, 0, 1).getTotal() > 0
                    || (!usuario.getGrupos().isEmpty()
                            && flowable.buscarTareas(null, usuario.getGrupos(), instanciaId, 0, 1).getTotal() > 0);
            if (!tieneTarea) {
                throw new TareaException(Motivo.INSTANCIA_NO_ENCONTRADA);
            }
            List<VariableResponse> permitidas = new ArrayList<>();
            for (VariableResponse variable : flowable.variablesDeInstancia(instanciaId)) {
                if (VARIABLES_LECTURA.contains(variable.getName())) {
                    permitidas.add(variable);
                }
            }
            return permitidas;
        });
    }

    /** Devuelve una variable de texto de la instancia, sin controlar permisos. */
    public Optional<String> textoDeInstancia(String instanciaId, String nombre) {
        return conFlowable(() -> flowable.variablesDeInstancia(instanciaId).stream()
                .filter(variable -> nombre.equals(variable.getName()) && variable.getValue() instanceof String)
                .map(variable -> (String) variable.getValue())
                .findFirst());
    }

    /** Busca la tarea del proceso o lanza TAREA_NO_ENCONTRADA si no existe. */
    private TareaResponse buscar(String tareaId) {
        Optional<TareaResponse> tarea = flowable.obtenerTarea(tareaId);
        if (tarea.isEmpty()) {
            throw new TareaException(Motivo.TAREA_NO_ENCONTRADA);
        }
        return tarea.get();
    }

    /** Indica si la tarea está asignada al usuario de la sesión. */
    private static boolean esSuya(UsuarioSesion usuario, TareaResponse tarea) {
        return usuario.getUsuario().equals(tarea.getAssignee());
    }

    /** Indica si alguno de los grupos del usuario es candidato de la tarea. */
    private boolean esDeSuGrupo(UsuarioSesion usuario, String tareaId) {
        return !usuario.getGrupos().isEmpty()
                && !Collections.disjoint(usuario.getGrupos(), flowable.gruposCandidatos(tareaId));
    }

    /** Devuelve el usuario de la sesión, o lanza USUARIO_INVALIDO si está vacío. */
    private static String usuarioValido(UsuarioSesion usuario) {
        String nombre = usuario == null ? null : usuario.getUsuario();
        if (nombre == null || nombre.isBlank()) {
            throw new TareaException(Motivo.USUARIO_INVALIDO);
        }
        return nombre;
    }

    /** Valida las variables a escribir contra VARIABLES_ESCRITURA. */
    private static List<CompletarTareaRequest.Variable> validarEscritura(List<CompletarTareaRequest.Variable> variables) {
        if (variables == null) {
            return Collections.emptyList();
        }
        Set<String> vistas = new HashSet<>();
        for (CompletarTareaRequest.Variable variable : variables) {
            String nombre = variable == null ? null : variable.getName();
            String tipo = nombre == null ? null : VARIABLES_ESCRITURA.get(nombre);
            if (tipo == null || !tipo.equals(variable.getType()) || !valorDelTipo(tipo, variable.getValue())
                    || !vistas.add(nombre)) {
                throw new TareaException(Motivo.VARIABLE_INVALIDA, String.valueOf(nombre));
            }
        }
        return variables;
    }

    /** Indica si el valor JSON corresponde al tipo de Flowable. */
    private static boolean valorDelTipo(String tipo, Object valor) {
        switch (tipo) {
            case TIPO_TEXTO:
            case TIPO_FECHA:
                return valor instanceof String;
            case TIPO_ENTERO_LARGO:
                return valor instanceof Integer || valor instanceof Long;
            case TIPO_BOOLEANO:
                return valor instanceof Boolean;
            default:
                return false;
        }
    }

    /** Ejecuta una operación contra Flowable y traduce la falta de respuesta del motor. */
    private static <T> T conFlowable(Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (RestClientException exception) {
            throw new TareaException(Motivo.FLOWABLE_NO_RESPONDE, exception);
        }
    }
}
