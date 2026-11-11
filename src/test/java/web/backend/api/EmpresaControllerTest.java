package web.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

import web.backend.api.dto.ActividadResponse;
import web.backend.api.dto.CursoEmpresaResponse;
import web.backend.service.EmpresaException;
import web.backend.service.EmpresaException.Motivo;
import web.backend.service.EmpresaService;
import web.backend.service.EmpresaSesion;
import web.backend.service.TareaService;
import web.backend.service.UsuarioSesion;

@WebMvcTest(controllers = {EmpresaController.class, TareaController.class})
class EmpresaControllerTest {
    private static final EmpresaSesion TECH = new EmpresaSesion(1L, "rrhh@tech.com", "Tech S.A.");
    private static final CursoEmpresaResponse CURSO = new CursoEmpresaResponse(10L, "BPMN", "Modelado",
            "confirmado", LocalDate.of(2027, 1, 15), LocalDate.of(2027, 2, 15), null);

    @Autowired
    private MockMvc mvc;

    @MockBean
    private EmpresaService empresas;

    @MockBean
    private TareaService tareas;

    @MockBean
    private RestTemplate restTemplate;

    @Test
    void loginSinSesionPreviaGuardaLaEmpresaYRenuevaElId() throws Exception {
        when(empresas.iniciar("rrhh@tech.com", "clave")).thenReturn(TECH);
        MockHttpSession sesion = new MockHttpSession(null, "id-anterior");

        MvcResult resultado = mvc.perform(post(EmpresaController.RUTA_SESION).session(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"rrhh@tech.com\",\"clave\":\"clave\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"usuario\":\"rrhh@tech.com\",\"empresa\":\"Tech S.A.\"}", true))
                .andReturn();

        assertNotEquals("id-anterior", resultado.getRequest().getSession().getId());
        assertEquals(TECH, sesion.getAttribute(EmpresaController.ATRIBUTO_EMPRESA));
    }

    @Test
    void loginIncorrectoResponde401() throws Exception {
        when(empresas.iniciar(anyString(), anyString()))
                .thenThrow(new EmpresaException(Motivo.CREDENCIALES_INVALIDAS));

        mvc.perform(post(EmpresaController.RUTA_SESION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"x\",\"clave\":\"y\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"message\":\"Usuario o clave incorrectos.\"}"));
    }

    @Test
    void misCursosConSesionDeEmpresa() throws Exception {
        when(empresas.cursos(TECH)).thenReturn(List.of(CURSO));

        mvc.perform(get(EmpresaController.RUTA + "/cursos").session(sesionDeEmpresa()))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"id\":10,\"nombre\":\"BPMN\",\"estado\":\"confirmado\","
                        + "\"fechaInicio\":\"2027-01-15\",\"fechaFin\":\"2027-02-15\"}]"));
    }

    @Test
    void elDetalleIncluyeLasActividades() throws Exception {
        when(empresas.curso(TECH, 10L)).thenReturn(CURSO.conActividades(
                List.of(new ActividadResponse("Recibir nómina", "receiveTask", "2026-11-11T10:00:00.000+0000"))));

        mvc.perform(get(EmpresaController.RUTA + "/cursos/10").session(sesionDeEmpresa()))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":10,\"actividades\":[{\"nombre\":\"Recibir nómina\"}]}"));
    }

    @Test
    void unCursoAjenoResponde404() throws Exception {
        when(empresas.curso(any(), anyLong())).thenThrow(new EmpresaException(Motivo.CURSO_NO_ENCONTRADO));

        mvc.perform(get(EmpresaController.RUTA + "/cursos/20").session(sesionDeEmpresa()))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinSesionOConSesionDelPersonalNoSeVenLosCursos() throws Exception {
        MockHttpSession personal = new MockHttpSession();
        personal.setAttribute(SesionController.ATRIBUTO_USUARIO,
                new UsuarioSesion("jefeAdministrativo", "Jefe", List.of("Administracion")));

        mvc.perform(get(EmpresaController.RUTA + "/cursos")).andExpect(status().isUnauthorized());
        mvc.perform(get(EmpresaController.RUTA + "/cursos").session(personal)).andExpect(status().isUnauthorized());
    }

    @Test
    void conSesionDeEmpresaNoSeLlegaALasTareas() throws Exception {
        mvc.perform(get("/api/tareas").param("alcance", "mias").session(sesionDeEmpresa()))
                .andExpect(status().isUnauthorized());
    }

    private static MockHttpSession sesionDeEmpresa() {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(EmpresaController.ATRIBUTO_EMPRESA, TECH);
        return sesion;
    }
}
