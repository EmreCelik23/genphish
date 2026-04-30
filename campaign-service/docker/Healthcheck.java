import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class Healthcheck {
    private Healthcheck() {
    }

    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : "http://127.0.0.1:8080/actuator/health/readiness";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 400) {
            throw new IllegalStateException("Healthcheck failed with status " + statusCode);
        }
    }
}
