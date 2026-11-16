package web.backend.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import web.backend.api.dto.VariableResponse;
import web.backend.flowable.FlowableRespuestaException;
import web.backend.service.TareaException;
import web.backend.service.TareaException.Motivo;
import web.backend.service.TareaService;
import web.backend.service.UsuarioSesion;

@WebMvcTest(InstanciaController.class)
class InstanciaControllerTest {
    private static final UsuarioSesion DOCENTE = new UsuarioSesion("docente1", "Docente 1", List.of("Docentes"));
    private static final String RUTA = InstanciaController.RUTA + "/7420/variables";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TareaService tareas;

    // BackendApplication declara el RestTemplate de Flowable; acá no se usa y se reemplaza.
    @MockBean
    private RestTemplate restTemplate;

    private MockHttpSession sesion;

    @BeforeEach
    void setUp() {
        sesion = new MockHttpSession();
        sesion.setAttribute(SesionController.ATRIBUTO_USUARIO, DOCENTE);
    }

    @Test
    void devuelveLasVariablesComoNombreTipoYValor() throws Exception {
        when(tareas.variables(DOCENTE, "7420")).thenReturn(List.of(
                new VariableResponse("tematica", "string", "BPMN"),
                new VariableResponse("participantesEstimados", "integer", 12),
                new VariableResponse("aulaFisica", "boolean", true),
                new VariableResponse("observaciones", "string", null)));

        mvc.perform(get(RUTA).session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().json("["
                        + "{\"name\":\"tematica\",\"type\":\"string\",\"value\":\"BPMN\"},"
                        + "{\"name\":\"participantesEstimados\",\"type\":\"integer\",\"value\":12},"
                        + "{\"name\":\"aulaFisica\",\"type\":\"boolean\",\"value\":true},"
                        + "{\"name\":\"observaciones\",\"type\":\"string\",\"value\":null}]", true));
    }

    @Test
    void sinTareasEnLaInstanciaResponde404ConMensaje() throws Exception {
        when(tareas.variables(DOCENTE, "7420")).thenThrow(new TareaException(Motivo.INSTANCIA_NO_ENCONTRADA));

        mvc.perform(get(RUTA).session(sesion))
                .andExpect(status().isNotFound())
                .andExpect(content().json(TareaControllerTest.mensaje(Motivo.INSTANCIA_NO_ENCONTRADA), true));
    }

    @Test
    void unErrorDelMotorSeReenviaIdentico() throws Exception {
        String cuerpo = "{\"message\":\"Not found\",\"exception\":\"Could not find a process instance with id '7420'.\"}";
        when(tareas.variables(DOCENTE, "7420")).thenThrow(new FlowableRespuestaException(
                404, cuerpo.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON, null));

        mvc.perform(get(RUTA).session(sesion))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(cuerpo, true));
    }
}
