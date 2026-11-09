package materiales;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Datos para conectarse a la base del motor Flowable (la misma que usa Flowable UI). */
public final class ConfigMotor {
    public final String url;
    public final String usuario;
    public final String clave;

    public ConfigMotor(String url, String usuario, String clave) {
        this.url = url;
        this.usuario = usuario;
        this.clave = clave;
    }

    /**
     * Toma los valores de MOTOR_DB_URL, MOTOR_DB_USER y MOTOR_DB_PASSWORD. Lo que falte se completa con el
     * .env de la raíz del repo (POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB) y con el puerto 5432.
     */
    public static ConfigMotor desdeEntorno() {
        Map<String, String> env = leerEnv(Path.of("../../.env"));
        String base = env.getOrDefault("POSTGRES_DB", "Institucion");
        String url = System.getenv().getOrDefault("MOTOR_DB_URL", "jdbc:postgresql://127.0.0.1:5432/" + base);
        String usuario = System.getenv().getOrDefault("MOTOR_DB_USER", env.getOrDefault("POSTGRES_USER", "UserPostgres"));
        String clave = System.getenv().getOrDefault("MOTOR_DB_PASSWORD", env.getOrDefault("POSTGRES_PASSWORD", ""));
        return new ConfigMotor(url, usuario, clave);
    }

    static Map<String, String> leerEnv(Path archivo) {
        Map<String, String> valores = new HashMap<>();
        if (!Files.isRegularFile(archivo)) {
            return valores;
        }
        try {
            for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
                int igual = linea.indexOf('=');
                if (igual > 0 && !linea.trim().startsWith("#")) {
                    valores.put(linea.substring(0, igual).trim(), linea.substring(igual + 1).trim());
                }
            }
        } catch (IOException e) {
            // Sin .env se usan los valores por defecto.
        }
        return valores;
    }
}
