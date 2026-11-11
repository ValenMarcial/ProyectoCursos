package web.backend.api.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Curso visto por la empresa. En el listado "actividades" no viaja; en el detalle es la lista de
 * actividades en curso (vacía si el proceso ya terminó).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CursoEmpresaResponse {
    public final long id;
    public final String nombre;
    public final String descripcion;
    /** Código de cursos.estado: en_preparacion, confirmado, pendiente_de_inicio, en_desarrollo, ... */
    public final String estado;
    public final LocalDate fechaInicio;
    public final LocalDate fechaFin;
    public final List<ActividadResponse> actividades;

    public CursoEmpresaResponse(long id, String nombre, String descripcion, String estado,
            LocalDate fechaInicio, LocalDate fechaFin, List<ActividadResponse> actividades) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.estado = estado;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.actividades = actividades;
    }

    /** Mismo curso con sus actividades en curso. */
    public CursoEmpresaResponse conActividades(List<ActividadResponse> lista) {
        return new CursoEmpresaResponse(id, nombre, descripcion, estado, fechaInicio, fechaFin, lista);
    }
}
