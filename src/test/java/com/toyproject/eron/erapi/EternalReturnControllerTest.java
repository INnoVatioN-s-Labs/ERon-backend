package com.toyproject.eron.erapi;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.toyproject.eron.erapi.dto.EquipmentSummary;
import com.toyproject.eron.erapi.dto.GameDetailResponse;
import com.toyproject.eron.erapi.dto.GameParticipantSummary;
import com.toyproject.eron.erapi.dto.UserGameSummary;
import com.toyproject.eron.erapi.dto.UserGamesResponse;
import com.toyproject.eron.erapi.dto.UserOverviewResponse;
import com.toyproject.eron.erapi.dto.UserRecentStatsResponse;
import com.toyproject.eron.erapi.dto.UserSearchResponse;
import com.toyproject.eron.global.error.GlobalExceptionHandler;

class EternalReturnControllerTest {

    private EternalReturnService eternalReturnService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        eternalReturnService = Mockito.mock(EternalReturnService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EternalReturnController(eternalReturnService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchUserReturnsMappedUser() throws Exception {
        when(eternalReturnService.getUserByNickname("testUser"))
                .thenReturn(new UserSearchResponse(
                        "abc-123",
                        "testUser",
                        java.util.Map.of(
                                "user", java.util.Map.of(
                                        "userId", "abc-123",
                                        "nickname", "testUser"
                                )
                        )
                ));

        mockMvc.perform(get("/api/er/users/search").param("nickname", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userNum").doesNotExist())
                .andExpect(jsonPath("$.userId").value("abc-123"))
                .andExpect(jsonPath("$.nickname").value("testUser"))
                .andExpect(jsonPath("$.raw.user.userId").value("abc-123"));
    }

    @Test
    void getUserGamesReturnsMatchHistory() throws Exception {
        when(eternalReturnService.getUserGames("abc-123"))
                .thenReturn(new UserGamesResponse(
                        List.of(new UserGameSummary(
                                98765,
                                "testUser",
                                39,
                                3,
                                3,
                                1,
                                "Jackie",
                                3,
                                5,
                                2,
                                1,
                                12345,
                                7,
                                1620,
                                1574,
                                46,
                                1620,
                                "2026-05-30T23:15:29.029+0900",
                                551
                        )),
                        98765
                ));

        mockMvc.perform(get("/api/er/users/abc-123/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.next").value(98765))
                .andExpect(jsonPath("$.games[0].gameId").value(98765))
                .andExpect(jsonPath("$.games[0].nickname").value("testUser"))
                .andExpect(jsonPath("$.games[0].seasonId").value(39))
                .andExpect(jsonPath("$.games[0].matchingMode").value(3))
                .andExpect(jsonPath("$.games[0].matchingTeamMode").value(3))
                .andExpect(jsonPath("$.games[0].characterNum").value(1))
                .andExpect(jsonPath("$.games[0].characterName").value("Jackie"))
                .andExpect(jsonPath("$.games[0].gameRank").value(3))
                .andExpect(jsonPath("$.games[0].playerKill").value(5))
                .andExpect(jsonPath("$.games[0].playerAssistant").value(2))
                .andExpect(jsonPath("$.games[0].playerDeaths").value(1))
                .andExpect(jsonPath("$.games[0].damageToPlayer").value(12345))
                .andExpect(jsonPath("$.games[0].teamKill").value(7))
                .andExpect(jsonPath("$.games[0].rankPoint").value(1620))
                .andExpect(jsonPath("$.games[0].mmrBefore").value(1574))
                .andExpect(jsonPath("$.games[0].mmrGain").value(46))
                .andExpect(jsonPath("$.games[0].mmrAfter").value(1620))
                .andExpect(jsonPath("$.games[0].startDtm").value("2026-05-30T23:15:29.029+0900"))
                .andExpect(jsonPath("$.games[0].playTime").value(551));
    }

    @Test
    void getUserRankReturnsRank() throws Exception {
        when(eternalReturnService.getUserRank("abc-123", 28, 1))
                .thenReturn(Map.of(
                        "code", 200,
                        "userRank", Map.of(
                                "rank", 123,
                                "rankScore", 4567,
                                "mmr", 4321
                        )
                ));

        mockMvc.perform(get("/api/er/users/abc-123/rank")
                        .param("seasonId", "28")
                        .param("matchingTeamMode", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.userRank.rank").value(123))
                .andExpect(jsonPath("$.userRank.rankScore").value(4567))
                .andExpect(jsonPath("$.userRank.mmr").value(4321));
    }

    @Test
    void getTopRankingsReturnsRankings() throws Exception {
        when(eternalReturnService.getTopRankings(39, 3))
                .thenReturn(Map.of(
                        "code", 200,
                        "topRanks", List.of(
                                Map.of(
                                        "rank", 1,
                                        "nickname", "topUser",
                                        "rankScore", 8320,
                                        "tier", "이터니티"
                                )
                        )
                ));

        mockMvc.perform(get("/api/er/rankings/top")
                        .param("seasonId", "39")
                        .param("matchingTeamMode", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.topRanks[0].rank").value(1))
                .andExpect(jsonPath("$.topRanks[0].nickname").value("topUser"))
                .andExpect(jsonPath("$.topRanks[0].rankScore").value(8320))
                .andExpect(jsonPath("$.topRanks[0].tier").value("이터니티"));
    }

    @Test
    void getCharacterMetaReturnsCharacterStats() throws Exception {
        when(eternalReturnService.getCharacterMeta(39, 3, "이터니티"))
                .thenReturn(Map.of(
                        "seasonId", 39,
                        "matchingTeamMode", 3,
                        "tier", "이터니티",
                        "sampleGameCount", 3,
                        "characters", List.of(Map.of(
                                "characterNum", 1,
                                "characterName", "Jackie",
                                "gameCount", 2,
                                "pickRate", 0.67,
                                "top3Rate", 0.5,
                                "averageRank", 2.5,
                                "averageKills", 4.0
                        ))
                ));

        mockMvc.perform(get("/api/er/meta/characters")
                        .param("seasonId", "39")
                        .param("matchingTeamMode", "3")
                        .param("tier", "이터니티"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonId").value(39))
                .andExpect(jsonPath("$.matchingTeamMode").value(3))
                .andExpect(jsonPath("$.tier").value("이터니티"))
                .andExpect(jsonPath("$.sampleGameCount").value(3))
                .andExpect(jsonPath("$.characters[0].characterNum").value(1))
                .andExpect(jsonPath("$.characters[0].characterName").value("Jackie"))
                .andExpect(jsonPath("$.characters[0].pickRate").value(0.67))
                .andExpect(jsonPath("$.characters[0].averageRank").value(2.5));
    }

    @Test
    void getUserOverviewReturnsUserRankAndGames() throws Exception {
        when(eternalReturnService.getUserOverview("testUser", 28, 1))
                .thenReturn(new UserOverviewResponse(
                        new UserSearchResponse(
                                "abc-123",
                                "testUser",
                                Map.of(
                                        "user", Map.of(
                                                "userId", "abc-123",
                                                "nickname", "testUser"
                                        )
                                )
                        ),
                        Map.of(
                                "code", 200,
                                "userRank", Map.of(
                                        "rank", 123,
                                        "rankScore", 4567
                                )
                        ),
                        new UserGamesResponse(
                                List.of(new UserGameSummary(
                                        98765,
                                        "testUser",
                                        39,
                                        3,
                                        3,
                                        1,
                                        "Jackie",
                                        3,
                                        5,
                                        2,
                                        1,
                                        12345,
                                        7,
                                        1620,
                                        1574,
                                        46,
                                        1620,
                                        "2026-05-30T23:15:29.029+0900",
                                        551
                                )),
                                98765
                        ),
                        new UserRecentStatsResponse(
                                1,
                                0,
                                0.0,
                                1,
                                1.0,
                                3.0,
                                5.0,
                                2.0,
                                1.0,
                                7.0,
                                12345.0,
                                46,
                                1,
                                "Jackie"
                        )
                ));

        mockMvc.perform(get("/api/er/users/overview")
                        .param("nickname", "testUser")
                        .param("seasonId", "28")
                        .param("matchingTeamMode", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.userId").value("abc-123"))
                .andExpect(jsonPath("$.user.nickname").value("testUser"))
                .andExpect(jsonPath("$.rank.code").value(200))
                .andExpect(jsonPath("$.rank.userRank.rank").value(123))
                .andExpect(jsonPath("$.rank.userRank.rankScore").value(4567))
                .andExpect(jsonPath("$.games.next").value(98765))
                .andExpect(jsonPath("$.games.games[0].gameId").value(98765))
                .andExpect(jsonPath("$.games.games[0].gameRank").value(3))
                .andExpect(jsonPath("$.games.games[0].characterName").value("Jackie"))
                .andExpect(jsonPath("$.games.games[0].playerKill").value(5))
                .andExpect(jsonPath("$.recentStats.gameCount").value(1))
                .andExpect(jsonPath("$.recentStats.top3Count").value(1))
                .andExpect(jsonPath("$.recentStats.top3Rate").value(1.0))
                .andExpect(jsonPath("$.recentStats.averageKda").value(7.0))
                .andExpect(jsonPath("$.recentStats.totalMmrGain").value(46))
                .andExpect(jsonPath("$.recentStats.mostPlayedCharacterNum").value(1))
                .andExpect(jsonPath("$.recentStats.mostPlayedCharacterName").value("Jackie"));
    }

    @Test
    void getGameReturnsMappedGameDetail() throws Exception {
        when(eternalReturnService.getGame(98765))
                .thenReturn(new GameDetailResponse(
                        98765,
                        39,
                        3,
                        3,
                        "2026-06-09T13:44:20.020+0900",
                        614,
                        609,
                        8,
                        2,
                        List.of(
                                new GameParticipantSummary(
                                        "winner",
                                        1,
                                        1,
                                        22,
                                        "Luke",
                                        20,
                                        12,
                                        11,
                                        6,
                                        3,
                                        25,
                                        35142,
                                        16269,
                                        5381,
                                        3253,
                                        3171,
                                        7,
                                        18,
                                        0,
                                        1,
                                        609,
                                        Map.of("0", new EquipmentSummary(114702, "Longbow", 6))
                                ),
                                new GameParticipantSummary(
                                        "runnerUp",
                                        2,
                                        2,
                                        45,
                                        "Celine",
                                        20,
                                        0,
                                        24,
                                        5,
                                        0,
                                        27,
                                        13759,
                                        36406,
                                        850,
                                        16163,
                                        13134,
                                        4,
                                        16,
                                        0,
                                        0,
                                        609,
                                        Map.of("0", new EquipmentSummary(109501, "Commander's Armor", 6))
                                )
                        )
                ));

        mockMvc.perform(get("/api/er/games/98765"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(98765))
                .andExpect(jsonPath("$.seasonId").value(39))
                .andExpect(jsonPath("$.matchingMode").value(3))
                .andExpect(jsonPath("$.matchingTeamMode").value(3))
                .andExpect(jsonPath("$.startDtm").value("2026-06-09T13:44:20.020+0900"))
                .andExpect(jsonPath("$.duration").value(614))
                .andExpect(jsonPath("$.playTime").value(609))
                .andExpect(jsonPath("$.matchSize").value(8))
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.participants[0].nickname").value("winner"))
                .andExpect(jsonPath("$.participants[0].teamNumber").value(1))
                .andExpect(jsonPath("$.participants[0].gameRank").value(1))
                .andExpect(jsonPath("$.participants[0].characterNum").value(22))
                .andExpect(jsonPath("$.participants[0].characterName").value("Luke"))
                .andExpect(jsonPath("$.participants[0].playerKill").value(12))
                .andExpect(jsonPath("$.participants[0].playerAssistant").value(11))
                .andExpect(jsonPath("$.participants[0].playerDeaths").value(6))
                .andExpect(jsonPath("$.participants[0].damageToPlayer").value(35142))
                .andExpect(jsonPath("$.participants[0].equipment.0.itemCode").value(114702))
                .andExpect(jsonPath("$.participants[0].equipment.0.itemName").value("Longbow"))
                .andExpect(jsonPath("$.participants[0].equipment.0.itemGrade").value(6))
                .andExpect(jsonPath("$.participants[0].equipmentList[0].itemCode").value(114702))
                .andExpect(jsonPath("$.participants[0].equipmentList[0].itemName").value("Longbow"))
                .andExpect(jsonPath("$.participants[0].equipmentList[0].itemGrade").value(6))
                .andExpect(jsonPath("$.participants[1].nickname").value("runnerUp"))
                .andExpect(jsonPath("$.participants[1].victory").value(0))
                .andExpect(jsonPath("$.raw").doesNotExist())
                .andExpect(jsonPath("$.participants[0].raw").doesNotExist());
    }

    @Test
    void apiExceptionReturnsStructuredErrorResponse() throws Exception {
        when(eternalReturnService.getUserGames("abc-123"))
                .thenThrow(new EternalReturnApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too Many Requests"
                ));

        mockMvc.perform(get("/api/er/users/abc-123/games"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Too Many Requests"));
    }

    @Test
    void notFoundApiExceptionReturnsStructuredErrorResponse() throws Exception {
        when(eternalReturnService.getUserByNickname("unknown"))
                .thenThrow(new EternalReturnApiException(
                        HttpStatus.NOT_FOUND,
                        "Eternal Return user not found."
                ));

        mockMvc.perform(get("/api/er/users/search").param("nickname", "unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Eternal Return user not found."));
    }

    @Test
    void missingRequiredQueryParameterReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/er/users/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Required request parameter 'nickname' for method parameter type String is not present"));
    }

    @Test
    void invalidQueryParameterTypeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/er/users/abc-123/rank")
                        .param("seasonId", "not-a-number")
                        .param("matchingTeamMode", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid value 'not-a-number' for request parameter 'seasonId'. Expected type: int."));
    }

    @Test
    void apiKeyConfigurationErrorReturnsStructuredErrorResponse() throws Exception {
        when(eternalReturnService.getUserByNickname("testUser"))
                .thenThrow(new EternalReturnApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "ER_API_KEY environment variable is not configured."
                ));

        mockMvc.perform(get("/api/er/users/search").param("nickname", "testUser"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("ER_API_KEY environment variable is not configured."));
    }
}
