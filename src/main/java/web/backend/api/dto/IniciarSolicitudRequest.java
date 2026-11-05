package web.backend.api.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

public class IniciarSolicitudRequest {
    @NotBlank
    @Size(max = 255)
    private String empresaNombre;

    @NotBlank
    @Size(max = 255)
    private String contactoNombre;

    @NotBlank
    @Email
    @Size(max = 320)
    private String contactoEmail;

    @Size(max = 64)
    private String contactoTelefono;

    @NotBlank
    @Size(max = 255)
    private String tematica;

    @NotBlank
    private String descripcion;

    @NotNull
    @Positive
    private Integer participantesEstimados;

    @NotBlank
    @Size(max = 500)
    private String fechasPreferidas;

    private String plazoRecordatorio;

    public String getEmpresaNombre() {
        return empresaNombre;
    }

    public void setEmpresaNombre(String empresaNombre) {
        this.empresaNombre = empresaNombre;
    }

    public String getContactoNombre() {
        return contactoNombre;
    }

    public void setContactoNombre(String contactoNombre) {
        this.contactoNombre = contactoNombre;
    }

    public String getContactoEmail() {
        return contactoEmail;
    }

    public void setContactoEmail(String contactoEmail) {
        this.contactoEmail = contactoEmail;
    }

    public String getContactoTelefono() {
        return contactoTelefono;
    }

    public void setContactoTelefono(String contactoTelefono) {
        this.contactoTelefono = contactoTelefono;
    }

    public String getTematica() {
        return tematica;
    }

    public void setTematica(String tematica) {
        this.tematica = tematica;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Integer getParticipantesEstimados() {
        return participantesEstimados;
    }

    public void setParticipantesEstimados(Integer participantesEstimados) {
        this.participantesEstimados = participantesEstimados;
    }

    public String getFechasPreferidas() {
        return fechasPreferidas;
    }

    public void setFechasPreferidas(String fechasPreferidas) {
        this.fechasPreferidas = fechasPreferidas;
    }

    public String getPlazoRecordatorio() {
        return plazoRecordatorio;
    }

    public void setPlazoRecordatorio(String plazoRecordatorio) {
        this.plazoRecordatorio = plazoRecordatorio;
    }
}
