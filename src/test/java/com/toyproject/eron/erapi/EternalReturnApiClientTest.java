package com.toyproject.eron.erapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.toyproject.eron.erapi.dto.UserSearchResponse;
import com.toyproject.eron.global.config.EternalReturnApiProperties;

class EternalReturnApiClientTest {

    private HttpServer server;
    private final List<CapturedRequest> capturedRequests = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void getUserByNicknameSendsApiKeyAndMapsUser() {
        server.createContext("/user/nickname", exchange -> {
            capturedRequests.add(CapturedRequest.from(exchange));
            writeJson(exchange, 200, """
                    {
                      "code": 200,
                      "message": "Success",
                      "user": {
                        "userNum": 12345,
                        "nickname": "testUser"
                      }
                    }
                    """);
        });

        EternalReturnApiClient client = createClient("test-api-key");

        UserSearchResponse response = client.getUserByNickname("testUser");

        assertThat(response.userNum()).isEqualTo(12345);
        assertThat(response.nickname()).isEqualTo("testUser");
        assertThat(response.raw()).containsEntry("code", 200);
        assertThat(capturedRequests).hasSize(1);
        assertThat(capturedRequests.get(0).path()).isEqualTo("/user/nickname");
        assertThat(capturedRequests.get(0).query()).isEqualTo("query=testUser");
        assertThat(capturedRequests.get(0).apiKey()).isEqualTo("test-api-key");
    }

    @Test
    void getUserByNicknameThrowsNotFoundWhenUserIsMissing() {
        server.createContext("/user/nickname", exchange -> writeJson(exchange, 200, """
                {
                  "code": 404,
                  "message": "Not Found"
                }
                """));

        EternalReturnApiClient client = createClient("test-api-key");

        assertThatThrownBy(() -> client.getUserByNickname("unknown"))
                .isInstanceOfSatisfying(EternalReturnApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getMessage()).isEqualTo("Eternal Return user not found.");
                });
    }

    @Test
    void getUserStatsMapsHttpErrorResponse() {
        server.createContext("/user/stats/12345/1", exchange -> writeJson(exchange, 429, """
                {
                  "code": 429,
                  "message": "Too Many Requests"
                }
                """));

        EternalReturnApiClient client = createClient("test-api-key");

        assertThatThrownBy(() -> client.getUserStats(12345, 1))
                .isInstanceOfSatisfying(EternalReturnApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.getMessage()).contains("Too Many Requests");
                });
    }

    @Test
    void apiKeyIsRequiredBeforeCallingRemoteApi() {
        EternalReturnApiClient client = createClient("");

        assertThatThrownBy(() -> client.getUserGames(12345))
                .isInstanceOfSatisfying(EternalReturnApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("ER_API_KEY environment variable is not configured.");
                });
    }

    private EternalReturnApiClient createClient(String apiKey) {
        EternalReturnApiProperties properties = new EternalReturnApiProperties();
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setKey(apiKey);

        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("x-api-key", properties.getKey())
                .build();

        return new EternalReturnApiClient(restClient, properties);
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private record CapturedRequest(String path, String query, String apiKey) {

        private static CapturedRequest from(HttpExchange exchange) {
            URI uri = exchange.getRequestURI();
            return new CapturedRequest(
                    uri.getPath(),
                    uri.getQuery(),
                    exchange.getRequestHeaders().getFirst("x-api-key")
            );
        }
    }
}
