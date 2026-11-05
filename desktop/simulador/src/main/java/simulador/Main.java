package simulador;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Punto de entrada del simulador. Sin argumentos abre la ventana; con argumentos funciona como
 * consola (útil para scripts y pruebas rápidas):
 *
 * <pre>
 * listar
 * habilitadas &lt;instanciaId&gt;
 * enviar &lt;instanciaId&gt; &lt;COMUNICACION&gt; [variable=valor ...]
 * </pre>
 *
 * Configuración por variables de entorno: FLOWABLE_REST_URL, FLOWABLE_USER, FLOWABLE_PASSWORD.
 */
public class Main {
    public static void main(String[] args) {
        String url = env("FLOWABLE_REST_URL", "http://127.0.0.1:8080/flowable-ui/process-api");
        String usuario = env("FLOWABLE_USER", "admin");
        String password = env("FLOWABLE_PASSWORD", "test");

        // Sin argumentos se abre la ventana; con argumentos se usa la consola.
        if (args.length == 0) {
            VentanaSimulador.abrir(url, usuario, password);
            return;
        }

        FlowableClient cliente = new FlowableClient(url, usuario, password);
        Simulador simulador = new Simulador(cliente);

        try {
            switch (args[0]) {
                case "listar":
                    for (Solicitud solicitud : simulador.solicitudes()) {
                        System.out.println(solicitud);
                    }
                    break;
                case "habilitadas":
                    requerir(args, 2);
                    List<Comunicacion> habilitadas = simulador.habilitadas(args[1]);
                    if (habilitadas.isEmpty()) {
                        System.out.println("(ninguna comunicación habilitada en este momento)");
                    }
                    for (Comunicacion comunicacion : habilitadas) {
                        System.out.println(comunicacion + " - " + comunicacion.titulo());
                        for (Comunicacion.Campo campo : comunicacion.campos()) {
                            System.out.println("    " + campo.variable() + ": " + campo.descripcion());
                        }
                    }
                    break;
                case "enviar":
                    requerir(args, 3);
                    Comunicacion comunicacion = Comunicacion.valueOf(args[2].toUpperCase());
                    simulador.enviar(args[1], comunicacion, datos(args));
                    System.out.println("Enviado: " + comunicacion.titulo());
                    break;
                default:
                    uso();
                    System.exit(1);
            }
        } catch (IllegalArgumentException | IllegalStateException | FlowableClient.FlowableException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    private static Map<String, String> datos(String[] args) {
        Map<String, String> datos = new LinkedHashMap<>();
        for (int i = 3; i < args.length; i++) {
            int igual = args[i].indexOf('=');
            if (igual < 1) {
                throw new IllegalArgumentException("Dato mal formado '" + args[i] + "', se espera variable=valor.");
            }
            datos.put(args[i].substring(0, igual), args[i].substring(igual + 1));
        }
        return datos;
    }

    private static void requerir(String[] args, int cantidad) {
        if (args.length < cantidad) {
            uso();
            System.exit(1);
        }
    }

    private static String env(String nombre, String porDefecto) {
        String valor = System.getenv(nombre);
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }

    private static void uso() {
        System.err.println("Uso:\n  listar\n  habilitadas <instanciaId>\n"
                + "  enviar <instanciaId> <ACEPTACION|RECHAZO|MODIFICACIONES|NOMINA|CANCELACION> [variable=valor ...]");
    }
}
