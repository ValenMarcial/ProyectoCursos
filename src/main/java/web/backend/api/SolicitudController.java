package web.backend.api;

import java.time.Duration;
import java.time.format.DateTimeParseException;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import web.backend.api.dto.IniciarSolicitudRequest;
import web.backend.api.dto.IniciarSolicitudResponse;
import web.backend.service.SolicitudService;
import web.backend.service.SolicitudPendienteException;

@RestController
@RequestMapping("/api/solicitudes")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class SolicitudController {
    private final SolicitudService solicitudes;

    public SolicitudController(SolicitudService solicitudes) {
        this.solicitudes = solicitudes;
    }

    @PostMapping
    public ResponseEntity<IniciarSolicitudResponse> iniciar(@Valid @RequestBody IniciarSolicitudRequest request) {
        validarPlazo(request.getPlazoRecordatorio());
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(solicitudes.iniciar(request));
        } catch (SolicitudPendienteException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new IniciarSolicitudResponse(
                            exception.getProcessInstanceId(), exception.getBusinessKey(), "PENDIENTE"));
        }
    }

    private void validarPlazo(String plazo) {
        if (plazo == null || plazo.trim().isEmpty()) {
            return;
        }
        try {
            Duration duration = Duration.parse(plazo.trim());
            if (duration.isZero() || duration.isNegative()) {
                throw new DateTimeParseException("La duración debe ser positiva", plazo, 0);
            }
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "plazoRecordatorio debe ser una duración ISO-8601 positiva, por ejemplo P5D o PT1M.",
                    exception);
        }
    }
}
