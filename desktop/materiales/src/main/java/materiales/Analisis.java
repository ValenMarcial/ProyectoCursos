package materiales;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.history.HistoricTaskInstance;

/** Consultas del punto 4.3 del enunciado, hechas sobre el historial de Flowable. */
public class Analisis {
    static final String PROCESO = "process-proyecto-curso";

    /** Ids de las actividades del BPMN que delimitan cada rama de "Preparar curso". */
    static final String RECIBIR_NOMINA = "sid-40B80C57-5A63-4D5C-A43B-710C8B140370";
    static final String LISTA_DEFINITIVA = "sid-67285B9F-672D-4038-A765-E67245B387D1";
    static final String PREPARAR_MATERIALES = "sid-809C7C67-70EC-4018-BBF7-6A27DD12D4BB";
    static final String EVALUAR_RECURSOS = "sid-F3381B5B-C506-4AC2-A09D-747447EFB31B";
    static final List<String> ACCIONES_RECURSOS = List.of(
            "sid-065649F8-9F01-4C34-B290-C37474CC479D",   // Reservar aula
            "sid-C3399A6A-261E-4D23-9AF8-F9F58D4A81CB",   // Solicitar aula virtual
            "sid-6AAC5E2F-BF84-4C7C-8963-7D1F6CBEF790");  // Contratar equipamiento
    /** Fin del subproceso "Preparar curso", después de la unión "Preparación completa". La cancelación nunca llega acá. */
    static final String FIN_PREPARACION = "sid-85D22149-CBE0-424A-9E0D-9C03CFB275CD";

    /** Resultados que reconoce el enunciado, en el orden en que se muestran. */
    static final List<String> RESULTADOS = List.of("finalizado", "cancelado", "rechazado", "no_viable",
            "cerrado_por_falta_de_respuesta");
    static final String SIN_RESULTADO = "sin_resultado";

    private final HistoryService historial;

    public Analisis(HistoryService historial) {
        this.historial = historial;
    }

    /** 4.3.1: cantidad de solicitudes terminadas por resultado; el porcentaje es sobre el total de terminadas. */
    public Map<String, Integer> resultados() {
        Map<String, Integer> cuenta = new LinkedHashMap<>();
        RESULTADOS.forEach(r -> cuenta.put(r, 0));
        List<HistoricProcessInstance> terminadas = historial.createHistoricProcessInstanceQuery()
                .processDefinitionKey(PROCESO).finished().includeProcessVariables().list();
        for (HistoricProcessInstance i : terminadas) {
            Object r = i.getProcessVariables().get("resultado");
            cuenta.merge(r == null ? SIN_RESULTADO : r.toString(), 1, Integer::sum);
        }
        if (cuenta.getOrDefault(SIN_RESULTADO, 0) == 0) {
            cuenta.remove(SIN_RESULTADO);
        }
        return cuenta;
    }

    public static double porcentaje(int parte, Map<String, Integer> resultados) {
        int total = resultados.values().stream().mapToInt(Integer::intValue).sum();
        return total == 0 ? 0 : parte * 100.0 / total;
    }

    /** 4.3.2: duración promedio (ms) de cada rama y cantidad de cursos considerados. */
    public Duraciones duraciones() {
        List<Long> inscripciones = new ArrayList<>();
        List<Long> materiales = new ArrayList<>();
        List<Long> recursos = new ArrayList<>();
        // Solo cursos que completaron la preparación: llegaron al fin del subproceso. Una preparación
        // interrumpida por cancelación nunca llega ahí, aunque alguna rama ya hubiera terminado.
        Set<String> instancias = new LinkedHashSet<>();
        for (HistoricActivityInstance fin : historial.createHistoricActivityInstanceQuery()
                .activityId(FIN_PREPARACION).finished().list()) {
            instancias.add(fin.getProcessInstanceId());
        }
        for (String instancia : instancias) {
            Map<String, List<HistoricActivityInstance>> porActividad = new TreeMap<>();
            for (HistoricActivityInstance a : historial.createHistoricActivityInstanceQuery()
                    .processInstanceId(instancia).list()) {
                porActividad.computeIfAbsent(a.getActivityId(), k -> new ArrayList<>()).add(a);
            }
            agregar(inscripciones, inicio(porActividad, RECIBIR_NOMINA), fin(porActividad, List.of(LISTA_DEFINITIVA)));
            agregar(materiales, inicio(porActividad, PREPARAR_MATERIALES), fin(porActividad, List.of(PREPARAR_MATERIALES)));
            agregar(recursos, inicio(porActividad, EVALUAR_RECURSOS), fin(porActividad, ACCIONES_RECURSOS));
        }
        return new Duraciones(promedio(inscripciones), promedio(materiales), promedio(recursos),
                inscripciones.size(), materiales.size(), recursos.size());
    }

