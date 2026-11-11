package shared;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hash de las claves de las cuentas de empresa, con PBKDF2 del JDK (sin dependencias).
 * Lo comparten el motor, que crea la cuenta en "Habilitar acceso", y el backend, que valida el login;
 * por eso está fuera de los paquetes de cada uno y lo compilan los dos perfiles de Maven.
 *
 * Formato guardado: pbkdf2-sha256$iteraciones$sal$hash (sal y hash en Base64). Las iteraciones
 * van en el texto, así se pueden subir sin invalidar las claves ya guardadas.
 */
public final class PasswordHash {
    private static final String PREFIJO = "pbkdf2-sha256";
    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    /** Valor recomendado por OWASP para PBKDF2-HMAC-SHA256. */
    private static final int ITERACIONES = 600_000;
    private static final int BYTES_SAL = 16;
    private static final int BITS_HASH = 256;
    /** Sin caracteres que se confunden al leerlos (0/O, 1/l/I). */
    private static final String ALFABETO_CLAVE = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHash() {
    }

    /** Devuelve el hash con sal aleatoria, listo para guardar. */
    public static String hash(String clave) {
        byte[] sal = new byte[BYTES_SAL];
        RANDOM.nextBytes(sal);
        return PREFIJO + "$" + ITERACIONES + "$" + base64(sal) + "$" + base64(pbkdf2(clave, sal, ITERACIONES));
    }

    /** Indica si la clave corresponde al hash guardado. Un hash con otro formato nunca coincide. */
    public static boolean coincide(String clave, String guardado) {
        String[] partes = guardado == null ? new String[0] : guardado.split("\\$");
        if (clave == null || partes.length != 4 || !PREFIJO.equals(partes[0])) {
            return false;
        }
        try {
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            byte[] calculado = pbkdf2(clave, Base64.getDecoder().decode(partes[2]), Integer.parseInt(partes[1]));
            // Comparación en tiempo constante: no revela cuántos bytes coinciden.
            return MessageDigest.isEqual(esperado, calculado);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** Genera una clave aleatoria para entregarle a la empresa. */
    public static String generarClave(int largo) {
        StringBuilder clave = new StringBuilder(largo);
        for (int i = 0; i < largo; i++) {
            clave.append(ALFABETO_CLAVE.charAt(RANDOM.nextInt(ALFABETO_CLAVE.length())));
        }
        return clave.toString();
    }

    private static byte[] pbkdf2(String clave, byte[] sal, int iteraciones) {
        PBEKeySpec spec = new PBEKeySpec(clave.toCharArray(), sal, iteraciones, BITS_HASH);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("El JDK no soporta " + ALGORITMO, exception);
        } finally {
            spec.clearPassword();
        }
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
