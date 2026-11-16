package web.backend.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import web.backend.api.dto.TareaResponse;
import web.backend.service.TareaException.Motivo;

@ExtendWith(MockitoExtension.class)
class InformeServiceTest {
    private static final UsuarioSesion JEFE = new UsuarioSesion("jefeDeEquipo", "Jefe docente", List.of("Docentes"));
    private static final byte[] PDF = "%PDF-1.7\ncontenido".getBytes(StandardCharsets.US_ASCII);
    /** Misma forma que valida SaveReport en el motor. */
    private static final Pattern RUTA = Pattern.compile("cursos/7420/[0-9a-f-]{36}\\.pdf");

    @Mock
    private TareaService tareas;

    @TempDir
    Path carpeta;

    private InformeService service;

    @BeforeEach
    void setUp() {
        service = new InformeService(tareas, carpeta.toString());
    }

    @Test
    void guardaElPdfBajoLaInstanciaYDevuelveLaRutaRelativa() throws IOException {
        when(tareas.tareaAsignada(JEFE, "7501")).thenReturn(tarea(InformeService.TAREA_INFORME));

        String ruta = service.guardar(JEFE, "7501", PDF);

        assertTrue(RUTA.matcher(ruta).matches(), ruta);
        assertArrayEquals(PDF, Files.readAllBytes(carpeta.resolve(ruta)));
    }

    @Test
    void cadaSubidaGeneraUnArchivoNuevo() {
        when(tareas.tareaAsignada(JEFE, "7501")).thenReturn(tarea(InformeService.TAREA_INFORME));

        assertFalse(service.guardar(JEFE, "7501", PDF).equals(service.guardar(JEFE, "7501", PDF)));
    }

    @Test
    void unaTareaQueNoEsElInformeSeRechazaSinGuardar() throws IOException {
        when(tareas.tareaAsignada(JEFE, "7501")).thenReturn(tarea("Definir Docente"));

        assertEquals(Motivo.TAREA_SIN_INFORME,
                assertThrows(TareaException.class, () -> service.guardar(JEFE, "7501", PDF)).getMotivo());
        assertTrue(vacia());
    }

    @Test
    void sinTareaPropiaNoGuardaNada() throws IOException {
        when(tareas.tareaAsignada(JEFE, "7501")).thenThrow(new TareaException(Motivo.NO_ASIGNADA));

        assertEquals(Motivo.NO_ASIGNADA,
                assertThrows(TareaException.class, () -> service.guardar(JEFE, "7501", PDF)).getMotivo());
        assertTrue(vacia());
    }

    @Test
    void rechazaLoQueNoEsPdfOEstaVacio() throws IOException {
        when(tareas.tareaAsignada(JEFE, "7501")).thenReturn(tarea(InformeService.TAREA_INFORME));

        for (byte[] contenido : new byte[][] {
                new byte[0], "texto plano".getBytes(StandardCharsets.UTF_8), "%PD".getBytes(StandardCharsets.US_ASCII) }) {
            assertEquals(Motivo.INFORME_INVALIDO,
                    assertThrows(TareaException.class, () -> service.guardar(JEFE, "7501", contenido)).getMotivo());
        }
        assertTrue(vacia());
    }

    @Test
    void rechazaUnPdfMasGrandeQueElMaximo() throws IOException {
        when(tareas.tareaAsignada(JEFE, "7501")).thenReturn(tarea(InformeService.TAREA_INFORME));
        byte[] grande = new byte[InformeService.MAXIMO_MB * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, grande, 0, PDF.length);

        assertEquals(Motivo.INFORME_MUY_GRANDE,
                assertThrows(TareaException.class, () -> service.guardar(JEFE, "7501", grande)).getMotivo());
        assertTrue(vacia());
    }

    @Test
    void siLaCarpetaNoSePuedeEscribirDaErrorDeServidor() throws IOException {
        when(tareas.tareaAsignada(JEFE, "7501")).thenReturn(tarea(InformeService.TAREA_INFORME));
        // "cursos" es un archivo: no se puede crear la carpeta de la instancia adentro.
        Files.writeString(carpeta.resolve("cursos"), "ocupado");

        assertEquals(Motivo.INFORME_NO_GUARDADO,
                assertThrows(TareaException.class, () -> service.guardar(JEFE, "7501", PDF)).getMotivo());
    }

