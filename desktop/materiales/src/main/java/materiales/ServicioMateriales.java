package materiales;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.idm.api.Group;
import org.flowable.idm.api.IdmIdentityService;
import org.flowable.task.api.Task;

/** Lo que puede hacer un administrativo con las tareas "Preparar materiales", usando la API Java del motor. */
public class ServicioMateriales {
    /** El BPMN la nombra "Prepara materiales"; el enunciado dice "Preparar materiales". Se aceptan las dos. */
    static final String PATRON_NOMBRE = "prepar% materiales";
    static final String GRUPO_ADMINISTRACION = "Administracion";

    private final TaskService tareas;
    private final RuntimeService ejecuciones;
    private final IdmIdentityService identidades;

    public ServicioMateriales(TaskService tareas, RuntimeService ejecuciones, IdmIdentityService identidades) {
        this.tareas = tareas;
        this.ejecuciones = ejecuciones;
        this.identidades = identidades;
    }

    /** Devuelve el usuario si el ID y la contraseña son los de Flowable y pertenece a Administración; si no, null. */
    public Usuario identificar(String id, String clave) {
        if (id == null || id.isBlank() || clave == null || !identidades.checkPassword(id.trim(), clave)) {
            return null;
        }
        List<String> grupos = new ArrayList<>();
        for (Group g : identidades.createGroupQuery().groupMember(id.trim()).list()) {
            grupos.add(g.getId());
        }
        return new Usuario(id.trim(), grupos);
    }

    /** Tareas de materiales asignadas al usuario, más las que puede reclamar por su grupo. */
    public List<TareaMateriales> consultar(Usuario usuario) {
        Map<String, TareaMateriales> resultado = new LinkedHashMap<>();
        for (Task t : tareas.createTaskQuery().taskAssignee(usuario.id).taskNameLikeIgnoreCase(PATRON_NOMBRE)
                .active().list()) {
            resultado.put(t.getId(), aVista(t, true));
        }
        if (!usuario.grupos.isEmpty()) {
            for (Task t : tareas.createTaskQuery().taskCandidateGroupIn(usuario.grupos)
                    .taskNameLikeIgnoreCase(PATRON_NOMBRE).active().list()) {
                resultado.putIfAbsent(t.getId(), aVista(t, false));
            }
        }
        List<TareaMateriales> lista = new ArrayList<>(resultado.values());
        lista.sort(Comparator.comparing((TareaMateriales t) -> t.creada));
        return lista;
    }

    /** Reclama una tarea disponible para el grupo del usuario. */
    public void reclamar(Usuario usuario, String tareaId) {
        Task t = buscar(tareaId);
        if (t.getAssignee() != null) {
            throw new IllegalStateException(t.getAssignee().equals(usuario.id)
                    ? "La tarea ya es tuya."
                    : "La tarea ya fue reclamada por " + t.getAssignee() + ".");
        }
        if (tareas.createTaskQuery().taskId(tareaId).taskCandidateGroupIn(usuario.gruposOVacio()).count() == 0) {
            throw new IllegalStateException("La tarea no está disponible para tu grupo.");
        }
        tareas.claim(tareaId, usuario.id);
    }

    /** Completa una tarea. Solo puede hacerlo quien la tiene asignada (hay que reclamarla antes). */
    public void completar(Usuario usuario, String tareaId) {
        Task t = buscar(tareaId);
        if (t.getAssignee() == null) {
            throw new IllegalStateException("Primero hay que reclamar la tarea.");
        }
        if (!t.getAssignee().equals(usuario.id)) {
            throw new IllegalStateException("La tarea está asignada a " + t.getAssignee() + ".");
        }
        tareas.complete(tareaId);
    }

    private Task buscar(String tareaId) {
        Task t = tareas.createTaskQuery().taskId(tareaId).taskNameLikeIgnoreCase(PATRON_NOMBRE).singleResult();
        if (t == null) {
            throw new IllegalStateException("La tarea ya no existe o no es de preparar materiales.");
        }
        return t;
    }

    private TareaMateriales aVista(Task t, boolean propia) {
        Map<String, Object> vars = ejecuciones.getVariables(t.getProcessInstanceId());
        return new TareaMateriales(t.getId(), t.getName(), texto(vars, "empresaNombre"), texto(vars, "tematica"),
                t.getCreateTime(), propia);
    }

    private static String texto(Map<String, Object> vars, String nombre) {
        Object valor = vars.get(nombre);
        return valor == null ? "-" : valor.toString();
    }

    /** Usuario identificado con los grupos de Flowable a los que pertenece. */
    public static final class Usuario {
        public final String id;
        public final List<String> grupos;

        public Usuario(String id, List<String> grupos) {
            this.id = id;
            this.grupos = grupos;
        }

        List<String> gruposOVacio() {
            return grupos.isEmpty() ? List.of("-") : grupos;
        }

        public boolean esAdministrativo() {
            return grupos.contains(GRUPO_ADMINISTRACION);
        }
    }
}
