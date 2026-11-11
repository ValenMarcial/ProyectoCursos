package web.backend.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
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

import web.backend.api.dto.PaginaResponse;
import web.backend.api.dto.TareaResponse;
import web.backend.flowable.FlowableRespuestaException;
import web.backend.service.TareaException;
import web.backend.service.TareaException.Motivo;
import web.backend.service.TareaService;
import web.backend.service.UsuarioSesion;

@WebMvcTest(TareaController.class)
class TareaControllerTest {
    private static final UsuarioSesion JEFE =
            new UsuarioSesion("jefeAdministrativo", "Jefe administrativo", List.of("Administracion"));
    private static final TareaResponse TAREA = new TareaResponse("7501", "Evaluar Recursos", null,
            "sid-F3381B5B", "7420", "2026-11-11T13:02:11.000+0000", null);
    private static final String TAREA_JSON = "{\"id\":\"7501\",\"name\":\"Evaluar Recursos\",\"assignee\":null,"
            + "\"taskDefinitionKey\":\"sid-F3381B5B\",\"processInstanceId\":\"7420\","
            + "\"createTime\":\"2026-11-11T13:02:11.000+0000\",\"dueDate\":null}";

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
        sesion.setAttribute(SesionController.ATRIBUTO_USUARIO, JEFE);
    }

    @Test
    void listaLasTareasConLaFormaDeFlowable() throws Exception {
        when(tareas.listar(JEFE, "grupo", null, null))
                .thenReturn(new PaginaResponse<>(List.of(TAREA), 1, 0, 50));

        mvc.perform(get(TareaController.RUTA).param("alcance", "grupo").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"data\":[" + TAREA_JSON + "],\"total\":1,\"start\":0,\"size\":50}", true));
    }

    @Test
    void pasaLaPaginaAlServicio() throws Exception {
        when(tareas.listar(JEFE, "mias", 10, 5)).thenReturn(new PaginaResponse<>(List.of(), 12, 10, 5));

        mvc.perform(get(TareaController.RUTA).param("alcance", "mias").param("start", "10").param("size", "5")
                        .session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"data\":[],\"total\":12,\"start\":10,\"size\":5}", true));
    }

    @Test
    void alcanceInvalidoResponde400ConMensaje() throws Exception {
        when(tareas.listar(eq(JEFE), isNull(), isNull(), isNull()))
                .thenThrow(new TareaException(Motivo.ALCANCE_INVALIDO));

        mvc.perform(get(TareaController.RUTA).session(sesion))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(mensaje(Motivo.ALCANCE_INVALIDO), true));
    }

    @Test
    void unaPaginaQueNoEsNumeroResponde400ConMensaje() throws Exception {
        mvc.perform(get(TareaController.RUTA).param("alcance", "mias").param("size", "muchas").session(sesion))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(mensaje(Motivo.PAGINA_INVALIDA), true));
        verifyNoInteractions(tareas);
    }

    @Test
    void obtieneUnaTarea() throws Exception {
        when(tareas.obtener(JEFE, "7501")).thenReturn(TAREA);

        mvc.perform(get(TareaController.RUTA + "/7501").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().json(TAREA_JSON, true));
    }

    @Test
    void unaTareaAjenaResponde404ConMensaje() throws Exception {
        when(tareas.obtener(JEFE, "7501")).thenThrow(new TareaException(Motivo.TAREA_NO_ENCONTRADA));

        mvc.perform(get(TareaController.RUTA + "/7501").session(sesion))
                .andExpect(status().isNotFound())
                .andExpect(content().json(mensaje(Motivo.TAREA_NO_ENCONTRADA), true));
    }

    @Test
    void sinRespuestaDeFlowableResponde502ConMensaje() throws Exception {
        when(tareas.listar(any(), anyString(), any(), any()))
                .thenThrow(new TareaException(Motivo.FLOWABLE_NO_RESPONDE));

        mvc.perform(get(TareaController.RUTA).param("alcance", "mias").session(sesion))
                .andExpect(status().isBadGateway())
                .andExpect(content().json(mensaje(Motivo.FLOWABLE_NO_RESPONDE), true));
    }

    @Test
    void unErrorDelMotorSeReenviaIdentico() throws Exception {
        String cuerpo = "{\"message\":\"Internal server error\",\"exception\":\"Algo falló en el motor\"}";
        when(tareas.listar(any(), anyString(), any(), any())).thenThrow(new FlowableRespuestaException(
                500, cuerpo.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON, null));

        mvc.perform(get(TareaController.RUTA).param("alcance", "mias").session(sesion))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(cuerpo, true));
    }

    static String mensaje(Motivo motivo) {
        return "{\"message\":\"" + motivo.getMensaje().replace("\"", "\\\"") + "\"}";
    }
}
