package simulador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

/** Prueba de humo: la ventana se puede construir sin abrirse ni conectarse a Flowable. */
class VentanaSimuladorTest {
    @Test
    void laVentanaSeConstruyeConLaConfiguracionIndicada() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "sin pantalla no se pueden crear ventanas");

        String[] titulo = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            VentanaSimulador ventana = new VentanaSimulador("http://localhost:1/x", "admin", "test");
            titulo[0] = ventana.getTitle();
            ventana.dispose();
        });

        assertEquals("Simulador de comunicaciones de la empresa", titulo[0]);
    }
}
