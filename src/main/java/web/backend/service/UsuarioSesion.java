package web.backend.service;

import java.io.Serializable;
import java.util.List;

/**
 * Usuario autenticado que se guarda en la sesión HTTP.
 * A propósito no tiene la clave: solo identifica a la persona y sus grupos, para que el
 * backend aplique los permisos usando su propio cliente de Flowable.
 */
public class UsuarioSesion implements Serializable {
    // Serializable porque el contenedor puede persistir la sesión entre reinicios.
    private static final long serialVersionUID = 1L;

    private final String usuario;
    private final String nombre;
    private final List<String> grupos;

    public UsuarioSesion(String usuario, String nombre, List<String> grupos) {
        this.usuario = usuario;
        this.nombre = nombre;
        this.grupos = List.copyOf(grupos);
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
