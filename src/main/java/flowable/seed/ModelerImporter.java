package flowable.seed;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

public class ModelerImporter {

    public static void importModelToModeler(String bpmnResourcePath, String user, String password) {

        try {
            // 1. Validar autenticación con la clase AutenticacionFlowable
            HttpClient client = AutenticationFlowable.authenticate(user, password);

            if (client == null) {
                System.err.println("Credenciales inválidas. Se cancela la importación del modelo.");
                return;
            }

            // 2. Leer el archivo BPMN desde resources
            InputStream is = null;
            byte[] archivoBytes;

            try {
                // Intentar obtener el archivo BPMN desde el classpath
                is = ModelerImporter.class.getClassLoader().getResourceAsStream(bpmnResourcePath);

                if (is == null) {
                    System.err.println("No se encontró el archivo BPMN en la ruta: " + bpmnResourcePath);
                    return;
                }

                // 3. Convertir el InputStream a un arreglo de bytes
                archivoBytes = is.readAllBytes();

            } finally {
                if (is != null) {
                    is.close();
                }
            }

            // 4. Armar y enviar la petición POST al API REST del Modeler
            String url = "http://localhost:8080/flowable-ui/modeler-app/rest/import-process-model";
            String boundary = "---Boundary";
            String credentials = user + ":" + password;
            String basicAuth = Base64.getEncoder().encodeToString(
                    credentials.getBytes(StandardCharsets.UTF_8));
            String fileName = Path.of(bpmnResourcePath).getFileName().toString();

            byte[] head = ("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                    + "Content-Type: text/xml\r\n\r\n").getBytes(StandardCharsets.UTF_8);

            byte[] foot = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + basicAuth)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(head, archivoBytes, foot)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 5. Verificar resultado de la importación
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                System.out.println("Modelo importado a Flowable Modeler con éxito.");
            } else {
                System.err.println("Error HTTP al importar modelo: " + response.statusCode());
                System.err.println(response.body());
            }

        } catch (IllegalStateException e) {
            // Captura las excepciones de permisos lanzadas por AutenticacionFlowable (p.
            // ej. HTTP 403)
            System.err.println("Error de permisos: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Excepción durante el proceso de importación:");
            e.printStackTrace();
        }
    }
}
