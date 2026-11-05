package web.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import web.backend.api.dto.IniciarSolicitudRequest;
import web.backend.api.dto.IniciarSolicitudResponse;
import web.backend.service.SolicitudPendienteException;
import web.backend.service.SolicitudService;

class SolicitudControllerTest {
    @Test
    void devuelveLaClaveDeNegocioEnElCuerpoCuandoLaSolicitudQuedaPendiente() {
        SolicitudService service = mock(SolicitudService.class);
        when(service.iniciar(org.mockito.ArgumentMatchers.any(IniciarSolicitudRequest.class)))
                .thenThrow(new SolicitudPendienteException("solicitud-demo", null, new RuntimeException()));
        SolicitudController controller = new SolicitudController(service);

        ResponseEntity<IniciarSolicitudResponse> response = controller.iniciar(new IniciarSolicitudRequest());

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("solicitud-demo", response.getBody().getBusinessKey());
        assertEquals("PENDIENTE", response.getBody().getEstado());
        assertNull(response.getBody().getProcessInstanceId());
    }
}