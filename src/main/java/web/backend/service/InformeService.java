package web.backend.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import web.backend.api.dto.TareaResponse;
import web.backend.service.TareaException.Motivo;

/**
 * Guarda el PDF del informe de resultados. Solo lo puede subir quien tiene asignada la tarea
 * "Elaborar informe de resultados". El archivo queda en cursos/&lt;instancia&gt;/&lt;uuid&gt;.pdf
 * dentro de la carpeta de informes, que es justo la forma que exige SaveReport en el motor.
 * Si se sube más de una vez, queda un archivo por intento; vale el último que se manda al completar.
 */
@Service
public class InformeService {
    /** Nombre de la tarea en el BPMN (el id cambia si se vuelve a exportar el modelo). */
    static final String TAREA_INFORME = "Elaborar informe de resultados";
    /** Tamaño máximo del PDF. Se repite en spring.servlet.multipart de application.yml. */
    public static final int MAXIMO_MB = 10;
    private static final long MAXIMO_BYTES = MAXIMO_MB * 1024L * 1024L;
    private static final byte[] CABECERA_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final TareaService tareas;
    private final Path carpeta;

    public InformeService(TareaService tareas, @Value("${informes.dir}") String carpeta) {
        this.tareas = tareas;
        this.carpeta = Path.of(carpeta);
    }

    /**
     * Guarda el PDF y devuelve su ruta relativa (cursos/&lt;instancia&gt;/&lt;uuid&gt;.pdf).
     * Recibe el usuario de la sesión, el id de la tarea y el contenido del archivo. Lanza
     * TareaException: las de tareaAsignada, TAREA_SIN_INFORME, INFORME_INVALIDO (vacío o sin
     * cabecera %PDF-), INFORME_MUY_GRANDE o INFORME_NO_GUARDADO.
     */
    public String guardar(UsuarioSesion usuario, String tareaId, byte[] contenido) {
        TareaResponse tarea = tareas.tareaAsignada(usuario, tareaId);
        if (!TAREA_INFORME.equals(tarea.getName())) {
            throw new TareaException(Motivo.TAREA_SIN_INFORME);
        }
        if (contenido == null || contenido.length == 0 || !esPdf(contenido)) {
            throw new TareaException(Motivo.INFORME_INVALIDO);
        }
        if (contenido.length > MAXIMO_BYTES) {
            throw new TareaException(Motivo.INFORME_MUY_GRANDE);
        }
        String relativa = "cursos/" + tarea.getProcessInstanceId() + "/" + UUID.randomUUID() + ".pdf";
        Path destino = carpeta.resolve(relativa);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, contenido);
        } catch (IOException exception) {
            throw new TareaException(Motivo.INFORME_NO_GUARDADO, exception);
        }
        return relativa;
    }

    /**
     * Ubica el PDF del informe de la instancia de una tarea del usuario, para descargarlo.
     * La ruta sale SIEMPRE de la variable rutaInforme de la instancia, leída en el servidor: la web
     * no manda ninguna ruta. Se exige que sea cursos/&lt;instancia de la tarea&gt;/&lt;archivo&gt;.pdf, que
     * quede dentro de la carpeta de informes (también tras resolver enlaces simbólicos) y que
     * sea un archivo común.
     * Recibe el usuario de la sesión y el id de la tarea. Lanza TareaException: las de
     * tareaAsignada, FLOWABLE_NO_RESPONDE o INFORME_NO_ENCONTRADO (sin ruta, ruta que no
     * corresponde a la instancia o archivo ausente: no se distingue, para no revelar rutas).
     */
    public Path ubicar(UsuarioSesion usuario, String tareaId) {
        TareaResponse tarea = tareas.tareaAsignada(usuario, tareaId);
        String instancia = tarea.getProcessInstanceId();
        String ruta = tareas.textoDeInstancia(instancia, "rutaInforme")
                .orElseThrow(() -> new TareaException(Motivo.INFORME_NO_ENCONTRADO));
        String prefijo = "cursos/" + instancia + "/";
        Path base = carpeta.toAbsolutePath().normalize();
        Path archivo = base.resolve(ruta).normalize();
        try {
            if (!ruta.startsWith(prefijo) || !ruta.endsWith(".pdf")
                    || !archivo.startsWith(base.resolve("cursos").resolve(instancia))
                    || !Files.isRegularFile(archivo)
                    || !archivo.toRealPath().startsWith(base.toRealPath())) {
                throw new TareaException(Motivo.INFORME_NO_ENCONTRADO);
            }
        } catch (IOException exception) {
            throw new TareaException(Motivo.INFORME_NO_ENCONTRADO, exception);
        }
        return archivo;
    }

    private static boolean esPdf(byte[] contenido) {
        return contenido.length >= CABECERA_PDF.length
                && Arrays.equals(Arrays.copyOf(contenido, CABECERA_PDF.length), CABECERA_PDF);
    }
}