    // --- ubicar (descarga) ---

    @Test
    void ubicaElPdfDeLaInstanciaSegunRutaInforme() throws IOException {
        Path pdf = crear("cursos/7420/informe.pdf");
        pedirRuta("cursos/7420/informe.pdf");

        assertEquals(pdf.toRealPath(), service.ubicar(JEFE, "7501").toRealPath());
    }

    @Test
    void sinRutaInformeEnLaInstanciaDa404() {
        when(tareas.tareaAsignada(JEFE, "7501")).thenReturn(tarea("Enviar informe"));
        when(tareas.textoDeInstancia("7420", "rutaInforme")).thenReturn(Optional.empty());

        assertEquals(Motivo.INFORME_NO_ENCONTRADO,
                assertThrows(TareaException.class, () -> service.ubicar(JEFE, "7501")).getMotivo());
    }

    @Test
    void unaRutaQueNoEsDeLaInstanciaOSaleDeLaCarpetaDa404() throws IOException {
        crear("cursos/7420/informe.pdf");
        crear("cursos/9999/ajeno.pdf");
        Files.writeString(carpeta.resolve("secreto.pdf"), "%PDF-");
        for (String ruta : List.of(
                "cursos/9999/ajeno.pdf",            // otra instancia
                "cursos/7420/../9999/ajeno.pdf",    // sube y baja de carpeta
                "../secreto.pdf",
                "/etc/passwd",
                "cursos/7420/informe.txt",          // otra extensión
                "cursos/7420/",                     // sin archivo
                "cursos/7420/no-esta.pdf")) {       // no existe
            pedirRuta(ruta);

            assertEquals(Motivo.INFORME_NO_ENCONTRADO,
                    assertThrows(TareaException.class, () -> service.ubicar(JEFE, "7501")).getMotivo(), ruta);
        }
    }

    @Test
    void unEnlaceSimbolicoQueSaleDeLaCarpetaDa404() throws IOException {
        Path afuera = Files.createTempFile("afuera", ".pdf");
        try {
            Files.createDirectories(carpeta.resolve("cursos/7420"));
            Files.createSymbolicLink(carpeta.resolve("cursos/7420/enlace.pdf"), afuera);
            pedirRuta("cursos/7420/enlace.pdf");

            assertEquals(Motivo.INFORME_NO_ENCONTRADO,
                    assertThrows(TareaException.class, () -> service.ubicar(JEFE, "7501")).getMotivo());
        } finally {
            Files.deleteIfExists(afuera);
        }
    }

    @Test
    void sinTareaPropiaNoSeUbicaNada() {
        when(tareas.tareaAsignada(JEFE, "7501")).thenThrow(new TareaException(Motivo.NO_AUTORIZADA));

        assertEquals(Motivo.NO_AUTORIZADA,
                assertThrows(TareaException.class, () -> service.ubicar(JEFE, "7501")).getMotivo());
    }

    private void pedirRuta(String ruta) {
        when(tareas.tareaAsignada(JEFE, "7501")).thenReturn(tarea("Enviar informe"));
        when(tareas.textoDeInstancia("7420", "rutaInforme")).thenReturn(Optional.of(ruta));
    }

    private Path crear(String relativa) throws IOException {
        Path archivo = carpeta.resolve(relativa);
        Files.createDirectories(archivo.getParent());
        return Files.write(archivo, PDF);
    }

    private boolean vacia() throws IOException {
        try (Stream<Path> archivos = Files.list(carpeta)) {
            return archivos.findAny().isEmpty();
        }
    }

    private static TareaResponse tarea(String nombre) {
        return new TareaResponse("7501", nombre, "jefeDeEquipo", "sid-8EE040F3", "7420",
                "2026-11-11T13:02:11.000+0000", null);
    }
}
