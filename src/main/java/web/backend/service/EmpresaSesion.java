package web.backend.service;

import java.io.Serializable;

/**
 * Empresa autenticada que se guarda en la sesión HTTP. Es otro tipo que UsuarioSesion a propósito:
 * con una sesión de empresa no se llega a la API del personal, ni al revés (ver SesionInterceptor).
 * Todas las consultas de la empresa se filtran por empresaId, que sale de acá y nunca del pedido.
 */
public class EmpresaSesion implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long empresaId;
    private final String usuario;
    private final String empresa;

    public EmpresaSesion(long empresaId, String usuario, String empresa) {
        this.empresaId = empresaId;
        this.usuario = usuario;
        this.empresa = empresa;
    }

    public long getEmpresaId() {
        return empresaId;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getEmpresa() {
        return empresa;
    }
}
