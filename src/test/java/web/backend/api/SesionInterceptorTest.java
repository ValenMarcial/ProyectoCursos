package web.backend.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import web.backend.api.dto.IniciarSolicitudRequest;
import web.backend.api.dto.IniciarSolicitudResponse;
import web.backend.service.SesionException.Motivo;
import web.backend.service.SesionService;
import web.backend.service.SolicitudService;
import web.backend.service.UsuarioSesion;

@WebMvcTest(controllers = {SesionController.class, SolicitudController.class})
class SesionInterceptorTest {
    private static final String SIN_SESION = "{\"message\":\"" + Motivo.SIN_SESION.getMensaje() + "\"}";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private SesionService sesiones;

    @MockBean
    private SolicitudService solicitudes;

    // BackendApplication declara el RestTemplate de Flowable; acá no se usa y se reemplaza.
    @MockBean
    private RestTemplate restTemplate;

    @Test
    void consultarLaSesionSinCookieResponde401ConMensaje() throws Exception {
        mvc.perform(get(SesionController.RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(SIN_SESION, true));
    }

    @Test
    void cerrarLaSesionSinCookieResponde401() throws Exception {
        mvc.perform(delete(SesionController.RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(SIN_SESION, true));
    }

    @Test
    void unaSesionSinUsuarioNoAlcanza() throws Exception {
        mvc.perform(get(SesionController.RUTA).session(new MockHttpSession()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elLoginNoPideSesion() throws Exception {
        when(sesiones.iniciar(anyString(), anyString()))
                .thenReturn(new UsuarioSesion("jefeAdministrativo", "Jefe administrativo", List.of("Administracion")));

        mvc.perform(post(SesionController.RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"jefeAdministrativo\",\"clave\":\"clave\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void lasRutasDeTareasEInstanciasPidenSesion() throws Exception {
        for (String ruta : new String[] {"/api/tareas", "/api/tareas/tarea-1", "/api/instancias/instancia-1/variables"}) {
            mvc.perform(get(ruta))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json(SIN_SESION, true));
        }
    }

    @Test
    void conSesionLasRutasProtegidasPasanElInterceptor() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionController.ATRIBUTO_USUARIO,
                new UsuarioSesion("jefeAdministrativo", "Jefe administrativo", List.of("Administracion")));

        // Todavía no hay controller de tareas: pasar el interceptor termina en 404, no en 401.
        mvc.perform(get("/api/tareas").session(sesion))
                .andExpect(status().isNotFound());
    }

    @Test
    void elAltaPublicaDeSolicitudesNoPideSesion() throws Exception {
        when(solicitudes.iniciar(any(IniciarSolicitudRequest.class)))
                .thenReturn(new IniciarSolicitudResponse("process-1", "solicitud-demo", "INICIADA"));

        mvc.perform(post("/api/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"empresaNombre\":\"Empresa Ejemplo\",\"contactoNombre\":\"Ana Pérez\","
                                + "\"contactoEmail\":\"ana@example.com\",\"tematica\":\"BPMN\","
                                + "\"descripcion\":\"Curso de prueba\",\"participantesEstimados\":12,"
                                + "\"fechasPreferidas\":\"Marzo 2027\"}"))
                .andExpect(status().isCreated());
    }

    /**
     * La protección va por defecto: una ruta que todavía no existe bajo /api (como la de un
     * endpoint nuevo que nadie agregó a SesionWebConfig) responde 401 y no 404.
     */
    @Test
    void unaRutaNuevaBajoApiPideSesion() throws Exception {
        for (String ruta : new String[] {"/api", "/api/cursos", "/api/cursos/curso-1/participantes"}) {
            mvc.perform(get(ruta))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json(SIN_SESION, true));
        }
        mvc.perform(post("/api/reportes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(SIN_SESION, true));
    }

    /** Solo /api/solicitudes es pública: una subruta nueva debajo de ella exige sesión. */
    @Test
    void unaSubrutaDeSolicitudesPideSesion() throws Exception {
        mvc.perform(get("/api/solicitudes/solicitud-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(SIN_SESION, true));
    }

    /**
     * La excepción de /api/solicitudes es por ruta, no por método: otro método no pasa por el
     * interceptor y lo rechaza Spring (405), no la sesión (401).
     */
    @Test
    void otroMetodoSobreSolicitudesLoResuelveSpringYNoLaSesion() throws Exception {
        mvc.perform(get("/api/solicitudes"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void losArchivosEstaticosNoPidenSesion() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk());
    }
}
