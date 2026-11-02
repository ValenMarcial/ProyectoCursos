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
    public static HttpClient authenticate(String user, String password) throws Exception {

        // Creamos la cokie y aceptamos todos los terminos
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .build();

        // 1. Inicializamos la sesión con una peticion GET al endpoint de Flowable UI
        HttpRequest initRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/flowable-ui/"))
                .GET()
                .build();

        client.send(initRequest, HttpResponse.BodyHandlers.ofString());

        // 2. Enviamos las credenciales para la autenticacion
        String formData = "j_username=" + URLEncoder.encode(user, StandardCharsets.UTF_8)
                + "&j_password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);

        // 3. Realizamos la petición POST al endpoint de autenticacion
        HttpRequest loginRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/flowable-ui/app/authentication"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> response = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());

        // 4. Verificamos el código de estado HTTP para determinar si la autenticacion
        // fue exitosa
        if (response.statusCode() == 200) {
            System.out.println("Autenticación exitosa en Flowable UI.");
            return client; // Retorna el cliente HTTP con la sesión iniciada
        }

        if (response.statusCode() == 401) {
            System.err.println("Autenticación fallida: Credenciales inválidas (HTTP 401).");
            return null;
        }

        throw new IllegalStateException(
                "Error al consultar la cuenta en Flowable UI. HTTP " + response.statusCode());
    }
}
