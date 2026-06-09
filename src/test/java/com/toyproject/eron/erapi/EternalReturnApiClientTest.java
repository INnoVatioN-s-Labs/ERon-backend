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
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.toyproject.eron.erapi.dto.UserGamesResponse;
import com.toyproject.eron.erapi.dto.UserOverviewResponse;
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
                        "userId": "abc-123",
                        "nickname": "testUser"
                      }
                    }
                    """);
        });

        EternalReturnApiClient client = createClient("test-api-key");

        UserSearchResponse response = client.getUserByNickname("testUser");

        assertThat(response.userId()).isEqualTo("abc-123");
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
        server.createContext("/user/stats/uid/abc-123/1", exchange -> writeJson(exchange, 429, """
                {
                  "code": 429,
                  "message": "Too Many Requests"
                }
                """));

        EternalReturnApiClient client = createClient("test-api-key");

        assertThatThrownBy(() -> client.getUserStats("abc-123", 1))
                .isInstanceOfSatisfying(EternalReturnApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.getMessage()).contains("Too Many Requests");
                });
    }

    @Test
    void getUserGamesSendsApiKeyAndReturnsMatchHistory() {
        server.createContext("/user/games/uid/abc-123", exchange -> {
            capturedRequests.add(CapturedRequest.from(exchange));
            writeJson(exchange, 200, """
                    {
                      "code": 200,
                      "userGames": [
                        {
                          "gameId": 98765,
                          "nickname": "testUser",
                          "seasonId": 39,
                          "matchingMode": 3,
                          "matchingTeamMode": 3,
                          "characterNum": 1,
                          "gameRank": 3,
                          "playerKill": 5,
                          "playerAssistant": 2,
                          "playerDeaths": 1,
                          "damageToPlayer": 12345,
                          "teamKill": 7,
                          "rankPoint": 1620,
                          "mmrBefore": 1574,
                          "mmrGain": 46,
                          "mmrAfter": 1620,
                          "startDtm": "2026-05-30T23:15:29.029+0900",
                          "playTime": 551,
                          "unusedField": "ignored"
                        }
                      ],
                      "next": 98765
                    }
                    """);
        });

        EternalReturnApiClient client = createClient("test-api-key");

        UserGamesResponse response = client.getUserGames("abc-123");

        assertThat(response.next()).isEqualTo(98765);
        assertThat(response.games())
                .singleElement()
                .satisfies(game -> {
                    assertThat(game.gameId()).isEqualTo(98765);
                    assertThat(game.nickname()).isEqualTo("testUser");
                    assertThat(game.seasonId()).isEqualTo(39);
                    assertThat(game.matchingMode()).isEqualTo(3);
                    assertThat(game.matchingTeamMode()).isEqualTo(3);
                    assertThat(game.characterNum()).isEqualTo(1);
                    assertThat(game.gameRank()).isEqualTo(3);
                    assertThat(game.playerKill()).isEqualTo(5);
                    assertThat(game.playerAssistant()).isEqualTo(2);
                    assertThat(game.playerDeaths()).isEqualTo(1);
                    assertThat(game.damageToPlayer()).isEqualTo(12345);
                    assertThat(game.teamKill()).isEqualTo(7);
                    assertThat(game.rankPoint()).isEqualTo(1620);
                    assertThat(game.mmrBefore()).isEqualTo(1574);
                    assertThat(game.mmrGain()).isEqualTo(46);
                    assertThat(game.mmrAfter()).isEqualTo(1620);
                    assertThat(game.startDtm()).isEqualTo("2026-05-30T23:15:29.029+0900");
                    assertThat(game.playTime()).isEqualTo(551);
                });
        assertThat(capturedRequests).hasSize(1);
        assertThat(capturedRequests.get(0).path()).isEqualTo("/user/games/uid/abc-123");
        assertThat(capturedRequests.get(0).query()).isNull();
        assertThat(capturedRequests.get(0).apiKey()).isEqualTo("test-api-key");
    }

    @Test
    void getUserRankSendsApiKeyAndReturnsRank() {
        server.createContext("/rank/uid/abc-123/28/1", exchange -> {
            capturedRequests.add(CapturedRequest.from(exchange));
            writeJson(exchange, 200, """
                    {
                      "code": 200,
                      "userRank": {
                        "rank": 123,
                        "rankScore": 4567,
                        "mmr": 4321
                      }
                    }
                    """);
        });

        EternalReturnApiClient client = createClient("test-api-key");

        Map<String, Object> response = client.getUserRank("abc-123", 28, 1);

        assertThat(response).containsEntry("code", 200);
        assertThat(response.get("userRank"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("rank", 123)
                .containsEntry("rankScore", 4567)
                .containsEntry("mmr", 4321);
        assertThat(capturedRequests).hasSize(1);
        assertThat(capturedRequests.get(0).path()).isEqualTo("/rank/uid/abc-123/28/1");
        assertThat(capturedRequests.get(0).query()).isNull();
        assertThat(capturedRequests.get(0).apiKey()).isEqualTo("test-api-key");
    }

    @Test
    void getUserOverviewCombinesUserRankAndGames() {
        server.createContext("/user/nickname", exchange -> {
            capturedRequests.add(CapturedRequest.from(exchange));
            writeJson(exchange, 200, """
                    {
                      "code": 200,
                      "message": "Success",
                      "user": {
                        "userId": "abc-123",
                        "nickname": "testUser"
                      }
                    }
                    """);
        });
        server.createContext("/rank/uid/abc-123/28/1", exchange -> {
            capturedRequests.add(CapturedRequest.from(exchange));
            writeJson(exchange, 200, """
                    {
                      "code": 200,
                      "userRank": {
                        "rank": 123,
                        "rankScore": 4567
                      }
                    }
                    """);
        });
        server.createContext("/user/games/uid/abc-123", exchange -> {
            capturedRequests.add(CapturedRequest.from(exchange));
            writeJson(exchange, 200, """
                    {
                      "code": 200,
                      "userGames": [
                        {
                          "gameId": 98765,
                          "nickname": "testUser",
                          "seasonId": 39,
                          "matchingMode": 3,
                          "matchingTeamMode": 3,
                          "characterNum": 1,
                          "gameRank": 3,
                          "playerKill": 5,
                          "playerAssistant": 2,
                          "playerDeaths": 1,
                          "damageToPlayer": 12345,
                          "teamKill": 7,
                          "rankPoint": 1620,
                          "startDtm": "2026-05-30T23:15:29.029+0900",
                          "playTime": 551
                        }
                      ],
                      "next": 98765
                    }
                    """);
        });

        EternalReturnApiClient client = createClient("test-api-key");

        UserOverviewResponse response = client.getUserOverview("testUser", 28, 1);

        assertThat(response.user().userId()).isEqualTo("abc-123");
        assertThat(response.user().nickname()).isEqualTo("testUser");
        assertThat(response.rank()).containsEntry("code", 200);
        assertThat(response.rank().get("userRank"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("rank", 123)
                .containsEntry("rankScore", 4567);
        assertThat(response.games().next()).isEqualTo(98765);
        assertThat(response.games().games())
                .singleElement()
                .satisfies(game -> {
                    assertThat(game.gameId()).isEqualTo(98765);
                    assertThat(game.gameRank()).isEqualTo(3);
                    assertThat(game.playerKill()).isEqualTo(5);
                });
        assertThat(response.recentStats().gameCount()).isEqualTo(1);
        assertThat(response.recentStats().winCount()).isZero();
        assertThat(response.recentStats().top3Count()).isEqualTo(1);
        assertThat(response.recentStats().top3Rate()).isEqualTo(1.0);
        assertThat(response.recentStats().averageRank()).isEqualTo(3.0);
        assertThat(response.recentStats().averageKills()).isEqualTo(5.0);
        assertThat(response.recentStats().averageAssists()).isEqualTo(2.0);
        assertThat(response.recentStats().averageDeaths()).isEqualTo(1.0);
        assertThat(response.recentStats().averageKda()).isEqualTo(7.0);
        assertThat(response.recentStats().averageDamageToPlayer()).isEqualTo(12345.0);
        assertThat(response.recentStats().totalMmrGain()).isNull();
        assertThat(response.recentStats().mostPlayedCharacterNum()).isEqualTo(1);
        assertThat(capturedRequests).extracting(CapturedRequest::path)
                .containsExactly(
                        "/user/nickname",
                        "/rank/uid/abc-123/28/1",
                        "/user/games/uid/abc-123"
                );
        assertThat(capturedRequests).allSatisfy(request -> assertThat(request.apiKey()).isEqualTo("test-api-key"));
    }

    @Test
    void apiKeyIsRequiredBeforeCallingRemoteApi() {
        EternalReturnApiClient client = createClient("");

        assertThatThrownBy(() -> client.getUserGames("abc-123"))
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
