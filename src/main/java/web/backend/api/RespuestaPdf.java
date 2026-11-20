package web.backend.api;

import java.nio.file.Path;

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Respuesta con el PDF de un informe para verlo en el navegador, sin caché (es de una sola empresa). */
final class RespuestaPdf {
    private RespuestaPdf() {
    }

    static ResponseEntity<Resource> informe(Path archivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"informe.pdf\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(new PathResource(archivo));
    }
}
