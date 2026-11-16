package web.backend.api.dto;

/** Respuesta de la subida del informe: la ruta relativa que la web manda como rutaInforme. */
public class RutaInformeResponse {
    private final String rutaInforme;

    public RutaInformeResponse(String rutaInforme) {
        this.rutaInforme = rutaInforme;
    }

    public String getRutaInforme() {
        return rutaInforme;
    }
}
