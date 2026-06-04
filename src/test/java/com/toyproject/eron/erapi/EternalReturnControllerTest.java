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

import com.toyproject.eron.erapi.dto.UserGameSummary;
import com.toyproject.eron.erapi.dto.UserGamesResponse;
import com.toyproject.eron.erapi.dto.UserOverviewResponse;
import com.toyproject.eron.erapi.dto.UserSearchResponse;
import com.toyproject.eron.global.error.GlobalExceptionHandler;

class EternalReturnControllerTest {

    private EternalReturnApiClient eternalReturnApiClient;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        eternalReturnApiClient = Mockito.mock(EternalReturnApiClient.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EternalReturnController(eternalReturnApiClient))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchUserReturnsMappedUser() throws Exception {
        when(eternalReturnApiClient.getUserByNickname("testUser"))
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
        when(eternalReturnApiClient.getUserGames("abc-123"))
                .thenReturn(new UserGamesResponse(
                        List.of(new UserGameSummary(
                                98765,
                                "testUser",
                                39,
                                3,
                                3,
                                1,
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
        when(eternalReturnApiClient.getUserRank("abc-123", 28, 1))
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
    void getUserOverviewReturnsUserRankAndGames() throws Exception {
        when(eternalReturnApiClient.getUserOverview("testUser", 28, 1))
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
                .andExpect(jsonPath("$.games.games[0].playerKill").value(5));
    }

    @Test
    void apiExceptionReturnsStructuredErrorResponse() throws Exception {
        when(eternalReturnApiClient.getUserGames("abc-123"))
                .thenThrow(new EternalReturnApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too Many Requests"
                ));

        mockMvc.perform(get("/api/er/users/abc-123/games"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Too Many Requests"));
    }

    @Test
    void missingRequiredQueryParameterReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/er/users/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }
}
