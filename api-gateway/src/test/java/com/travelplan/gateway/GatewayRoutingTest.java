package com.travelplan.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class GatewayRoutingTest {

    private static final Map<String, HttpServer> UPSTREAMS = Map.of(
            "auth", upstream("auth"), "users", upstream("users"),
            "travel", upstream("travel"), "payments", upstream("payments"));

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void configureUpstreams(DynamicPropertyRegistry registry) {
        UPSTREAMS.forEach((name, server) -> registry.add("services." + name + ".url",
                () -> "http://127.0.0.1:" + server.getAddress().getPort()));
    }

    @AfterAll
    static void stopUpstreams() {
        UPSTREAMS.values().forEach(server -> server.stop(0));
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /api/auth/login, auth",
            "GET, /api/users, users",
            "PUT, /api/users/123, users",
            "DELETE, /api/travelers/123, users",
            "GET, /api/travels, travel",
            "PUT, /api/travels/123/itinerary, travel",
            "GET, /api/destinations, travel",
            "POST, /api/payment-methods, payments",
            "DELETE, /api/payment-gateways/123, payments"
    })
    void forwardsToDomainOwnerPreservingRequest(String method, String path, String owner) throws Exception {
        String fullPath = path + "?page=1&size=20";
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + fullPath))
                .timeout(Duration.ofSeconds(10))
                .header("X-Request-Id", "routing-check")
                .method(method, HttpRequest.BodyPublishers.ofString("payload"))
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(owner + "|" + method + "|" + fullPath
                    + "|routing-check|payload");
        }
    }

    @ParameterizedTest
    @CsvSource({"/api/unknown", "/api/users-extra", "/internal/cleanup", "/actuator/env"})
    void doesNotRouteUnownedOrPrivatePaths(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(404);
        }
    }

    private static HttpServer upstream(String owner) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    String body = owner + "|" + exchange.getRequestMethod() + "|" + exchange.getRequestURI()
                            + "|" + exchange.getRequestHeaders().getFirst("X-Request-Id") + "|"
                            + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start test upstream", exception);
        }
    }
}
