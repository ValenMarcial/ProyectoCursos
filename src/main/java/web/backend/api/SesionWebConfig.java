package web.backend.api;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra SesionInterceptor en toda la API. La protección va por defecto: un endpoint
 * nuevo bajo /api exige sesión aunque nadie se acuerde de agregarlo acá. Solo se dejan
 * libres las rutas públicas de forma explícita.
 */
@Configuration
public class SesionWebConfig implements WebMvcConfigurer {
    /** Rutas que exigen sesión: toda la API. Los archivos estáticos quedan afuera. */
    static final String RUTAS_PROTEGIDAS = "/api/**";

    /**
     * Rutas libres dentro de la API. /api/solicitudes es el alta pública de la web y de las
     * empresas, sin login. Va sin /** a propósito: una subruta nueva bajo /api/solicitudes
     * exige sesión. El login (POST /api/sesion) no está acá porque comparte ruta con GET y
     * DELETE, que sí piden sesión: lo deja pasar el propio SesionInterceptor.
     */
    static final String[] RUTAS_PUBLICAS = {"/api/solicitudes"};

    private final ObjectMapper objectMapper;

    /**
     * Recibe el ObjectMapper de Spring para que el interceptor escriba el 401 en JSON con
     * la misma configuración que el resto de las respuestas.
     */
    public SesionWebConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Engancha el interceptor en /api/** menos las rutas públicas. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SesionInterceptor(objectMapper))
                .addPathPatterns(RUTAS_PROTEGIDAS)
                .excludePathPatterns(RUTAS_PUBLICAS);
    }
}
