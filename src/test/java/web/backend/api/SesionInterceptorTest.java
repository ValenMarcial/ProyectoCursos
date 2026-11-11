package web.backend.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import web.backend.api.dto.IniciarSolicitudRequest;
import web.backend.api.dto.IniciarSolicitudResponse;
import web.backend.api.dto.PaginaResponse;
import web.backend.service.EmpresaService;
import web.backend.service.EmpresaSesion;
import web.backend.service.SesionException.Motivo;
import web.backend.service.SesionService;
import web.backend.service.SolicitudService;
import web.backend.service.TareaService;
import web.backend.service.UsuarioSesion;

@WebMvcTest(controllers = {SesionController.class, SolicitudController.class, TareaController.class,
        EmpresaController.class})
@Import(SesionInterceptorTest.OtrosMetodosDeSolicitudes.class)
class SesionInterceptorTest {
    private static final String SIN_SESION = "{\"message\":\"" + Motivo.SIN_SESION.getMensaje() + "\"}";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private SesionService sesiones;

    @MockBean
    private SolicitudService solicitudes;

    @MockBean
    private TareaService tareas;

    @MockBean
    private EmpresaService empresas;

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
        UsuarioSesion jefe = new UsuarioSesion("jefeAdministrativo", "Jefe administrativo", List.of("Administracion"));
        sesion.setAttribute(SesionController.ATRIBUTO_USUARIO, jefe);
        when(tareas.listar(jefe, "mias", null, null)).thenReturn(new PaginaResponse<>(List.of(), 0, 0, 50));

        mvc.perform(get("/api/tareas").param("alcance", "mias").session(sesion))
                .andExpect(status().isOk());
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
     * La excepción de /api/solicitudes es por método: solo el POST es libre. GET, PUT y DELETE
     * (OtrosMetodosDeSolicitudes, endpoints que hoy no existen) piden sesión.
     */
    @Test
    void otroMetodoSobreSolicitudesPideSesion() throws Exception {
        mvc.perform(get("/api/solicitudes"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(SIN_SESION, true));
        mvc.perform(put("/api/solicitudes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(SIN_SESION, true));
        mvc.perform(delete("/api/solicitudes"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(SIN_SESION, true));
    }

    @Test
    void conSesionOtroMetodoSobreSolicitudesLlegaAlController() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionController.ATRIBUTO_USUARIO,
                new UsuarioSesion("jefeAdministrativo", "Jefe administrativo", List.of("Administracion")));

        mvc.perform(get("/api/solicitudes").session(sesion))
                .andExpect(status().isOk());
    }

    /**
     * Un método sin ningún endpoint en la ruta lo rechaza Spring con 405 antes de llegar al
     * interceptor. No expone nada: el 405 no trae datos.
     */
    @Test
    void unMetodoSinEndpointLoResuelveSpringAntesDelInterceptor() throws Exception {
        mvc.perform(patch("/api/solicitudes"))
                .andExpect(status().isMethodNotAllowed());
    }

    /** El preflight de CORS del alta pública (sin cookies) no pide sesión. */
    @Test
    void elPreflightDelAltaPublicaNoPideSesion() throws Exception {
        mvc.perform(options("/api/solicitudes")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk());
    }

    @Test
    void elLoginDeEmpresaNoPideSesion() throws Exception {
        when(empresas.iniciar(anyString(), anyString())).thenReturn(new EmpresaSesion(1L, "rrhh@tech.com", "Tech"));

        mvc.perform(post(EmpresaController.RUTA_SESION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"rrhh@tech.com\",\"clave\":\"clave\"}"))
                .andExpect(status().isOk());
    }

    /** /api/empresa/** pide sesión de empresa y el resto de la API la del personal: no se mezclan. */
    @Test
    void cadaParteDeLaApiPideSuPropiaSesion() throws Exception {
        UsuarioSesion jefe = new UsuarioSesion("jefeAdministrativo", "Jefe administrativo", List.of("Administracion"));
        EmpresaSesion empresa = new EmpresaSesion(1L, "rrhh@tech.com", "Tech");
        MockHttpSession personal = new MockHttpSession();
        personal.setAttribute(SesionController.ATRIBUTO_USUARIO, jefe);
        MockHttpSession deEmpresa = new MockHttpSession();
        deEmpresa.setAttribute(EmpresaController.ATRIBUTO_EMPRESA, empresa);
        when(empresas.cursos(empresa)).thenReturn(List.of());

        for (String ruta : new String[] {EmpresaController.RUTA_SESION, EmpresaController.RUTA + "/cursos",
                EmpresaController.RUTA + "/cursos/10"}) {
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
            mvc.perform(get(ruta).session(personal))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json(SIN_SESION, true));
        }
        mvc.perform(get(EmpresaController.RUTA + "/cursos").session(deEmpresa)).andExpect(status().isOk());
        for (String ruta : new String[] {SesionController.RUTA, "/api/tareas", "/api/instancias/instancia-1/variables"}) {
            mvc.perform(get(ruta).param("alcance", "mias").session(deEmpresa))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json(SIN_SESION, true));
        }
    }

    @Test
    void losArchivosEstaticosNoPidenSesion() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk());
    }

    /**
     * Endpoints de prueba con otros métodos sobre /api/solicitudes. Hoy SolicitudController
     * solo tiene POST; sin un endpoint, Spring responde 405 antes del interceptor y no se
     * podría ver si la ruta pide sesión.
     */
    @RestController
    static class OtrosMetodosDeSolicitudes {
        @GetMapping("/api/solicitudes")
        String listar() {
            return "ok";
        }

        @PutMapping("/api/solicitudes")
        String reemplazar() {
            return "ok";
        }

        @DeleteMapping("/api/solicitudes")
        String borrar() {
            return "ok";
        }
    }
}
