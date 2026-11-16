package shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHashTest {

    @Test
    void validaSoloLaClaveCorrecta() {
        String hash = PasswordHash.hash("clave-de-prueba");

        assertTrue(PasswordHash.coincide("clave-de-prueba", hash));
        assertFalse(PasswordHash.coincide("clave-de-Prueba", hash));
        assertFalse(hash.contains("clave-de-prueba"));
    }

    @Test
    void usaUnaSalDistintaEnCadaHash() {
        assertNotEquals(PasswordHash.hash("misma"), PasswordHash.hash("misma"));
    }

    @Test
    void unHashMalFormadoNuncaCoincide() {
        assertFalse(PasswordHash.coincide("x", null));
        assertFalse(PasswordHash.coincide("x", "texto-plano"));
        assertFalse(PasswordHash.coincide("x", "pbkdf2-sha256$abc$%%%$%%%"));
        assertFalse(PasswordHash.coincide(null, PasswordHash.hash("x")));
    }

    @Test
    void generaClavesDelLargoPedido() {
        assertEquals(12, PasswordHash.generarClave(12).length());
        assertNotEquals(PasswordHash.generarClave(12), PasswordHash.generarClave(12));
    }
}
