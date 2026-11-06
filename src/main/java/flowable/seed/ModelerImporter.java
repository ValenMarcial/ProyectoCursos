package flowable.seed;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

public class ModelerImporter {
    private static final String MODELER_IMPORT_URL = "http://localhost:8080/flowable-ui/modeler-app/rest/import-process-model";
    private static final String MULTIPART_BOUNDARY = "---Boundary";

    public static void importModelToModeler(Path bpmnPath, String user, String password) {
        try {
            // Cargar el archivo BPMN del repo, corrigiendo lo que cambia el editor al guardar
            byte[] modelBytes = loadAndFixModel(bpmnPath);

            // Autenticar al usuario en Flowable Modeler
            HttpClient client = AuthenticationFlowable.authenticate(user, password);
            if (client == null) {
                System.err.println("Credenciales inválidas. Se cancela la importación del modelo.");
                return;
            }

            // Construir la solicitud HTTP para importar el modelo BPMN
            HttpRequest request = buildImportRequest(bpmnPath, modelBytes, user, password);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            reportImportResult(response);

        } catch (IllegalStateException e) {
            System.err.println("Error de permisos: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Excepción durante el proceso de importación:");
            e.printStackTrace();
        }
    }

    private static byte[] loadAndFixModel(Path bpmnPath) throws Exception {
        String original = Files.readString(bpmnPath);
        String fixed = ModelerExportFixer.fix(original);

        // Se reescribe el archivo del repo para que make deploy despliegue la versión corregida
        if (!fixed.equals(original)) {
            Files.writeString(bpmnPath, fixed);
            System.out.println("BPMN corregido (compensaciones y nombre del proceso) en " + bpmnPath);
        }
        return fixed.getBytes(StandardCharsets.UTF_8);
    }

    private static HttpRequest buildImportRequest(Path bpmnPath, byte[] modelBytes, String user,
            String password) {
        String fileName = bpmnPath.getFileName().toString();
        String credentials = user + ":" + password;
        String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        // Construir la solicitud HTTP POST para importar el modelo BPMN al Modeler de Flowable
        return HttpRequest.newBuilder()
                .uri(URI.create(MODELER_IMPORT_URL))
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", "multipart/form-data; boundary=" + MULTIPART_BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(
                        createMultipartBody(fileName, modelBytes)))
                .build();
    }

    private static List<byte[]> createMultipartBody(String fileName, byte[] modelBytes) {
        // Construir el cuerpo de la solicitud multipart/form-data para enviar el archivo BPMN
        byte[] head = ("--" + MULTIPART_BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: text/xml\r\n\r\n").getBytes(StandardCharsets.UTF_8);

        // Agregar el contenido del archivo BPMN y el pie de la solicitud multipart
        byte[] foot = ("\r\n--" + MULTIPART_BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8);

        // Devolver una lista de partes del cuerpo de la solicitud multipart
        return List.of(head, modelBytes, foot);
    }

    private static void reportImportResult(HttpResponse<String> response) {
        // Informar el resultado de la importación del modelo BPMN al Modeler de Flowable
        if (response.statusCode() == 200 || response.statusCode() == 201) {
            System.out.println("Modelo importado a Flowable Modeler con éxito.");
        } else {
            System.err.println("Error HTTP al importar modelo: " + response.statusCode());
            System.err.println(response.body());
        }
    }
}
