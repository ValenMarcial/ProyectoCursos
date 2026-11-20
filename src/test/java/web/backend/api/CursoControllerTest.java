package web.backend.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import web.backend.api.dto.CursoFinalizadoResponse;
import web.backend.service.CursoService;
import web.backend.service.EmpresaSesion;
import web.backend.service.TareaException;
import web.backend.service.TareaException.Motivo;
import web.backend.service.UsuarioSesion;

@WebMvcTest(CursoController.class)
class CursoControllerTest {
    private static final UsuarioSesion JEFE =
            new UsuarioSesion("jefeAdministrativo", "Jefe", List.of("Administracion"));

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CursoService cursos;

    @MockBean
    private RestTemplate restTemplate;

    @Test
    void listaLosCursosFinalizados() throws Exception {
        when(cursos.finalizados(JEFE)).thenReturn(List.of(new CursoFinalizadoResponse(10L, "BPMN", "Tech S.A.",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1), true)));

        mvc.perform(get(CursoController.RUTA + "/finalizados").session(sesion(JEFE)))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"id\":10,\"nombre\":\"BPMN\",\"empresa\":\"Tech S.A.\","
                        + "\"fechaInicio\":\"2026-03-01\",\"fechaFin\":\"2026-04-01\",\"informeDisponible\":true}]",
                        true));
    }

    @Test
    void otroGrupoRecibe403ConMensaje() throws Exception {
        when(cursos.finalizados(any())).thenThrow(new TareaException(Motivo.SOLO_ADMINISTRACION));

        mvc.perform(get(CursoController.RUTA + "/finalizados").session(sesion(JEFE)))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"message\":\"" + Motivo.SOLO_ADMINISTRACION.getMensaje() + "\"}"));
    }

    @Test
    void elInformeSeDevuelveComoPdf(@TempDir Path carpeta) throws Exception {
        byte[] pdf = "%PDF-1.7 informe".getBytes(StandardCharsets.US_ASCII);
        when(cursos.informe(JEFE, 10L)).thenReturn(Files.write(carpeta.resolve("a.pdf"), pdf));

        mvc.perform(get(CursoController.RUTA + "/10/informe").session(sesion(JEFE)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdf));
    }

    @Test
    void unIdQueNoEsNumeroOUnInformeInexistenteDa404() throws Exception {
        when(cursos.informe(any(), anyLong())).thenThrow(new TareaException(Motivo.INFORME_NO_ENCONTRADO));

        mvc.perform(get(CursoController.RUTA + "/abc/informe").session(sesion(JEFE)))
                .andExpect(status().isNotFound());
        mvc.perform(get(CursoController.RUTA + "/99/informe").session(sesion(JEFE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinSesionDelPersonalResponde401() throws Exception {
        MockHttpSession empresa = new MockHttpSession();
        empresa.setAttribute(EmpresaController.ATRIBUTO_EMPRESA, new EmpresaSesion(1L, "rrhh@tech.com", "Tech S.A."));

        mvc.perform(get(CursoController.RUTA + "/finalizados")).andExpect(status().isUnauthorized());
        mvc.perform(get(CursoController.RUTA + "/10/informe").session(empresa)).andExpect(status().isUnauthorized());
        verifyNoInteractions(cursos);
    }

    private static MockHttpSession sesion(UsuarioSesion usuario) {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionController.ATRIBUTO_USUARIO, usuario);
        return sesion;
    }
}
