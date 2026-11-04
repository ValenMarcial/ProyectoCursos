package web.backend.api.dto;

public class IniciarSolicitudResponse {
    private final String processInstanceId;
    private final String businessKey;
    private final String estado;

    public IniciarSolicitudResponse(String processInstanceId, String businessKey, String estado) {
        this.processInstanceId = processInstanceId;
        this.businessKey = businessKey;
        this.estado = estado;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public String getEstado() {
        return estado;
    }
}
