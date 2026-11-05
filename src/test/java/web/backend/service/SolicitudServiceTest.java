package web.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import web.backend.api.dto.IniciarSolicitudRequest;
import web.backend.api.dto.IniciarSolicitudResponse;
import web.backend.flowable.FlowableRestClient;
import web.backend.flowable.FlowableStartException;
import web.backend.persistence.SolicitudRepository;
import web.backend.persistence.SolicitudRepository.SolicitudRegistrada;

@ExtendWith(MockitoExtension.class)
class SolicitudServiceTest {
    @Mock
    private SolicitudRepository solicitudes;

    @Mock
    private FlowableRestClient flowable;

    private SolicitudService service;
    private IniciarSolicitudRequest request;

    @BeforeEach
    void setUp() {
        service = new SolicitudService(solicitudes, flowable);
        request = new IniciarSolicitudRequest();
        request.setEmpresaNombre("Empresa Ejemplo");
        request.setContactoNombre("Ana Pérez");
        request.setContactoEmail("ana@example.com");
        request.setTematica("BPMN");
        request.setDescripcion("Curso de prueba");
        request.setParticipantesEstimados(12);
        request.setFechasPreferidas("Marzo 2027");
    }

    @Test
    void persisteAntesDeIniciarYGuardaElIdDevueltoPorFlowable() {
        when(solicitudes.registrar(anyString(), same(request)))
                .thenReturn(new SolicitudRegistrada(7L, 42L));
        when(flowable.iniciarSolicitud(anyString(), eq(7L), eq(42L), same(request)))
                .thenReturn("process-1");

        IniciarSolicitudResponse response = service.iniciar(request);

        assertEquals("process-1", response.getProcessInstanceId());
        assertEquals("INICIADA", response.getEstado());
        ArgumentCaptor<String> businessKey = forClass(String.class);
        verify(solicitudes).registrar(businessKey.capture(), same(request));
        assertEquals(businessKey.getValue(), response.getBusinessKey());
        verify(flowable).iniciarSolicitud(businessKey.getValue(), 7L, 42L, request);
        verify(solicitudes).vincularInstancia(7L, "process-1");
    }

    @Test
    void eliminaElCursoCuandoFlowableRechazaElInicio() {
        when(solicitudes.registrar(anyString(), same(request)))
                .thenReturn(new SolicitudRegistrada(7L, 42L));
        when(flowable.iniciarSolicitud(anyString(), eq(7L), eq(42L), same(request)))
                .thenThrow(new FlowableStartException("rechazo", false));

        assertThrows(ResponseStatusException.class, () -> service.iniciar(request));

        verify(solicitudes).eliminarSolicitud(7L);
        verify(solicitudes, never()).vincularInstancia(eq(7L), anyString());
    }

    @Test
    void conservaElCursoPendienteCuandoElResultadoDeFlowableEsIncierto() {
        when(solicitudes.registrar(anyString(), same(request)))
                .thenReturn(new SolicitudRegistrada(7L, 42L));
        when(flowable.iniciarSolicitud(anyString(), eq(7L), eq(42L), same(request)))
                .thenThrow(new FlowableStartException("timeout", true));
        when(flowable.buscarInstanciaPorBusinessKey(anyString())).thenReturn(null);

        SolicitudPendienteException exception = assertThrows(
                SolicitudPendienteException.class, () -> service.iniciar(request));

        assertNull(exception.getProcessInstanceId());
        org.junit.jupiter.api.Assertions.assertFalse(exception.getBusinessKey().isEmpty());
        verify(solicitudes, never()).eliminarSolicitud(7L);
        verify(solicitudes, never()).vincularInstancia(eq(7L), anyString());
    }

        @Test
        void reconciliaYVinculaLaInstanciaSiFlowableLaCreoAntesDelTimeout() {
                when(solicitudes.registrar(anyString(), same(request)))
                                .thenReturn(new SolicitudRegistrada(7L, 42L));
                when(flowable.iniciarSolicitud(anyString(), eq(7L), eq(42L), same(request)))
                                .thenThrow(new FlowableStartException("timeout", true));
                when(flowable.buscarInstanciaPorBusinessKey(anyString())).thenReturn("process-1");

                IniciarSolicitudResponse response = service.iniciar(request);

                assertEquals("process-1", response.getProcessInstanceId());
                assertEquals("INICIADA", response.getEstado());
                verify(solicitudes).vincularInstancia(7L, "process-1");
        }
}