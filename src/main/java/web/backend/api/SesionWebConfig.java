package web.backend.api;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra SesionInterceptor en toda la API. La protección va por defecto: un endpoint
 * nuevo bajo /api exige sesión aunque nadie se acuerde de agregarlo acá. No se excluye ninguna
 * ruta: los pedidos públicos (login y alta de solicitudes) los deja pasar el propio
 * SesionInterceptor por método y ruta, así otro método sobre la misma ruta pide sesión.
 */
@Configuration
public class SesionWebConfig implements WebMvcConfigurer {
    /** Rutas que exigen sesión: toda la API. Los archivos estáticos quedan afuera. */
    static final String RUTAS_PROTEGIDAS = "/api/**";

    private final ObjectMapper objectMapper;

    /**
     * Recibe el ObjectMapper de Spring para que el interceptor escriba el 401 en JSON con
     * la misma configuración que el resto de las respuestas.
     */
    public SesionWebConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Engancha el interceptor en /api/**. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SesionInterceptor(objectMapper))
                .addPathPatterns(RUTAS_PROTEGIDAS);
    }
}
