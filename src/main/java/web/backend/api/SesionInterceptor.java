package web.backend.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import web.backend.api.dto.MensajeErrorResponse;
import web.backend.service.SesionException.Motivo;
import web.backend.service.UsuarioSesion;

/**
 * Corta con 401 los pedidos que llegan sin sesión a la API (todo /api salvo el login y
 * el alta pública). Qué rutas pasan por acá lo decide SesionWebConfig. Flowable no controla quién usa sus
 * tareas, así que este es el primer filtro: sin sesión no hay forma de llegar al motor.
 */
public class SesionInterceptor implements HandlerInterceptor {
    private static final String BARRA = "/";

    private final ObjectMapper objectMapper;

    public SesionInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (esLogin(request) || tieneSesion(request)) {
            return true;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), new MensajeErrorResponse(Motivo.SIN_SESION.getMensaje()));
        return false;
    }

    // POST /api/sesion es el login: comparte ruta con GET y DELETE, pero tiene que andar sin sesión.
    private static boolean esLogin(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        // Spring acepta la barra final en la ruta del controller, así que acá también.
        if (ruta.endsWith(BARRA)) {
            ruta = ruta.substring(0, ruta.length() - 1);
        }
        return HttpMethod.POST.matches(request.getMethod()) && SesionController.RUTA.equals(ruta);
    }

    // Una sesión sin usuario (por ejemplo, creada por otra parte del sitio) no alcanza.
    private static boolean tieneSesion(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        return sesion != null && sesion.getAttribute(SesionController.ATRIBUTO_USUARIO) instanceof UsuarioSesion;
    }
}
