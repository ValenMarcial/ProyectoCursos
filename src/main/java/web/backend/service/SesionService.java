package web.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import web.backend.flowable.FlowableRestClient;
import web.backend.service.SesionException.Motivo;

/**
 * Reglas del inicio de sesión del personal: valida las credenciales contra Flowable y
 * arma el usuario que se guarda en la sesión.
 */
@Service
public class SesionService {
    /** Separa usuario y clave en Basic auth: un usuario que lo contenga sería ambiguo. */
    private static final String SEPARADOR_BASIC = ":";

    private final FlowableRestClient flowable;

    public SesionService(FlowableRestClient flowable) {
        this.flowable = flowable;
    }

    /**
     * Valida usuario y clave y devuelve el usuario con su nombre y grupos.
     * La clave solo se usa para validar: no queda en el resultado ni en este servicio.
     * Lanza SesionException con el motivo si los datos o las credenciales no sirven, o si
     * Flowable no responde.
     */
    public UsuarioSesion iniciar(String usuario, String clave) {
        if (usuario.contains(SEPARADOR_BASIC)) {
            throw new SesionException(Motivo.DATOS_INVALIDOS);
        }
        try {
            if (!flowable.credencialesValidas(usuario, clave)) {
                throw new SesionException(Motivo.CREDENCIALES_INVALIDAS);
            }
            return new UsuarioSesion(usuario, flowable.buscarNombre(usuario), flowable.buscarGrupos(usuario));
        } catch (RestClientException exception) {
            throw new SesionException(Motivo.FLOWABLE_NO_RESPONDE, exception);
        }
    }
}
