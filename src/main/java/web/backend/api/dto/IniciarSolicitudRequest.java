package web.backend.api.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

public class IniciarSolicitudRequest {
    @NotNull
    @Positive
    private Long empresaId;

    @NotBlank
    @Size(max = 255)
    private String nombre;

    @NotBlank
    private String descripcion;

    private String plazoRecordatorio;

    public Long getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(Long empresaId) {
        this.empresaId = empresaId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getPlazoRecordatorio() {
        return plazoRecordatorio;
    }

    public void setPlazoRecordatorio(String plazoRecordatorio) {
        this.plazoRecordatorio = plazoRecordatorio;
    }
}
