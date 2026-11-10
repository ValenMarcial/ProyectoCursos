package web.backend.api;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra SesionInterceptor solo en las rutas del personal. */
@Configuration
public class SesionWebConfig implements WebMvcConfigurer {
    /**
     * Rutas que exigen sesión. /api/solicitudes queda afuera a propósito: es el alta
     * pública de la web y de las empresas, sin login. Los archivos estáticos tampoco entran.
     */
    static final String[] RUTAS_PROTEGIDAS = {"/api/tareas/**", "/api/instancias/**", SesionController.RUTA};

    private final ObjectMapper objectMapper;

    public SesionWebConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SesionInterceptor(objectMapper)).addPathPatterns(RUTAS_PROTEGIDAS);
    }
}
