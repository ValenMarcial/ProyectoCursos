package web.backend.service;

public class SolicitudPendienteException extends RuntimeException {
    private final String businessKey;
    private final String processInstanceId;

    public SolicitudPendienteException(String businessKey, String processInstanceId, Throwable cause) {
        super("La solicitud quedó pendiente de reconciliación.", cause);
        this.businessKey = businessKey;
        this.processInstanceId = processInstanceId;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }
}