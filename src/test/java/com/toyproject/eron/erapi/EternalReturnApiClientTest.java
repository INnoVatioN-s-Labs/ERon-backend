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
import com.toyproject.eron.erapi.dto.EquipmentSummary;
import com.toyproject.eron.erapi.dto.GameDetailResponse;
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
                          "characterNum": 68,
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
        createCharacterDataContext();
        createEquipmentDataContexts();

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
                    assertThat(game.characterNum()).isEqualTo(68);
                    assertThat(game.characterName()).isEqualTo("Alonso");
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
        assertThat(capturedRequests).hasSize(2);
        assertThat(capturedRequests.get(0).path()).isEqualTo("/user/games/uid/abc-123");
        assertThat(capturedRequests.get(0).query()).isNull();
        assertThat(capturedRequests.get(0).apiKey()).isEqualTo("test-api-key");
        assertThat(capturedRequests.get(1).path()).isEqualTo("/data/Character");
        assertThat(capturedRequests.get(1).apiKey()).isEqualTo("test-api-key");
    }

    @Test
    void apiCharacterNameOverridesLocalFallback() {
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
                          "characterNum": 68,
                          "gameRank": 3
                        }
                      ],
                      "next": 98765
                    }
                    """);
        });
        createCharacterDataContextWithAlonsoOverride();

        EternalReturnApiClient client = createClient("test-api-key");

        UserGamesResponse response = client.getUserGames("abc-123");

        assertThat(response.games())
                .singleElement()
                .satisfies(game -> {
                    assertThat(game.characterNum()).isEqualTo(68);
                    assertThat(game.characterName()).isEqualTo("Api Alonso");
                });
    }

    @Test
    void getUserGamesReturnsEmptyListWhenUserGamesIsMissing() {
        server.createContext("/user/games/uid/abc-123", exchange -> {
            capturedRequests.add(CapturedRequest.from(exchange));
            writeJson(exchange, 200, """
                    {
                      "code": 200,
                      "next": 98765
                    }
                    """);
        });
        createCharacterDataContext();

        EternalReturnApiClient client = createClient("test-api-key");

        UserGamesResponse response = client.getUserGames("abc-123");

        assertThat(response.games()).isEmpty();
        assertThat(response.next()).isEqualTo(98765);
    }

    @Test
    void getUserGamesUsesFallbackNamesWhenCharacterMetadataFails() {
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
                          "characterNum": 68,
                          "gameRank": 3
                        },
                        {
                          "gameId": 98766,
                          "nickname": "unknownCharacterUser",
                          "seasonId": 39,
                          "matchingMode": 3,
                          "matchingTeamMode": 3,
                          "characterNum": 999,
                          "gameRank": 4
                        }
                      ]
                    }
                    """);
        });
        createFailingDataContext("Character");

        EternalReturnApiClient client = createClient("test-api-key");

        UserGamesResponse response = client.getUserGames("abc-123");

        assertThat(response.games()).hasSize(2);
        assertThat(response.games().get(0).characterName()).isEqualTo("Alonso");
        assertThat(response.games().get(1).characterName()).isEqualTo("Unknown Character (999)");
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
    void getGameSendsApiKeyAndReturnsMappedGameDetail() {
        server.createContext("/games/98765", exchange -> {
            capturedRequests.add(CapturedRequest.from(exchange));
            writeJson(exchange, 200, """
                    {
                      "code": 200,
                      "message": "Success",
                      "userGames": [
                        {
                          "gameId": 98765,
                          "nickname": "winner",
                          "seasonId": 39,
                          "matchingMode": 3,
                          "matchingTeamMode": 3,
                          "characterNum": 22,
                          "characterLevel": 20,
                          "gameRank": 1,
                          "playerKill": 12,
                          "playerAssistant": 11,
                          "playerDeaths": 6,
                          "monsterKill": 3,
                          "teamKill": 25,
                          "damageToPlayer": 35142,
                          "damageFromPlayer": 16269,
                          "damageToMonster": 5381,
                          "healAmount": 3253,
                          "protectAbsorb": 3171,
                          "bestWeapon": 7,
                          "bestWeaponLevel": 18,
                          "rankPoint": 0,
                          "victory": 1,
                          "startDtm": "2026-06-09T13:44:20.020+0900",
                          "duration": 614,
                          "playTime": 609,
                          "matchSize": 8,
                          "teamNumber": 1,
                          "equipment": {
                            "0": 114702,
                            "1": 108701
                          },
                          "equipmentGrade": {
                            "0": 6,
                            "1": 5
                          }
                        },
                        {
                          "gameId": 98765,
                          "nickname": "runnerUp",
                          "seasonId": 39,
                          "matchingMode": 3,
                          "matchingTeamMode": 3,
                          "characterNum": 68,
                          "characterLevel": 20,
                          "gameRank": 2,
                          "playerKill": 0,
                          "playerAssistant": 24,
                          "playerDeaths": 5,
                          "monsterKill": 0,
                          "teamKill": 27,
                          "damageToPlayer": 13759,
                          "damageFromPlayer": 36406,
                          "damageToMonster": 850,
                          "healAmount": 16163,
                          "protectAbsorb": 13134,
                          "bestWeapon": 4,
                          "bestWeaponLevel": 16,
                          "rankPoint": 0,
                          "victory": 0,
                          "startDtm": "2026-06-09T13:44:20.020+0900",
                          "duration": 614,
                          "playTime": 609,
                          "matchSize": 8,
                          "teamNumber": 2,
                          "equipment": {
                            "0": 109501
                          },
                          "equipmentGrade": {
                            "0": 6
                          }
                        }
                      ]
                    }
                    """);
        });
        createCharacterDataContext();
        createEquipmentDataContexts();

        EternalReturnApiClient client = createClient("test-api-key");

        GameDetailResponse response = client.getGame(98765);

        assertThat(response.gameId()).isEqualTo(98765);
        assertThat(response.seasonId()).isEqualTo(39);
        assertThat(response.matchingMode()).isEqualTo(3);
        assertThat(response.matchingTeamMode()).isEqualTo(3);
        assertThat(response.startDtm()).isEqualTo("2026-06-09T13:44:20.020+0900");
        assertThat(response.duration()).isEqualTo(614);
        assertThat(response.playTime()).isEqualTo(609);
        assertThat(response.matchSize()).isEqualTo(8);
        assertThat(response.participantCount()).isEqualTo(2);
        assertThat(response.participants())
                .first()
                .satisfies(participant -> {
                    assertThat(participant.nickname()).isEqualTo("winner");
                    assertThat(participant.teamNumber()).isEqualTo(1);
                    assertThat(participant.gameRank()).isEqualTo(1);
                    assertThat(participant.characterNum()).isEqualTo(22);
                    assertThat(participant.characterName()).isEqualTo("Luke");
                    assertThat(participant.characterLevel()).isEqualTo(20);
                    assertThat(participant.playerKill()).isEqualTo(12);
                    assertThat(participant.playerAssistant()).isEqualTo(11);
                    assertThat(participant.playerDeaths()).isEqualTo(6);
                    assertThat(participant.monsterKill()).isEqualTo(3);
                    assertThat(participant.teamKill()).isEqualTo(25);
                    assertThat(participant.damageToPlayer()).isEqualTo(35142);
                    assertThat(participant.damageFromPlayer()).isEqualTo(16269);
                    assertThat(participant.damageToMonster()).isEqualTo(5381);
                    assertThat(participant.healAmount()).isEqualTo(3253);
                    assertThat(participant.protectAbsorb()).isEqualTo(3171);
                    assertThat(participant.bestWeapon()).isEqualTo(7);
                    assertThat(participant.bestWeaponLevel()).isEqualTo(18);
                    assertThat(participant.rankPoint()).isZero();
                    assertThat(participant.victory()).isEqualTo(1);
                    assertThat(participant.playTime()).isEqualTo(609);
                    assertThat(participant.equipment()).containsEntry(
                            "0",
                            new EquipmentSummary(114702, "Longbow", 6)
                    );
                    assertThat(participant.equipment()).containsEntry(
                            "1",
                            new EquipmentSummary(108701, "Unknown Item (108701)", 5)
                    );
                });
        assertThat(response.participants())
                .element(1)
                .satisfies(participant -> {
                    assertThat(participant.characterNum()).isEqualTo(68);
                    assertThat(participant.characterName()).isEqualTo("Alonso");
                });
        assertThat(capturedRequests).hasSize(4);
        assertThat(capturedRequests.get(0).path()).isEqualTo("/games/98765");
        assertThat(capturedRequests.get(0).query()).isNull();
        assertThat(capturedRequests.get(0).apiKey()).isEqualTo("test-api-key");
        assertThat(capturedRequests.get(1).path()).isEqualTo("/data/Character");
        assertThat(capturedRequests.get(1).apiKey()).isEqualTo("test-api-key");
        assertThat(capturedRequests.get(2).path()).isEqualTo("/data/ItemWeapon");
        assertThat(capturedRequests.get(2).apiKey()).isEqualTo("test-api-key");
        assertThat(capturedRequests.get(3).path()).isEqualTo("/data/ItemArmor");
        assertThat(capturedRequests.get(3).apiKey()).isEqualTo("test-api-key");
    }

    @Test
    void metadataNamesAreCachedAcrossGameResponses() {
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
        server.createContext("/games/98765", exchange -> {
            capturedRequests.add(CapturedRequest.from(exchange));
            writeJson(exchange, 200, """
                    {
                      "code": 200,
                      "message": "Success",
                      "userGames": [
                        {
                          "gameId": 98765,
                          "nickname": "winner",
                          "seasonId": 39,
                          "matchingMode": 3,
                          "matchingTeamMode": 3,
                          "characterNum": 22,
                          "characterLevel": 20,
                          "gameRank": 1,
                          "playerKill": 12,
                          "playerAssistant": 11,
                          "playerDeaths": 6,
                          "teamKill": 25,
                          "damageToPlayer": 35142,
                          "startDtm": "2026-06-09T13:44:20.020+0900",
                          "duration": 614,
                          "playTime": 609,
                          "matchSize": 8,
                          "teamNumber": 1,
                          "equipment": {
                            "0": 114702
                          },
                          "equipmentGrade": {
                            "0": 6
                          }
                        }
                      ]
                    }
                    """);
        });
        createCharacterDataContext();
        createEquipmentDataContexts();
        EternalReturnApiClient client = createClient("test-api-key");

        UserGamesResponse games = client.getUserGames("abc-123");
        GameDetailResponse detail = client.getGame(98765);

        assertThat(games.games())
                .singleElement()
                .satisfies(game -> assertThat(game.characterName()).isEqualTo("Jackie"));
        assertThat(detail.participants())
                .singleElement()
                .satisfies(participant -> {
                    assertThat(participant.characterName()).isEqualTo("Luke");
                    assertThat(participant.equipment()).containsEntry(
                            "0",
                            new EquipmentSummary(114702, "Longbow", 6)
                    );
                });
        assertThat(capturedRequests).extracting(CapturedRequest::path)
                .containsExactly(
                        "/user/games/uid/abc-123",
                        "/data/Character",
                        "/games/98765",
                        "/data/ItemWeapon",
                        "/data/ItemArmor"
                );
    }

    @Test
    void getGameUsesUnknownItemsWhenEquipmentMetadataFails() {
        server.createContext("/games/98765", exchange -> {
            capturedRequests.add(CapturedRequest.from(exchange));
            writeJson(exchange, 200, """
                    {
                      "code": 200,
                      "message": "Success",
                      "userGames": [
                        {
                          "gameId": 98765,
                          "nickname": "winner",
                          "seasonId": 39,
                          "matchingMode": 3,
                          "matchingTeamMode": 3,
                          "characterNum": 22,
                          "characterLevel": 20,
                          "gameRank": 1,
                          "startDtm": "2026-06-09T13:44:20.020+0900",
                          "duration": 614,
                          "playTime": 609,
                          "matchSize": 8,
                          "teamNumber": 1,
                          "equipment": {
                            "0": 114702,
                            "1": 109501
                          },
                          "equipmentGrade": {
                            "0": 6,
                            "1": 5
                          }
                        }
                      ]
                    }
                    """);
        });
        createCharacterDataContext();
        createFailingDataContext("ItemWeapon");
        createFailingDataContext("ItemArmor");

        EternalReturnApiClient client = createClient("test-api-key");

        GameDetailResponse response = client.getGame(98765);

        assertThat(response.participants())
                .singleElement()
                .satisfies(participant -> assertThat(participant.equipment())
                        .containsEntry("0", new EquipmentSummary(114702, "Unknown Item (114702)", 6))
                        .containsEntry("1", new EquipmentSummary(109501, "Unknown Item (109501)", 5)));
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
                          "characterNum": 68,
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
        createCharacterDataContext();

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
                    assertThat(game.characterName()).isEqualTo("Alonso");
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
        assertThat(response.recentStats().mostPlayedCharacterNum()).isEqualTo(68);
        assertThat(response.recentStats().mostPlayedCharacterName()).isEqualTo("Alonso");
        assertThat(capturedRequests).extracting(CapturedRequest::path)
                .containsExactly(
                        "/user/nickname",
                        "/rank/uid/abc-123/28/1",
                        "/user/games/uid/abc-123",
                        "/data/Character"
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

    private void createCharacterDataContext() {
        server.createContext("/data/Character", exchange -> {
            capturedRequests.add(CapturedRequest.from(exchange));
            writeJson(exchange, 200, """
                    {
                      "code": 200,
                      "message": "Success",
                      "data": [
                        {
                          "code": 1,
                          "name": "Jackie"
                        },
                        {
                          "code": 22,
                          "name": "Luke"
                        },
                        {
                          "code": 45,
                          "name": "Celine"
                        }
                      ]
                    }
                    """);
        });
    }

    private void createCharacterDataContextWithAlonsoOverride() {
        server.createContext("/data/Character", exchange -> {
            capturedRequests.add(CapturedRequest.from(exchange));
            writeJson(exchange, 200, """
                    {
                      "code": 200,
                      "message": "Success",
                      "data": [
                        {
                          "code": 68,
                          "name": "Api Alonso"
                        }
                      ]
                    }
                    """);
        });
    }

    private void createEquipmentDataContexts() {
        server.createContext("/data/ItemWeapon", exchange -> {
            capturedRequests.add(CapturedRequest.from(exchange));
            writeJson(exchange, 200, """
                    {
                      "code": 200,
                      "message": "Success",
                      "data": [
                        {
                          "code": 114702,
                          "name": "Longbow"
                        }
                      ]
                    }
                    """);
        });
        server.createContext("/data/ItemArmor", exchange -> {
            capturedRequests.add(CapturedRequest.from(exchange));
            writeJson(exchange, 200, """
                    {
                      "code": 200,
                      "message": "Success",
                      "data": [
                        {
                          "code": 109501,
                          "name": "Commander's Armor"
                        }
                      ]
                    }
                    """);
        });
    }

    private void createFailingDataContext(String metaType) {
        server.createContext("/data/" + metaType, exchange -> {
            capturedRequests.add(CapturedRequest.from(exchange));
            writeJson(exchange, 500, """
                    {
                      "code": 500,
                      "message": "Metadata API failed"
                    }
                    """);
        });
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
