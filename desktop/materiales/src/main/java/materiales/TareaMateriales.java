package materiales;

import java.util.Date;

/** Una tarea "Preparar materiales" tal como la ve el administrativo identificado. */
public final class TareaMateriales {
    public final String id;
    public final String nombre;
    public final String empresa;
    public final String tematica;
    public final Date creada;
    /** true si ya está asignada al usuario; false si solo está disponible para su grupo. */
    public final boolean propia;

    public TareaMateriales(String id, String nombre, String empresa, String tematica, Date creada, boolean propia) {
        this.id = id;
        this.nombre = nombre;
        this.empresa = empresa;
        this.tematica = tematica;
        this.creada = creada;
        this.propia = propia;
    }

    public String estado() {
        return propia ? "Asignada a mí" : "Disponible para mi grupo";
    }
}
