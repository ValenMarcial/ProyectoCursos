package web.backend.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import web.backend.api.dto.MensajeErrorResponse;
import web.backend.service.EmpresaSesion;
import web.backend.service.SesionException.Motivo;
import web.backend.service.UsuarioSesion;

/**
 * Corta con 401 los pedidos que llegan sin sesión a la API (todo /api). Solo pasan sin sesión
 * los pedidos públicos, por método y ruta exactos: los dos logins y el alta pública de solicitudes.
 * Hay dos sesiones que no se mezclan: /api/empresa/** pide una sesión de empresa y el resto de la
 * API una del personal. Una empresa logueada no llega a las tareas, y el personal no ve "Mis cursos".
 * Qué rutas pasan por acá lo decide SesionWebConfig. Flowable no controla quién usa sus
 * tareas, así que este es el primer filtro: sin sesión no hay forma de llegar al motor.
 */
public class SesionInterceptor implements HandlerInterceptor {
    private static final String BARRA = "/";
    /** Ruta del alta pública; tiene que coincidir con el @RequestMapping de SolicitudController. */
    static final String RUTA_SOLICITUDES = "/api/solicitudes";

    /**
     * Pedidos que pasan sin sesión, como "MÉTODO ruta". Es por método a propósito: GET, PUT o
     * DELETE sobre las mismas rutas exigen sesión.
     * - POST /api/sesion: el login (GET y DELETE de esa ruta piden sesión).
     * - POST /api/solicitudes: el alta de la web pública y de las empresas, sin login.
     * - POST /api/empresa/sesion: el login de las empresas.
     */
    private static final Set<String> PEDIDOS_PUBLICOS = Set.of(
            pedido(HttpMethod.POST, SesionController.RUTA),
            pedido(HttpMethod.POST, RUTA_SOLICITUDES),
            pedido(HttpMethod.POST, EmpresaController.RUTA_SESION));

    private final ObjectMapper objectMapper;

    public SesionInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // El preflight de CORS (OPTIONS) lo manda el navegador sin cookies antes del POST público
        // desde otro origen. Spring lo responde solo con los encabezados de CORS, sin llegar al
        // controller, así que dejarlo pasar no expone nada.
        if (CorsUtils.isPreFlightRequest(request) || esPublico(request) || tieneSesion(request)) {
            return true;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), new MensajeErrorResponse(Motivo.SIN_SESION.getMensaje()));
        return false;
    }

    /** Indica si el pedido es uno de PEDIDOS_PUBLICOS (método y ruta exactos). */
    private static boolean esPublico(HttpServletRequest request) {
        return PEDIDOS_PUBLICOS.contains(request.getMethod() + " " + ruta(request));
    }

    private static String ruta(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        // Spring acepta la barra final en la ruta del controller, así que acá también.
        if (ruta.endsWith(BARRA)) {
            ruta = ruta.substring(0, ruta.length() - 1);
        }
        return ruta;
    }

    private static String pedido(HttpMethod metodo, String ruta) {
        return metodo.name() + " " + ruta;
    }

    // Una sesión sin usuario (por ejemplo, creada por otra parte del sitio) no alcanza, y cada parte
    // de la API pide la suya.
    private static boolean tieneSesion(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion == null) {
            return false;
        }
        String ruta = ruta(request);
        if (ruta.equals(EmpresaController.RUTA) || ruta.startsWith(EmpresaController.RUTA + BARRA)) {
            return sesion.getAttribute(EmpresaController.ATRIBUTO_EMPRESA) instanceof EmpresaSesion;
        }
        return sesion.getAttribute(SesionController.ATRIBUTO_USUARIO) instanceof UsuarioSesion;
    }
}
