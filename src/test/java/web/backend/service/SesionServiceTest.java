package web.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import web.backend.flowable.FlowableRestClient;
import web.backend.service.SesionException.Motivo;

@ExtendWith(MockitoExtension.class)
class SesionServiceTest {
    private static final String CLAVE = "clave-secreta-de-prueba";

    @Mock
    private FlowableRestClient flowable;

    private SesionService service;

    @BeforeEach
    void setUp() {
        service = new SesionService(flowable);
    }

    @Test
    void loginCorrectoDevuelveUsuarioNombreYGrupos() {
        when(flowable.credencialesValidas("jefeAdministrativo", CLAVE)).thenReturn(true);
        when(flowable.buscarNombre("jefeAdministrativo")).thenReturn("Jefe administrativo");
        when(flowable.buscarGrupos("jefeAdministrativo")).thenReturn(List.of("Administracion"));

        UsuarioSesion usuario = service.iniciar("jefeAdministrativo", CLAVE);

        assertEquals("jefeAdministrativo", usuario.getUsuario());
        assertEquals("Jefe administrativo", usuario.getNombre());
        assertEquals(List.of("Administracion"), usuario.getGrupos());
    }

    @Test
    void elUsuarioDeLaSesionNoGuardaLaClave() throws IllegalAccessException {
        when(flowable.credencialesValidas("jefeAdministrativo", CLAVE)).thenReturn(true);
        when(flowable.buscarNombre("jefeAdministrativo")).thenReturn("Jefe administrativo");
        when(flowable.buscarGrupos("jefeAdministrativo")).thenReturn(List.of("Administracion"));

        UsuarioSesion usuario = service.iniciar("jefeAdministrativo", CLAVE);

        for (Field campo : UsuarioSesion.class.getDeclaredFields()) {
            campo.setAccessible(true);
            assertNotEquals(CLAVE, String.valueOf(campo.get(usuario)), "El campo " + campo.getName() + " tiene la clave");
        }
    }

    @Test
    void loginConClaveIncorrectaFallaPorCredencialesSinPedirMasDatos() {
        when(flowable.credencialesValidas("jefeAdministrativo", "clave-incorrecta")).thenReturn(false);

        SesionException exception = assertThrows(
                SesionException.class, () -> service.iniciar("jefeAdministrativo", "clave-incorrecta"));

        assertEquals(Motivo.CREDENCIALES_INVALIDAS, exception.getMotivo());
        verify(flowable, never()).buscarNombre(anyString());
        verify(flowable, never()).buscarGrupos(anyString());
    }

    @Test
    void unUsuarioConDosPuntosSeRechazaSinConsultarAFlowable() {
        SesionException exception = assertThrows(
                SesionException.class, () -> service.iniciar("jefe:Administrativo", CLAVE));

        assertEquals(Motivo.DATOS_INVALIDOS, exception.getMotivo());
        verifyNoInteractions(flowable);
    }

    @Test
    void siFlowableNoRespondeElLoginFallaConEseMotivo() {
        when(flowable.credencialesValidas("jefeAdministrativo", CLAVE))
                .thenThrow(new ResourceAccessException("Connection refused"));

        SesionException exception = assertThrows(
                SesionException.class, () -> service.iniciar("jefeAdministrativo", CLAVE));

        assertEquals(Motivo.FLOWABLE_NO_RESPONDE, exception.getMotivo());
    }
}
