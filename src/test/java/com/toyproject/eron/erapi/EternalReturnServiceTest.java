package com.toyproject.eron.erapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import com.toyproject.eron.erapi.dto.GameDetailResponse;
import com.toyproject.eron.erapi.dto.TopRankingsResponse;
import com.toyproject.eron.erapi.dto.UserGameSummary;
import com.toyproject.eron.erapi.dto.UserGamesResponse;
import com.toyproject.eron.erapi.dto.UserOverviewResponse;
import com.toyproject.eron.erapi.dto.UserSearchResponse;

class EternalReturnServiceTest {

    private final EternalReturnApiClient eternalReturnApiClient = Mockito.mock(EternalReturnApiClient.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-06-17T00:00:00Z"));
    private final EternalReturnService eternalReturnService =
            new EternalReturnService(eternalReturnApiClient, Duration.ofSeconds(30), clock);

    @Test
    void getUserByNicknameDelegatesToApiClient() {
        UserSearchResponse expected = new UserSearchResponse(
                "abc-123",
                "testUser",
                Map.of("code", 200)
        );
        when(eternalReturnApiClient.getUserByNickname("testUser")).thenReturn(expected);

        UserSearchResponse response = eternalReturnService.getUserByNickname("testUser");

        assertThat(response).isSameAs(expected);
        verify(eternalReturnApiClient).getUserByNickname("testUser");
    }

    @Test
    void getUserGamesCachesResponseWithinTtl() {
        UserGamesResponse expected = userGamesResponse(98765);
        when(eternalReturnApiClient.getUserGames("abc-123")).thenReturn(expected);

        UserGamesResponse firstResponse = eternalReturnService.getUserGames("abc-123");
        UserGamesResponse secondResponse = eternalReturnService.getUserGames("abc-123");

        assertThat(firstResponse).isSameAs(expected);
        assertThat(secondResponse).isSameAs(expected);
        verify(eternalReturnApiClient, times(1)).getUserGames("abc-123");
    }

    @Test
    void getUserGamesReloadsResponseAfterCacheExpires() {
        UserGamesResponse first = userGamesResponse(98765);
        UserGamesResponse second = userGamesResponse(98766);
        when(eternalReturnApiClient.getUserGames("abc-123")).thenReturn(first, second);

        UserGamesResponse firstResponse = eternalReturnService.getUserGames("abc-123");
        clock.advance(Duration.ofSeconds(31));
        UserGamesResponse secondResponse = eternalReturnService.getUserGames("abc-123");

        assertThat(firstResponse).isSameAs(first);
        assertThat(secondResponse).isSameAs(second);
        verify(eternalReturnApiClient, times(2)).getUserGames("abc-123");
    }

    @Test
    void getUserGamesBypassesCacheWhenTtlIsZero() {
        EternalReturnService serviceWithoutCache =
                new EternalReturnService(eternalReturnApiClient, Duration.ZERO, clock);
        UserGamesResponse first = userGamesResponse(98765);
        UserGamesResponse second = userGamesResponse(98766);
        when(eternalReturnApiClient.getUserGames("abc-123")).thenReturn(first, second);

        UserGamesResponse firstResponse = serviceWithoutCache.getUserGames("abc-123");
        UserGamesResponse secondResponse = serviceWithoutCache.getUserGames("abc-123");

        assertThat(firstResponse).isSameAs(first);
        assertThat(secondResponse).isSameAs(second);
        verify(eternalReturnApiClient, times(2)).getUserGames("abc-123");
    }

    @Test
    void getUserOverviewKeepsRecentGamesAndUsesOnlyRankedGamesForStats() {
        when(eternalReturnApiClient.getUserByNickname("testUser"))
                .thenReturn(new UserSearchResponse("abc-123", "testUser", Map.of()));
        when(eternalReturnApiClient.getUserRank("abc-123", 39, 3))
                .thenReturn(Map.of("userRank", Map.of("rank", 123)));
        when(eternalReturnApiClient.getUserGames("abc-123"))
                .thenReturn(new UserGamesResponse(
                        List.of(
                                userGame(98765, 1, "Jackie", 2, 5),
                                userGameWithSeason(98766, 0, 22, "Luke", 1, 20)
                        ),
                        98766L
                ));

        UserOverviewResponse response = eternalReturnService.getUserOverview("testUser", 39, 3);

        assertThat(response.games().games()).hasSize(2);
        assertThat(response.recentStats().gameCount()).isEqualTo(1);
        assertThat(response.recentStats().top3Count()).isEqualTo(1);
        assertThat(response.recentStats().averageKills()).isEqualTo(5.0);
    }

    @Test
    void getUserGamesIncludesLimitedGameDetailsWhenRequested() {
        UserGamesResponse games = new UserGamesResponse(
                List.of(
                        userGame(98765, 1, "Jackie", 3, 5),
                        userGame(98766, 22, "Luke", 1, 7)
                ),
                98766L
        );
        GameDetailResponse detail = gameDetailResponse(98765);
        when(eternalReturnApiClient.getUserGames("abc-123")).thenReturn(games);
        when(eternalReturnApiClient.getGame(98765)).thenReturn(detail);

        UserGamesResponse response = eternalReturnService.getUserGames("abc-123", true, 1);

        assertThat(response.games()).hasSize(2);
        assertThat(response.detailsByGameId()).containsOnly(Map.entry(98765L, detail));
        verify(eternalReturnApiClient).getUserGames("abc-123");
        verify(eternalReturnApiClient).getGame(98765);
        verify(eternalReturnApiClient, times(0)).getGame(98766);
    }

    @Test
    void getGameCachesDetailWithinTtl() {
        GameDetailResponse detail = gameDetailResponse(98765);
        when(eternalReturnApiClient.getGame(98765)).thenReturn(detail);

        GameDetailResponse firstResponse = eternalReturnService.getGame(98765);
        GameDetailResponse secondResponse = eternalReturnService.getGame(98765);

        assertThat(firstResponse).isSameAs(detail);
        assertThat(secondResponse).isSameAs(detail);
        verify(eternalReturnApiClient, times(1)).getGame(98765);
    }

    @Test
    void getUserGamesStopsAddingDetailsWhenRateLimited() {
        UserGamesResponse games = new UserGamesResponse(
                List.of(
                        userGame(98765, 1, "Jackie", 3, 5),
                        userGame(98766, 22, "Luke", 1, 7),
                        userGame(98767, 45, "Mai", 2, 4)
                ),
                98767L
        );
        GameDetailResponse detail = gameDetailResponse(98765);
        when(eternalReturnApiClient.getUserGames("abc-123")).thenReturn(games);
        when(eternalReturnApiClient.getGame(98765)).thenReturn(detail);
        when(eternalReturnApiClient.getGame(98766))
                .thenThrow(new EternalReturnApiException(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests"));

        UserGamesResponse response = eternalReturnService.getUserGames("abc-123", true, 3);

        assertThat(response.detailsByGameId()).containsOnly(Map.entry(98765L, detail));
        verify(eternalReturnApiClient).getGame(98765);
        verify(eternalReturnApiClient).getGame(98766);
        verify(eternalReturnApiClient, times(0)).getGame(98767);
    }

    @Test
    void getTopRankingsAddsRecentStatsForTopTenWithoutGameCount() {
        when(eternalReturnApiClient.getTopRankings(39, 3))
                .thenReturn(Map.of(
                        "code", 200,
                        "topRanks", List.of(Map.of(
                                "rank", 1,
                                "nickname", "topUser",
                                "rankScore", 8320
                        ))
                ));
        when(eternalReturnApiClient.getUserByNickname("topUser"))
                .thenReturn(new UserSearchResponse("abc-123", "topUser", Map.of()));
        when(eternalReturnApiClient.getUserGames("abc-123")).thenReturn(userGamesResponse(98765));

        TopRankingsResponse response = eternalReturnService.getTopRankings(39, 3);

        assertThat(response.raw()).containsEntry("code", 200);
        assertThat(response.topRanks())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("rank", 1)
                .containsEntry("nickname", "topUser")
                .containsEntry("averageRank", 3.0)
                .containsEntry("top3Count", 1)
                .containsEntry("top3Rate", 1.0)
                .containsEntry("averageKills", 5.0)
                .containsEntry("mostPlayedCharacterName", "Jackie")
                .doesNotContainKey("gameCount")
                .doesNotContainKey("recentStats");
        verify(eternalReturnApiClient).getUserByNickname("topUser");
        verify(eternalReturnApiClient).getUserGames("abc-123");
    }

    @Test
    void getTopRankingsEnrichesRecentStatsWithRankedGamesOnly() {
        when(eternalReturnApiClient.getTopRankings(39, 3))
                .thenReturn(Map.of(
                        "code", 200,
                        "topRanks", List.of(Map.of(
                                "rank", 1,
                                "nickname", "topUser",
                                "rankScore", 8320
                        ))
                ));
        when(eternalReturnApiClient.getUserByNickname("topUser"))
                .thenReturn(new UserSearchResponse("abc-123", "topUser", Map.of()));
        when(eternalReturnApiClient.getUserGames("abc-123"))
                .thenReturn(new UserGamesResponse(
                        List.of(
                                userGame(98765, 1, "Jackie", 2, 5),
                                userGameWithSeason(98766, 0, 22, "Luke", 1, 20)
                        ),
                        98766L
                ));

        TopRankingsResponse response = eternalReturnService.getTopRankings(39, 3);

        assertThat(response.topRanks())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("top3Count", 1)
                .containsEntry("averageKills", 5.0)
                .containsEntry("mostPlayedCharacterName", "Jackie");
    }

    @Test
    void getTopRankingsKeepsRankingWithZeroGamesWhenUserCannotBeResolved() {
        when(eternalReturnApiClient.getTopRankings(39, 3))
                .thenReturn(Map.of(
                        "code", 200,
                        "topRanks", List.of(Map.of(
                                "rank", 1,
                                "rankScore", 8320
                        ))
                ));

        TopRankingsResponse response = eternalReturnService.getTopRankings(39, 3);

        assertThat(response.topRanks())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("rank", 1)
                .containsEntry("top3Count", 0)
                .containsEntry("top3Rate", 0.0)
                .doesNotContainKey("gameCount")
                .doesNotContainKey("recentStats");
    }

    @Test
    void getTopRankingsDoesNotResolveNicknamesAfterTopTen() {
        List<Map<String, Object>> topRanks = java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(rank -> Map.<String, Object>of(
                        "rank", rank,
                        "nickname", "topUser" + rank,
                        "rankScore", 9000 - rank
                ))
                .toList();
        when(eternalReturnApiClient.getTopRankings(39, 3))
                .thenReturn(Map.of("code", 200, "topRanks", topRanks));
        for (int rank = 1; rank <= 10; rank++) {
            String nickname = "topUser" + rank;
            String userId = "abc-" + rank;
            when(eternalReturnApiClient.getUserByNickname(nickname))
                    .thenReturn(new UserSearchResponse(userId, nickname, Map.of()));
            when(eternalReturnApiClient.getUserGames(userId)).thenReturn(userGamesResponse(98765 + rank));
        }

        eternalReturnService.getTopRankings(39, 3);

        verify(eternalReturnApiClient, times(0)).getUserByNickname("topUser11");
        verify(eternalReturnApiClient, times(0)).getUserGames("abc-11");
    }

    @Test
    void getTopRankingsContinuesAfterSingleUserFailure() {
        when(eternalReturnApiClient.getTopRankings(39, 3))
                .thenReturn(Map.of(
                        "code", 200,
                        "topRanks", List.of(
                                Map.of("rank", 1, "nickname", "topUser1", "rankScore", 8320),
                                Map.of("rank", 2, "nickname", "topUser2", "rankScore", 8200),
                                Map.of("rank", 3, "nickname", "topUser3", "rankScore", 8100)
                        )
                ));
        when(eternalReturnApiClient.getUserByNickname("topUser1"))
                .thenReturn(new UserSearchResponse("abc-1", "topUser1", Map.of()));
        when(eternalReturnApiClient.getUserGames("abc-1")).thenReturn(userGamesResponse(98765));
        when(eternalReturnApiClient.getUserByNickname("topUser2"))
                .thenThrow(new EternalReturnApiException(HttpStatus.NOT_FOUND, "not found"));
        when(eternalReturnApiClient.getUserByNickname("topUser3"))
                .thenReturn(new UserSearchResponse("abc-3", "topUser3", Map.of()));
        when(eternalReturnApiClient.getUserGames("abc-3")).thenReturn(userGamesResponse(98767));

        TopRankingsResponse response = eternalReturnService.getTopRankings(39, 3);

        assertThat(response.topRanks())
                .element(2)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("averageRank", 3.0)
                .containsEntry("averageKills", 5.0);
        verify(eternalReturnApiClient).getUserByNickname("topUser3");
        verify(eternalReturnApiClient).getUserGames("abc-3");
    }

    @Test
    void getTopRankingsContinuesAfterRateLimitOnSingleRanking() {
        when(eternalReturnApiClient.getTopRankings(39, 3))
                .thenReturn(Map.of(
                        "code", 200,
                        "topRanks", List.of(
                                Map.of("rank", 1, "nickname", "topUser1", "rankScore", 8320),
                                Map.of("rank", 2, "nickname", "topUser2", "rankScore", 8200),
                                Map.of("rank", 3, "nickname", "topUser3", "rankScore", 8100)
                        )
                ));
        when(eternalReturnApiClient.getUserByNickname("topUser1"))
                .thenReturn(new UserSearchResponse("abc-1", "topUser1", Map.of()));
        when(eternalReturnApiClient.getUserGames("abc-1")).thenReturn(userGamesResponse(98765));
        when(eternalReturnApiClient.getUserByNickname("topUser2"))
                .thenReturn(new UserSearchResponse("abc-2", "topUser2", Map.of()));
        when(eternalReturnApiClient.getUserGames("abc-2"))
                .thenThrow(new EternalReturnApiException(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests"));
        when(eternalReturnApiClient.getUserByNickname("topUser3"))
                .thenReturn(new UserSearchResponse("abc-3", "topUser3", Map.of()));
        when(eternalReturnApiClient.getUserGames("abc-3")).thenReturn(userGamesResponse(98767));

        TopRankingsResponse response = eternalReturnService.getTopRankings(39, 3);

        assertThat(response.topRanks())
                .element(1)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("top3Count", 0)
                .containsEntry("averageKills", null);
        assertThat(response.topRanks())
                .element(2)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("averageRank", 3.0)
                .containsEntry("averageKills", 5.0)
                .containsEntry("mostPlayedCharacterName", "Jackie");
        verify(eternalReturnApiClient).getUserByNickname("topUser3");
        verify(eternalReturnApiClient).getUserGames("abc-3");
    }

    @Test
    void getCharacterMetaAggregatesTopRankingGamesByCharacter() {
        when(eternalReturnApiClient.getTopRankings(39, 3))
                .thenReturn(Map.of(
                        "code", 200,
                        "topRanks", List.of(Map.of(
                                "rank", 1,
                                "nickname", "topUser",
                                "rankScore", 8320,
                                "tier", "이터니티"
                        ))
                ));
        when(eternalReturnApiClient.getUserByNickname("topUser"))
                .thenReturn(new UserSearchResponse("abc-123", "topUser", Map.of()));
        when(eternalReturnApiClient.getUserGames("abc-123"))
                .thenReturn(new UserGamesResponse(
                        List.of(
                                userGame(98765, 1, "Jackie", 1, 6),
                                userGame(98766, 1, "Jackie", 4, 2),
                                userGame(98767, 22, "Luke", 3, 5)
                        ),
                        null
                ));

        Map<String, Object> response = eternalReturnService.getCharacterMeta(39, 3, "이터니티");

        assertThat(response)
                .containsEntry("seasonId", 39)
                .containsEntry("matchingTeamMode", 3)
                .containsEntry("tier", "이터니티")
                .containsEntry("sampleGameCount", 3);
        assertThat(response.get("characters"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .first()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("characterNum", 1)
                .containsEntry("characterName", "Jackie")
                .containsEntry("gameCount", 2)
                .containsEntry("pickRate", 0.67)
                .containsEntry("top3Count", 1)
                .containsEntry("top3Rate", 0.5)
                .containsEntry("averageRank", 2.5)
                .containsEntry("averageKills", 4.0);
    }

    private UserGamesResponse userGamesResponse(long gameId) {
        return new UserGamesResponse(
                List.of(userGame(gameId, 1, "Jackie", 3, 5)),
                gameId
        );
    }

    private GameDetailResponse gameDetailResponse(long gameId) {
        return new GameDetailResponse(
                gameId,
                39,
                3,
                3,
                "2026-06-09T13:44:20.020+0900",
                614,
                609,
                8,
                0,
                List.of()
        );
    }

    private UserGameSummary userGame(
            long gameId,
            int characterNum,
            String characterName,
            int gameRank,
            int playerKill
    ) {
        return userGameWithSeason(gameId, 39, characterNum, characterName, gameRank, playerKill);
    }

    private UserGameSummary userGameWithSeason(
            long gameId,
            int seasonId,
            int characterNum,
            String characterName,
            int gameRank,
            int playerKill
    ) {
        return new UserGameSummary(
                gameId,
                "testUser",
                seasonId,
                3,
                3,
                characterNum,
                characterName,
                gameRank,
                playerKill,
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
        );
    }

    private static class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
