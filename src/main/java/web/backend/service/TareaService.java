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

    /**
     * Variables de la instancia que puede leer la web (lista cerrada). Las que solo usan el
     * motor y los listeners (solicitudId, empresaId, cursoId, isViable, amountCursos,
     * recordatoriosEnviados, plazoRecordatorio, docenteSeleccionado, aulaAsignada, cancelada)
     * quedan afuera. Es una sola lista para todo el proceso: no distingue por tarea.
     */
    static final Set<String> VARIABLES_LECTURA = Set.of(
            // Solicitud de la empresa.
            "empresaNombre", "tematica", "descripcion", "contactoNombre", "contactoEmail",
            "contactoTelefono", "participantesEstimados", "fechasPreferidas",
            // Propuesta.
            "programa", "presupuesto", "fechaInicioPosible", "fechaFinPropuesta", "fechaInicio",
            // Recursos.
            "aulaFisica", "aulaVirtual", "equipamiento",
            // Lista de participantes.
            "participantes", "listaDefinitiva",
            // Próximas pantallas (informeResultados: nombre pendiente de acordar).
            "observaciones", "resultado", "informeResultados",
            // Cierre: código del error que cerró el curso (hoy SIN_AULAS), para mostrar el motivo.
            "motivoCierre");

    // Tipos de Flowable que acepta la lista de escritura.
    private static final String TIPO_TEXTO = "string";
    private static final String TIPO_ENTERO_LARGO = "long";
    private static final String TIPO_BOOLEANO = "boolean";
    private static final String TIPO_FECHA = "date";

    /**
     * Variables que la web puede escribir al completar, con su tipo de Flowable (lista cerrada,
     * igual a tiposVariables de contrato.js). Los tipos van fijos porque los listeners castean
     * (presupuesto a Long, las fechas a Date). Quedan afuera las que setean el motor o los
     * listeners (resultado, docenteSeleccionado, aulaAsignada, isViable, cancelada, motivoCierre).
     * Limitación: es una sola lista para todo el proceso, no por tarea; una tarea podría recibir
     * una variable que es de otra. El motor sigue validando cada formulario con sus listeners.
     */
    static final Map<String, String> VARIABLES_ESCRITURA = Map.of(
            // Propuesta.
            "programa", TIPO_TEXTO,
            "presupuesto", TIPO_ENTERO_LARGO,
            "fechaInicioPosible", TIPO_FECHA,
            "fechaFinPropuesta", TIPO_FECHA,
            // Recursos.
            "aulaFisica", TIPO_BOOLEANO,
            "aulaVirtual", TIPO_BOOLEANO,
            "equipamiento", TIPO_BOOLEANO,
            // Lista definitiva e informe (nombres pendientes de acordar).
            "listaDefinitiva", TIPO_TEXTO,
            "informeResultados", TIPO_TEXTO);

    private final FlowableTareaClient flowable;

    public TareaService(FlowableTareaClient flowable) {
        this.flowable = flowable;
    }

    /**
     * Lista las tareas del usuario según el alcance ("mias" o "grupo"), paginadas.
     * Recibe el usuario de la sesión, el alcance como texto y start/size (null = valor por defecto).
     * Devuelve la página de tareas. Lanza TareaException si el alcance o la página no son válidos,
     * o si Flowable no responde.
     */
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
        // Sin grupos no se llama a Flowable: con candidateGroups vacío ignoraría el filtro y traería todo.
        if (usuario.getGrupos().isEmpty()) {
            return new PaginaResponse<>(Collections.emptyList(), 0, inicio, tamanio);
        }
        return conFlowable(() -> flowable.buscarTareas(null, usuario.getGrupos(), null, inicio, tamanio));
    }

    /**
     * Devuelve una tarea si el usuario la puede ver: si está asignada a él, o si alguno de sus
     * grupos es candidato (aunque ya la haya tomado un compañero).
     * Recibe el usuario de la sesión y el id de la tarea. Si no existe o no la puede ver lanza
     * TareaException(TAREA_NO_ENCONTRADA), sin distinguir los dos casos.
     */
    public TareaResponse obtener(UsuarioSesion usuario, String tareaId) {
        return conFlowable(() -> {
            TareaResponse tarea = buscar(tareaId);
            if (esSuya(usuario, tarea) || esDeSuGrupo(usuario, tareaId)) {
                return tarea;
            }
            throw new TareaException(Motivo.TAREA_NO_ENCONTRADA);
        });
    }

    /**
     * Reclama la tarea para el usuario de la sesión. El assignee sale SIEMPRE de la sesión: el
     * controller no lee ningún cuerpo. Exige que la tarea sea de uno de sus grupos o ya suya.
     * Recibe el usuario de la sesión y el id de la tarea. Lanza TareaException: USUARIO_INVALIDO
     * (sin llamar a Flowable), TAREA_NO_ENCONTRADA o NO_AUTORIZADA. Si la tiene otra persona,
     * el 409 del motor sigue de largo como FlowableRespuestaException.
     */
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

    /**
     * Completa la tarea con las variables recibidas, solo si está asignada al usuario de la sesión.
     * Las variables se validan antes de consultar al motor contra VARIABLES_ESCRITURA (nombre,
     * tipo y valor); null equivale a ninguna.
     * Recibe el usuario de la sesión, el id de la tarea y las variables. Lanza TareaException:
     * USUARIO_INVALIDO, VARIABLE_INVALIDA, TAREA_NO_ENCONTRADA, NO_AUTORIZADA (ni suya ni de su
     * grupo) o NO_ASIGNADA (de su grupo pero sin reclamar). Los errores de negocio del motor
     * (400) siguen de largo como FlowableRespuestaException.
     */
    public void completar(UsuarioSesion usuario, String tareaId, List<CompletarTareaRequest.Variable> variables) {
        String yo = usuarioValido(usuario);
        List<CompletarTareaRequest.Variable> validas = validarEscritura(variables);
        conFlowable(() -> {
            TareaResponse tarea = buscar(tareaId);
            if (!yo.equals(tarea.getAssignee())) {
                // Solo en el caso de error se consultan los candidatos, para elegir el mensaje.
                throw new TareaException(esDeSuGrupo(usuario, tareaId) ? Motivo.NO_ASIGNADA : Motivo.NO_AUTORIZADA);
            }
            flowable.completarTarea(tareaId, validas);
            return null;
        });
    }

    /**
     * Devuelve las variables permitidas (VARIABLES_LECTURA) de una instancia, solo si el usuario
     * tiene una tarea activa en ella: asignada a él o sin asignar de uno de sus grupos.
     * Recibe el usuario de la sesión y el id de la instancia. Si no hay tarea suya lanza
     * TareaException(INSTANCIA_NO_ENCONTRADA): tampoco se revela si la instancia existe.
     */
    public List<VariableResponse> variables(UsuarioSesion usuario, String instanciaId) {
        return conFlowable(() -> {
            // Basta con saber si hay al menos una: se pide una sola tarea y se mira el total.
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

    /**
     * Indica si alguno de los grupos del usuario es candidato de la tarea (aunque ya la haya
     * tomado otra persona). Sin grupos no consulta a Flowable.
     */
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

    /**
     * Valida las variables a escribir: nombre en VARIABLES_ESCRITURA, el mismo tipo, un valor no
     * nulo que coincida con el tipo y sin nombres repetidos. Devuelve la lista (vacía si vino null).
     * Lanza TareaException(VARIABLE_INVALIDA) con el nombre de la primera variable rechazada.
     */
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

    /**
     * Indica si el valor JSON corresponde al tipo de Flowable. Las fechas van como texto ISO
     * (las convierte el motor); long acepta enteros, no decimales.
     */
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

    /**
     * Ejecuta una operación contra Flowable y traduce la falta de respuesta del motor (conexión
     * o cuenta del backend rechazada) a TareaException(FLOWABLE_NO_RESPONDE). Los errores que
     * respondió el motor (FlowableRespuestaException) siguen de largo para reenviarse tal cual.
     */
    private static <T> T conFlowable(Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (RestClientException exception) {
            throw new TareaException(Motivo.FLOWABLE_NO_RESPONDE, exception);
        }
    }
}
