package flowable.seed;

import org.flowable.idm.api.IdmIdentityService;
import org.flowable.idm.engine.IdmEngine;
import org.flowable.idm.engine.IdmEngineConfiguration;
import java.nio.file.Path;
import java.util.Scanner;

public class Main {

    private static final String USAGE = "Uso: mvn exec:java -Dexec.mainClass=flowable.seed.Main "
            + "[-Dexec.args=\"[--no-seed] [--no-import]\"]\n"
            + "  (sin flags)   Crea usuarios y grupos, e importa el modelo BPMN.\n"
            + "  --no-seed     Solo importa el modelo BPMN.\n"
            + "  --no-import   Solo crea usuarios y grupos.\n"
            + "Los flags --no-seed y --no-import no se pueden usarjuntos.";

    public static void main(String[] args) {
        boolean seedEnabled = true;
        boolean importEnabled = true;

        // 0. Interpretar los parámetros de lanzamiento.
        for (String arg : args) {
            if ("--no-seed".equals(arg)) {
                seedEnabled = false;
            } else if ("--no-import".equals(arg)) {
                importEnabled = false;
            } else {
                System.err.println("Argumento desconocido: " + arg);
                System.err.println(USAGE);
                System.exit(1);
                return;
            }
        }

        if (!seedEnabled && !importEnabled) {
            System.err.println("Error: --no-seed y --no-import no pueden usarse juntos.");
            System.err.println(USAGE);
            System.exit(1);
            return;
        }

        System.out.println("Configurando el motor de Flowable ...");

        // 1. Crear la configuración para conectarse al motor de identidad de Flowable.
        IdmEngineConfiguration config = IdmEngineConfiguration.createStandaloneIdmEngineConfiguration();

        // 2. Indicar la conexión y las credenciales de la base Institucion. Se pueden cambiar con
        // MOTOR_DB_URL, MOTOR_DB_USER y MOTOR_DB_PASSWORD (los mismos nombres que la app de materiales),
        // por ejemplo si Postgres está en otro puerto.
        config.setJdbcUrl(System.getenv().getOrDefault("MOTOR_DB_URL", "jdbc:postgresql://localhost:5432/Institucion"));
        config.setJdbcDriver("org.postgresql.Driver");
        config.setJdbcUsername(System.getenv().getOrDefault("MOTOR_DB_USER", "UserPostgres"));
        config.setJdbcPassword(System.getenv().getOrDefault("MOTOR_DB_PASSWORD", "Password"));

        config.setDatabaseSchemaUpdate(IdmEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);

        // 3. Construir el motor IDM, que proporciona acceso a usuarios, grupos y
        // privilegios.
        IdmEngine idmEngine = config.buildIdmEngine();
        try {
            // 4. Obtener el servicio IDM e inyectarlo en la lógica de la seed.
            IdmIdentityService idmIdentityService = idmEngine.getIdmIdentityService();

            // 5. Crear los usuarios, grupos, membresías y privilegios definidos en
            // FlowableSeed, a menos que este el argumento --no-seed.
            if (seedEnabled) {
                FlowableSeed seed = new FlowableSeed(idmIdentityService);
                System.out.println("Ejecutando la seed");
                seed.seed();
            }

            // 6. Importar el modelo BPMN al Modeler, a menos que este el argumento --no-import.
            if (importEnabled) {
                System.out.println("Importando el modelo a Flowable Modeler");

                try (Scanner scanner = new Scanner(System.in)) {

                    // Solicitar al usuario las credenciales de Flowable
                    System.out.print("Usuario de Flowable: ");
                    String user = scanner.nextLine().trim();

                    System.out.print("Contraseña: ");
                    String password = scanner.nextLine();

                    // Importar el modelo BPMN al Modeler de Flowable
                    // Se usa el archivo del repo (no el copiado a target) para poder corregirlo en el lugar
                    ModelerImporter.importModelToModeler(
                            Path.of("src/main/resources/processes/ProyectoCursos.bpmn20.xml"),
                            user,
                            password);
                }
            }

        } catch (Exception e) {
            System.err.println("Ocurrió un error crítico durante la inicialización: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 7. Cerrar el motor.
            idmEngine.close();
        }
    }
}