    private static Date inicio(Map<String, List<HistoricActivityInstance>> a, String id) {
        return a.getOrDefault(id, List.of()).stream().map(HistoricActivityInstance::getStartTime)
                .min(Comparator.naturalOrder()).orElse(null);
    }

    /** Fin de una rama: el último fin entre sus actividades (para recursos, la última acción elegida). */
    private static Date fin(Map<String, List<HistoricActivityInstance>> a, List<String> ids) {
        Date ultimo = null;
        for (String id : ids) {
            for (HistoricActivityInstance x : a.getOrDefault(id, List.of())) {
                if (x.getEndTime() != null && (ultimo == null || x.getEndTime().after(ultimo))) {
                    ultimo = x.getEndTime();
                }
            }
        }
        return ultimo;
    }

    private static void agregar(List<Long> lista, Date desde, Date hasta) {
        if (desde != null && hasta != null) {
            lista.add(hasta.getTime() - desde.getTime());
        }
    }

    private static Double promedio(List<Long> valores) {
        return valores.isEmpty() ? null : valores.stream().mapToLong(Long::longValue).average().getAsDouble();
    }

    /** 4.3.3: tareas de usuario completadas por cada persona (no cuenta pendientes ni canceladas). */
    public Map<String, Integer> tareasPorUsuario() {
        Map<String, Integer> cuenta = new TreeMap<>();
        for (HistoricTaskInstance t : historial.createHistoricTaskInstanceQuery()
                .processDefinitionKey(PROCESO).finished().list()) {
            // Una tarea completada termina sin motivo de borrado; una cancelada (por ejemplo, por la cancelación
            // del curso) también tiene fin, pero con un motivo.
            if (t.getAssignee() != null && t.getDeleteReason() == null) {
                cuenta.merge(t.getAssignee(), 1, Integer::sum);
            }
        }
        return cuenta;
    }

    /** Informe completo en texto, para la consola. */
    public String informe() {
        StringBuilder s = new StringBuilder();
        Map<String, Integer> r = resultados();
        int total = r.values().stream().mapToInt(Integer::intValue).sum();
        s.append("4.3.1 Resultados de las solicitudes (terminadas: ").append(total).append(")\n");
        r.forEach((k, v) -> s.append(String.format("  %-34s %4d  %6.1f %%%n", k, v, porcentaje(v, r))));
        Duraciones d = duraciones();
        s.append("\n4.3.2 Duración promedio de la preparación paralela\n");
        s.append(String.format("  %-14s %-14s (%d cursos)%n", "Inscripciones", Duraciones.formato(d.inscripciones), d.cursosInscripciones));
        s.append(String.format("  %-14s %-14s (%d cursos)%n", "Materiales", Duraciones.formato(d.materiales), d.cursosMateriales));
        s.append(String.format("  %-14s %-14s (%d cursos)%n", "Recursos", Duraciones.formato(d.recursos), d.cursosRecursos));
        s.append("\n4.3.3 Tareas completadas por persona\n");
        Map<String, Integer> t = tareasPorUsuario();
        if (t.isEmpty()) {
            s.append("  (ninguna)\n");
        }
        t.forEach((k, v) -> s.append(String.format("  %-24s %4d%n", k, v)));
        return s.toString();
    }

    /** Duraciones promedio en milisegundos; null si no hay cursos que cumplan. */
    public static final class Duraciones {
        public final Double inscripciones;
        public final Double materiales;
        public final Double recursos;
        public final int cursosInscripciones;
        public final int cursosMateriales;
        public final int cursosRecursos;

        Duraciones(Double inscripciones, Double materiales, Double recursos, int ci, int cm, int cr) {
            this.inscripciones = inscripciones;
            this.materiales = materiales;
            this.recursos = recursos;
            this.cursosInscripciones = ci;
            this.cursosMateriales = cm;
            this.cursosRecursos = cr;
        }

        public static String formato(Double ms) {
            if (ms == null) {
                return "sin datos";
            }
            long seg = Math.round(ms / 1000);
            return String.format("%d h %02d min %02d s", seg / 3600, (seg % 3600) / 60, seg % 60);
        }
    }
}
