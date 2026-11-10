package web.backend.api.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

/** Datos del login. La clave no se loguea ni se devuelve: solo se usa para validar. */
public class IniciarSesionRequest {
    @NotBlank
    private String usuario;

    // NotEmpty y no NotBlank: una clave hecha de espacios sigue siendo una clave.
    @NotEmpty
    private String clave;

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
    }
}
