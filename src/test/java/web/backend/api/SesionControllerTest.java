package web.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

import web.backend.service.SesionException;
import web.backend.service.SesionException.Motivo;
import web.backend.service.SesionService;
import web.backend.service.UsuarioSesion;

@WebMvcTest(SesionController.class)
class SesionControllerTest {
    private static final String CLAVE = "clave-secreta-de-prueba";
    private static final String LOGIN = "{\"usuario\":\"jefeAdministrativo\",\"clave\":\"" + CLAVE + "\"}";
    private static final UsuarioSesion JEFE =
            new UsuarioSesion("jefeAdministrativo", "Jefe administrativo", List.of("Administracion"));

    @Autowired
    private MockMvc mvc;

    @MockBean
    private SesionService sesiones;

    // BackendApplication declara el RestTemplate de Flowable; acá no se usa y se reemplaza.
    @MockBean
    private RestTemplate restTemplate;

    @Test
    void loginCorrectoResponde200ConUsuarioNombreYGrupos() throws Exception {
        when(sesiones.iniciar("jefeAdministrativo", CLAVE)).thenReturn(JEFE);

        mvc.perform(post(SesionController.RUTA).contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        "{\"usuario\":\"jefeAdministrativo\",\"nombre\":\"Jefe administrativo\","
                                + "\"grupos\":[\"Administracion\"]}",
                        true));
    }

    @Test
    void loginRenuevaElIdDeSesionYGuardaElUsuarioSinLaClave() throws Exception {
        when(sesiones.iniciar("jefeAdministrativo", CLAVE)).thenReturn(JEFE);
        MockHttpSession sesion = new MockHttpSession(null, "id-anterior");

        MvcResult resultado = mvc.perform(post(SesionController.RUTA)
                        .session(sesion).contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk())
                .andReturn();

        assertNotEquals("id-anterior", resultado.getRequest().getSession().getId());
        assertEquals(JEFE, sesion.getAttribute(SesionController.ATRIBUTO_USUARIO));
        assertFalse(resultado.getResponse().getContentAsString().contains(CLAVE));
    }

    @Test
    void loginSinClaveResponde400ConMensaje() throws Exception {
        mvc.perform(post(SesionController.RUTA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"usuario\":\"jefeAdministrativo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"message\":\"" + mensajeJson(Motivo.DATOS_INVALIDOS) + "\"}", true));

        verifyNoInteractions(sesiones);
    }

    @Test
    void loginConUsuarioVacioResponde400() throws Exception {
        mvc.perform(post(SesionController.RUTA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"usuario\":\"  \",\"clave\":\"x\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(sesiones);
    }

    @Test
    void loginConJsonMalFormadoResponde400SinRepetirElCuerpo() throws Exception {
        MvcResult resultado = mvc.perform(post(SesionController.RUTA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"usuario\":\"jefe\",\"clave\":\"" + CLAVE))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertFalse(resultado.getResponse().getContentAsString().contains(CLAVE));
    }

    @Test
    void loginConUsuarioConDosPuntosResponde400() throws Exception {
        when(sesiones.iniciar(anyString(), anyString())).thenThrow(new SesionException(Motivo.DATOS_INVALIDOS));

        mvc.perform(post(SesionController.RUTA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"usuario\":\"a:b\",\"clave\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginConClaveIncorrectaResponde401ConMensaje() throws Exception {
        when(sesiones.iniciar(anyString(), anyString()))
                .thenThrow(new SesionException(Motivo.CREDENCIALES_INVALIDAS));

        mvc.perform(post(SesionController.RUTA).contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(
                        "{\"message\":\"" + mensajeJson(Motivo.CREDENCIALES_INVALIDAS) + "\"}", true));
    }

    @Test
    void loginSinRespuestaDeFlowableResponde502() throws Exception {
        when(sesiones.iniciar(anyString(), anyString()))
                .thenThrow(new SesionException(Motivo.FLOWABLE_NO_RESPONDE));

        mvc.perform(post(SesionController.RUTA).contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isBadGateway())
                .andExpect(content().json(
                        "{\"message\":\"" + mensajeJson(Motivo.FLOWABLE_NO_RESPONDE) + "\"}", true));
    }

    @Test
    void consultarLaSesionDevuelveElMismoUsuario() throws Exception {
        mvc.perform(get(SesionController.RUTA).session(sesionDe(JEFE)))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        "{\"usuario\":\"jefeAdministrativo\",\"nombre\":\"Jefe administrativo\","
                                + "\"grupos\":[\"Administracion\"]}",
                        true));
    }

    @Test
    void cerrarLaSesionResponde204YLaInvalida() throws Exception {
        MockHttpSession sesion = sesionDe(JEFE);

        mvc.perform(delete(SesionController.RUTA).session(sesion))
                .andExpect(status().isNoContent());

        assertTrue(sesion.isInvalid());
    }

    private static MockHttpSession sesionDe(UsuarioSesion usuario) {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionController.ATRIBUTO_USUARIO, usuario);
        return sesion;
    }

    // El mensaje tiene comillas; se escapan para compararlo dentro de un JSON.
    private static String mensajeJson(Motivo motivo) {
        return motivo.getMensaje().replace("\"", "\\\"");
    }
}
