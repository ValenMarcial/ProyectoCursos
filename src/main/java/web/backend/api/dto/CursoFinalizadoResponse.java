package web.backend.api.dto;

import java.time.LocalDate;

/** Curso finalizado visto por Administración, con la empresa y si tiene el PDF del informe. */
public class CursoFinalizadoResponse {
    public final long id;
    public final String nombre;
    public final String empresa;
    public final LocalDate fechaInicio;
    public final LocalDate fechaFin;
    public final boolean informeDisponible;

    public CursoFinalizadoResponse(long id, String nombre, String empresa, LocalDate fechaInicio,
            LocalDate fechaFin, boolean informeDisponible) {
        this.id = id;
        this.nombre = nombre;
        this.empresa = empresa;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.informeDisponible = informeDisponible;
    }
}
