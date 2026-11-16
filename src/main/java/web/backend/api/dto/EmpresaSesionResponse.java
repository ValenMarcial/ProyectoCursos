package web.backend.api.dto;

/** Datos de la empresa con sesión iniciada. Nunca incluye la clave ni su hash. */
public class EmpresaSesionResponse {
    public final String usuario;
    public final String empresa;

    public EmpresaSesionResponse(String usuario, String empresa) {
        this.usuario = usuario;
        this.empresa = empresa;
    }
}
