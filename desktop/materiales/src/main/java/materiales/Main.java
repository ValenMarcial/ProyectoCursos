package materiales;

/** Abre la app de preparación de materiales. Variables opcionales: MOTOR_DB_URL, MOTOR_DB_USER, MOTOR_DB_PASSWORD. */
public final class Main {
    public static void main(String[] args) {
        ConfigMotor config = ConfigMotor.desdeEntorno();
        Motor motor;
        try {
            motor = Motor.conectar(config);
        } catch (RuntimeException e) {
            System.err.println("No se pudo conectar a la base del motor (" + config.url + "): " + e.getMessage());
            System.exit(1);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(motor::close));
        VentanaMateriales.abrir(new ServicioMateriales(motor.procesos().getTaskService(),
                motor.procesos().getRuntimeService(), motor.identidades()));
    }
}
