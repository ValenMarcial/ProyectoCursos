package web.backend.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

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
            Optional<TareaResponse> tarea = flowable.obtenerTarea(tareaId);
            if (tarea.isEmpty()) {
                throw new TareaException(Motivo.TAREA_NO_ENCONTRADA);
            }
            if (usuario.getUsuario().equals(tarea.get().getAssignee())) {
                return tarea.get();
            }
            // Si el usuario no tiene grupos no hace falta consultar los candidatos.
            if (!usuario.getGrupos().isEmpty()
                    && !Collections.disjoint(usuario.getGrupos(), flowable.gruposCandidatos(tareaId))) {
                return tarea.get();
            }
            throw new TareaException(Motivo.TAREA_NO_ENCONTRADA);
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
