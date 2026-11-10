package web.backend.api.dto;

import java.util.List;

/** Usuario de la sesión tal como lo recibe la web: {usuario, nombre, grupos}. */
public class SesionResponse {
    private final String usuario;
    private final String nombre;
    private final List<String> grupos;

    public SesionResponse(String usuario, String nombre, List<String> grupos) {
        this.usuario = usuario;
        this.nombre = nombre;
        this.grupos = grupos;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getNombre() {
        return nombre;
    }

    public List<String> getGrupos() {
        return grupos;
    }
}
