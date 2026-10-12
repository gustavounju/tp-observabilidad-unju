package tpobservabilidadjava;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;

public class ObservabilidadDemo {

    // Se leen desde las variables de entorno inyectadas
    private static final String LOKI_URL = System.getenv().getOrDefault("LOKI_URL", "https://logs-prod-024.grafana.net/loki/api/v1/push");
    private static final String LOKI_USER = System.getenv().getOrDefault("LOKI_USER", "1825198");
    private static final String LOKI_API_KEY = System.getenv("LOKI_API_KEY");

    private static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) {
        if (LOKI_API_KEY == null || LOKI_API_KEY.isBlank()) {
            System.err.println("Error: Variable de entorno LOKI_API_KEY no definida.");
            System.exit(1);
        }

        System.out.println("Iniciando simulacion de pasarela de pagos...");
        long tiempoInicio = System.currentTimeMillis();

        try {
            while (true) {
                long transcurridoSegundos = (System.currentTimeMillis() - tiempoInicio) / 1000;

                // Al llegar a los 22 segundos disparamos el error simulado
                if (transcurridoSegundos >= 22) {
                    throw new RuntimeException("Error en pasarela: Fondos insuficientes / Gateway Timeout");
                }

                enviarLogLoki("INFO", "pago_exitoso");
                System.out.println("[" + transcurridoSegundos + "s] Enviado log: pago_exitoso");

                Thread.sleep(2000);
            }
        } catch (Exception e) {
            // Captura de excepcion: log de error
            enviarLogLoki("ERROR", "pago_fallido");
            System.err.println("\n[!] Excepcion capturada a los 22s: " + e.getMessage());
            System.err.println("[!] Enviado log de fallo: pago_fallido");
        }

        System.out.println("\nSimulacion finalizada.");
    }

    private static void enviarLogLoki(String nivel, String mensaje) {
        try {
            Instant now = Instant.now();
            long nanoTimestamp = now.getEpochSecond() * 1_000_000_000L + now.getNano();

            String jsonPayload = "{"
                + "\"streams\": ["
                + "  {"
                + "    \"stream\": {"
                + "      \"app\": \"pasarela-pagos-unju\","
                + "      \"level\": \"" + nivel + "\""
                + "    },"
                + "    \"values\": ["
                + "      [\"" + nanoTimestamp + "\", \"" + mensaje + "\"]"
                + "    ]"
                + "  }"
                + "]"
                + "}";

            String credenciales = LOKI_USER + ":" + LOKI_API_KEY;
            String authHeader = "Basic " + Base64.getEncoder().encodeToString(credenciales.getBytes());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LOKI_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", authHeader)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 204 && response.statusCode() != 200) {
                System.err.println("Aviso Loki: HTTP " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception ex) {
            System.err.println("Error de red: " + ex.getMessage());
        }
    }
}