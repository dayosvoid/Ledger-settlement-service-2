package com.ledger.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PaymentControllerIT {

    // A real PostgreSQL in Docker. @ServiceConnection points the app's datasource at it.
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Value("${local.server.port}")
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    @DisplayName("POST 128450 for MR-4471, then the settlement owes 124469")
    void recordThenSettle() throws Exception {
        HttpRequest post = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/payments"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"merchantId\":\"MR-4471\",\"amountMinor\":128450,\"currency\":\"GBP\"}"))
                .build();
        HttpResponse<String> created = client.send(post, HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("Location")).isPresent();

        HttpRequest get = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/payments/settlement?merchantId=MR-4471")).GET().build();
        HttpResponse<String> settlement = client.send(get, HttpResponse.BodyHandlers.ofString());
        assertThat(settlement.statusCode()).isEqualTo(200);
        assertThat(settlement.body()).contains("\"owedMinor\":124469");
    }
}
