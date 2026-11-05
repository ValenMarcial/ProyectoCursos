package web.backend.service;

import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import web.backend.api.dto.IniciarSolicitudRequest;
import web.backend.api.dto.IniciarSolicitudResponse;
import web.backend.flowable.FlowableRestClient;
import web.backend.flowable.FlowableStartException;
import web.backend.persistence.SolicitudRepository;
import web.backend.persistence.SolicitudRepository.SolicitudRegistrada;

@Service
public class SolicitudService {
    private final SolicitudRepository solicitudes;
    private final FlowableRestClient flowable;

    public SolicitudService(SolicitudRepository solicitudes, FlowableRestClient flowable) {
        this.solicitudes = solicitudes;
        this.flowable = flowable;
    }

    public IniciarSolicitudResponse iniciar(IniciarSolicitudRequest request) {
        String businessKey = "solicitud-" + UUID.randomUUID();
        SolicitudRegistrada solicitud;
        try {
            solicitud = solicitudes.registrar(businessKey, request);
        } catch (DataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "No se pudo guardar la solicitud en PostgreSQL.", exception);
        }

        String processInstanceId;
        try {
            processInstanceId = flowable.iniciarSolicitud(
                    businessKey, solicitud.getSolicitudId(), solicitud.getEmpresaId(), request);
        } catch (FlowableStartException exception) {
            if (!exception.isOutcomeUnknown()) {
                solicitudes.eliminarSolicitud(solicitud.getSolicitudId());
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Flowable rechazó el inicio de la solicitud.", exception);
            }
            String reconciledProcessInstanceId;
            try {
                reconciledProcessInstanceId = flowable.buscarInstanciaPorBusinessKey(businessKey);
            } catch (RestClientException reconciliationException) {
                throw new SolicitudPendienteException(businessKey, null, reconciliationException);
            }
            if (reconciledProcessInstanceId == null) {
                throw new SolicitudPendienteException(businessKey, null, exception);
            }
            try {
                solicitudes.vincularInstancia(solicitud.getSolicitudId(), reconciledProcessInstanceId);
            } catch (DataAccessException | IllegalStateException reconciliationException) {
                throw new SolicitudPendienteException(
                        businessKey, reconciledProcessInstanceId, reconciliationException);
            }
            return new IniciarSolicitudResponse(reconciledProcessInstanceId, businessKey, "INICIADA");
        }

        try {
            solicitudes.vincularInstancia(solicitud.getSolicitudId(), processInstanceId);
        } catch (DataAccessException | IllegalStateException exception) {
            throw new SolicitudPendienteException(businessKey, processInstanceId, exception);
        }

        return new IniciarSolicitudResponse(processInstanceId, businessKey, "INICIADA");
    }
}