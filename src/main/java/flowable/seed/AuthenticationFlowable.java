package flowable.seed;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class AuthenticationFlowable {
    private static final URI FLOWABLE_UI_URI = URI.create("http://localhost:8080/flowable-ui/");
    private static final URI AUTHENTICATION_URI = URI.create("http://localhost:8080/flowable-ui/app/authentication");

    public static HttpClient authenticate(String user, String password) throws Exception {
        // Crear un cliente HTTP con manejo de cookies para mantener la sesion
        HttpClient client = createClient();

        // Inicializar la sesión en Flowable UI para obtener cookies de sesion
        initializeSession(client);

        // Enviar la solicitud de inicio de sesion con las credenciales proporcionadas
        HttpResponse<String> response = submitLogin(client, user, password);

        // Validar la respuesta de inicio de sesion y devolver el cliente si es exitoso
        return validateLoginResponse(response) ? client : null;
    }

    private static HttpClient createClient() {
        // Crear un cliente HTTP con manejo de cookies para mantener la sesion
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        return HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .build();
    }

    private static void initializeSession(HttpClient client) throws Exception {
        // Enviar una solicitud GET a Flowable UI para inicializar la sesión y obtener
        // cookies de sesion
        HttpRequest initRequest = HttpRequest.newBuilder()
                .uri(FLOWABLE_UI_URI)
                .GET()
                .build();

        // Enviar la solicitud y descartar la respuesta, ya que solo necesitamos las cookies de sesion
        client.send(initRequest, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> submitLogin(HttpClient client, String user, String password) throws Exception {
        // Construir los datos del formulario de inicio de sesion con las credenciales proporcionadas
        String formData = "j_username=" + URLEncoder.encode(user, StandardCharsets.UTF_8)
                + "&j_password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);

        // Construir la solicitud POST para enviar los datos del formulario de inicio de sesion
        HttpRequest loginRequest = HttpRequest.newBuilder()
                .uri(AUTHENTICATION_URI)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        // Enviar la solicitud de inicio de sesion y devolver la respuesta
        return client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
    }

    private static boolean validateLoginResponse(HttpResponse<String> response) {
        // Validar la respuesta de inicio de sesion y determinar si fue exitosa
        if (response.statusCode() == 200) {
            System.out.println("Autenticación exitosa en Flowable UI.");
            return true;
        }

        if (response.statusCode() == 401) {
            System.err.println("Autenticación fallida: Credenciales inválidas (HTTP 401).");
            return false;
        }

        throw new IllegalStateException(
                "Error al consultar la cuenta en Flowable UI. HTTP " + response.statusCode());
    }
}
