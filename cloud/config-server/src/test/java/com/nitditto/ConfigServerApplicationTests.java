package com.nitditto;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerApplicationTests {

    @LocalServerPort
    private int port;

    private String get(final String path) throws Exception {
        final HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    @Test
    void servesSharedConfigToAnyService() throws Exception {
        final String body = this.get("/location-service/default");
        assertThat(body).contains("config-repo/application.yaml").contains("prefer-ip-address");
    }

    @Test
    void servesServiceSpecificConfigOnTopOfShared() throws Exception {
        final String body = this.get("/flight-ops-service/default");
        assertThat(body).contains("flight-ops-service.yaml").contains("flight.instance.created");
    }
}
