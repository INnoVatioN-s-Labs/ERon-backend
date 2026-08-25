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
import com.toyproject.eron.erapi.dto.UserRankResponse;
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
                .thenReturn(Map.of("userRank", Map.of("rank", 321, "mmr", 8461)));
        when(eternalReturnApiClient.getUserStats("abc-123", 41, 3))
                .thenReturn(Map.of("userStats", List.of(Map.ofEntries(
                        Map.entry("seasonId", 41),
                        Map.entry("matchingMode", 3),
                        Map.entry("matchingTeamMode", 3),
                        Map.entry("totalGames", 12),
                        Map.entry("winRate", 0.25),
                        Map.entry("averageTeamKill", 9.5),
                        Map.entry("averageKills", 4.0),
                        Map.entry("top2Rate", 0.33),
                        Map.entry("top3Rate", 0.5),
                        Map.entry("averageDamageToPlayer", 18052.5),
                        Map.entry("averageAssists", 5.0),
                        Map.entry("averageRank", 4.5),
                        Map.entry("averageMonsterKill", 31.5),
                        Map.entry("averageCredit", 240),
                        Map.entry("averageVision", 18.5),
                        Map.entry("rank", 123),
                        Map.entry("mmr", 4567)
                ))));
        when(eternalReturnApiClient.getUserGames("abc-123"))
                .thenReturn(new UserGamesResponse(
                        List.of(
                                userGameWithSeason(98765, 41, 1, "Jackie", 2, 5),
                                userGameWithSeason(98766, 0, 22, "Luke", 1, 20)
                        ),
                        null
                ));

        UserOverviewResponse response = eternalReturnService.getUserOverview("testUser", 39, 3);

        assertThat(response.games().games()).hasSize(2);
        assertThat(response.currentSeasonId()).isEqualTo(41);
        assertThat(response.previousSeasonId()).isEqualTo(39);
        assertThat(response.currentRank()).isSameAs(response.rank());
        assertThat(response.rank())
                .containsEntry("seasonId", 41)
                .containsEntry("matchingTeamMode", 3);
        assertThat(response.rank().get("userRank"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("seasonId", 41)
                .containsEntry("matchingTeamMode", 3)
                .containsEntry("rankPoint", 4567)
                .containsEntry("tier", "플래티넘 2")
                .containsEntry("tierName", "플래티넘 2")
                .containsEntry("tierGrade", 2);
        assertThat(response.previousRank())
                .containsEntry("seasonId", 39)
                .containsEntry("matchingTeamMode", 3);
        assertThat(response.previousRank().get("userRank"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("seasonId", 39)
                .containsEntry("matchingTeamMode", 3)
                .containsEntry("rank", 321)
                .containsEntry("rankPoint", 8461)
                .containsEntry("tier", "데미갓")
                .containsEntry("tierName", "데미갓")
                .containsEntry("tierGrade", null);
        assertThat(response.seasonSummary())
                .containsEntry("gameCount", 12)
                .containsEntry("averageTeamKill", 9.5)
                .containsEntry("winRate", 0.25)
                .containsEntry("averageKills", 4.0)
                .containsEntry("top2Rate", 0.33)
                .containsEntry("top3Rate", 0.5)
                .containsEntry("averageDamageToPlayer", 18052.5)
                .containsEntry("averageAssists", 5.0)
                .containsEntry("averageRank", 4.5)
                .containsEntry("averageMonsterKill", 31.5)
                .containsEntry("averageCredit", 240)
                .containsEntry("averageVision", 18.5);
        assertThat(response.recentStats().gameCount()).isEqualTo(1);
        assertThat(response.recentStats().top3Count()).isEqualTo(1);
        assertThat(response.recentStats().averageKills()).isEqualTo(5.0);
        verify(eternalReturnApiClient).getUserRank("abc-123", 39, 3);
        verify(eternalReturnApiClient).getUserStats("abc-123", 41, 3);
    }

    @Test
    void getUserRankAddsTierWhenRankApiOmitsTier() {
        when(eternalReturnApiClient.getUserRank("abc-123", 39, 3))
                .thenReturn(Map.of(
                        "code", 200,
                        "userRank", Map.of(
                                "rank", 123,
                                "rankScore", 4567,
                                "mmr", 4321
                        )
                ));

        UserRankResponse response = eternalReturnService.getUserRank("abc-123", 39, 3);

        assertThat(response.userRank())
                .containsEntry("rank", 123)
                .containsEntry("rankScore", 4567)
                .containsEntry("mmr", 4321)
                .containsEntry("rankPoint", 4567)
                .containsEntry("tier", "플래티넘 2")
                .containsEntry("tierName", "플래티넘 2")
                .containsEntry("tierGrade", 2);
        assertThat(response.raw().get("userRank"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("tier", "플래티넘 2")
                .containsEntry("tierName", "플래티넘 2")
                .containsEntry("tierGrade", 2);
    }

    @Test
    void getUserRankAddsTierFromMmrWhenRankScoreIsMissing() {
        when(eternalReturnApiClient.getUserRank("abc-123", 39, 3))
                .thenReturn(Map.of(
                        "code", 200,
                        "userRank", Map.of(
                                "serverCode", 10,
                                "mmr", 8461,
                                "rewardServerCode", 10,
                                "serverRank", 1963,
                                "nickname", "서른이상원딜금지",
                                "rank", 1997
                        )
                ));

        UserRankResponse response = eternalReturnService.getUserRank("abc-123", 39, 3);

        assertThat(response.userRank())
                .containsEntry("mmr", 8461)
                .containsEntry("tier", "미스릴")
                .containsEntry("tierName", "미스릴");
    }

    @Test
    void getUserRankKeepsProvidedTier() {
        when(eternalReturnApiClient.getUserRank("abc-123", 39, 3))
                .thenReturn(Map.of(
                        "code", 200,
                        "userRank", Map.of(
                                "rank", 123,
                                "rankScore", 4567,
                                "tier", "원본 티어"
                        )
                ));

        UserRankResponse response = eternalReturnService.getUserRank("abc-123", 39, 3);

        assertThat(response.userRank())
                .containsEntry("tier", "원본 티어")
                .containsEntry("tierName", "원본 티어");
    }

    @Test
    void getUserRankFillsBlankTierName() {
        when(eternalReturnApiClient.getUserRank("abc-123", 39, 3))
                .thenReturn(Map.of(
                        "code", 200,
                        "userRank", Map.of(
                                "rank", 123,
                                "rankScore", 4567,
                                "tierName", ""
                        )
                ));

        UserRankResponse response = eternalReturnService.getUserRank("abc-123", 39, 3);

        assertThat(response.userRank())
                .containsEntry("tier", "플래티넘 2")
                .containsEntry("tierName", "플래티넘 2");
    }

    @Test
    void getUserOverviewKeepsResponseWhenPreviousRankIsUnavailable() {
        when(eternalReturnApiClient.getUserByNickname("testUser"))
                .thenReturn(new UserSearchResponse("abc-123", "testUser", Map.of()));
        when(eternalReturnApiClient.getUserRank("abc-123", 39, 3))
                .thenThrow(new EternalReturnApiException(HttpStatus.NOT_FOUND, "Previous rank not found."));
        when(eternalReturnApiClient.getUserStats("abc-123", 41, 3))
                .thenReturn(Map.of("userStats", List.of(Map.of(
                        "seasonId", 41,
                        "matchingMode", 3,
                        "matchingTeamMode", 3,
                        "rank", 123,
                        "mmr", 8461
                ))));
        when(eternalReturnApiClient.getUserGames("abc-123"))
                .thenReturn(new UserGamesResponse(List.of(), null));

        UserOverviewResponse response = eternalReturnService.getUserOverview("testUser", 40, 3);

        assertThat(response.rank().get("userRank"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("tierName", "이터니티");
        assertThat(response.previousRank())
                .containsEntry("seasonId", 39)
                .containsEntry("matchingTeamMode", 3);
        assertThat(response.previousRank().get("userRank"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .isEmpty();
    }

    @Test
    void getUserOverviewKeepsCurrentRankEmptyWhenCurrentRankIsUnavailable() {
        when(eternalReturnApiClient.getUserByNickname("testUser"))
                .thenReturn(new UserSearchResponse("abc-123", "testUser", Map.of()));
        when(eternalReturnApiClient.getUserRank("abc-123", 39, 3))
                .thenReturn(Map.of("userRank", Map.of("rank", 321, "mmr", 8461)));
        when(eternalReturnApiClient.getUserStats("abc-123", 41, 3))
                .thenReturn(Map.of("userStats", List.of()));
        when(eternalReturnApiClient.getUserGames("abc-123"))
                .thenReturn(new UserGamesResponse(List.of(), null));

        UserOverviewResponse response = eternalReturnService.getUserOverview("testUser", 40, 3);

        assertThat(response.currentRank())
                .containsEntry("seasonId", 41)
                .containsEntry("matchingTeamMode", 3);
        assertThat(response.currentRank().get("userRank"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .isEmpty();
        assertThat(response.previousRank().get("userRank"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("tierName", "데미갓");
    }

    @Test
    void getUserOverviewUsesCurrentSeasonStatsForCurrentRank() {
        when(eternalReturnApiClient.getUserByNickname("testUser"))
                .thenReturn(new UserSearchResponse("abc-123", "testUser", Map.of()));
        when(eternalReturnApiClient.getUserRank("abc-123", 39, 3))
                .thenReturn(Map.of("userRank", Map.of(
                        "serverCode", 10,
                        "mmr", 1825,
                        "serverRank", 0,
                        "rank", 153496
                )));
        when(eternalReturnApiClient.getUserStats("abc-123", 41, 3))
                .thenReturn(Map.of("userStats", List.of(Map.of(
                        "seasonId", 41,
                        "matchingMode", 3,
                        "matchingTeamMode", 3,
                        "mmr", 639,
                        "rank", 123456
                ))));
        when(eternalReturnApiClient.getUserGames("abc-123"))
                .thenReturn(new UserGamesResponse(List.of(), null));

        UserOverviewResponse response = eternalReturnService.getUserOverview("testUser", 40, 3);

        assertThat(response.currentRank())
                .containsEntry("seasonId", 41)
                .containsEntry("matchingTeamMode", 3);
        assertThat(response.currentRank().get("userRank"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("seasonId", 41)
                .containsEntry("rank", 123456)
                .containsEntry("rankPoint", 639)
                .containsEntry("tierName", "브론즈 4");
        assertThat(response.previousRank().get("userRank"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("seasonId", 39)
                .containsEntry("rank", 153496)
                .containsEntry("rankPoint", 1825)
                .containsEntry("tierName", "실버 3");
    }

    @Test
    void getUserOverviewCollectsCurrentSeasonRankedGamesAcrossPagesForRecentStats() {
        when(eternalReturnApiClient.getUserByNickname("testUser"))
                .thenReturn(new UserSearchResponse("abc-123", "testUser", Map.of()));
        when(eternalReturnApiClient.getUserRank("abc-123", 39, 3))
                .thenReturn(Map.of("userRank", Map.of("rank", 321, "mmr", 8461)));
        when(eternalReturnApiClient.getUserStats("abc-123", 41, 3))
                .thenReturn(Map.of("userStats", List.of(Map.of(
                        "seasonId", 41,
                        "matchingMode", 3,
                        "matchingTeamMode", 3,
                        "mmr", 4567,
                        "rank", 123
                ))));
        when(eternalReturnApiClient.getUserGames("abc-123"))
                .thenReturn(new UserGamesResponse(
                        List.of(userGameWithSeason(98765, 0, 1, "Jackie", 1, 20)),
                        98765L
                ));
        when(eternalReturnApiClient.getUserGames("abc-123", 98765L))
                .thenReturn(new UserGamesResponse(
                        List.of(userGameWithSeason(98764, 41, 22, "Luke", 2, 7)),
                        null
                ));

        UserOverviewResponse response = eternalReturnService.getUserOverview("testUser", 40, 3);

        assertThat(response.games().games()).hasSize(1);
        assertThat(response.recentStats().gameCount()).isEqualTo(1);
        assertThat(response.recentStats().top3Count()).isEqualTo(1);
        assertThat(response.recentStats().averageKills()).isEqualTo(7.0);
        assertThat(response.recentStats().mostPlayedCharacterName()).isEqualTo("Luke");
    }

    @Test
    void getUserOverviewKeepsCurrentRankWhenCurrentSeasonStatsOnlyHasMmr() {
        when(eternalReturnApiClient.getUserByNickname("testUser"))
                .thenReturn(new UserSearchResponse("abc-123", "testUser", Map.of()));
        when(eternalReturnApiClient.getUserRank("abc-123", 39, 3))
                .thenReturn(Map.of("userRank", Map.of(
                        "serverCode", 10,
                        "mmr", 1825,
                        "serverRank", 0,
                        "rank", 153496
                )));
        when(eternalReturnApiClient.getUserStats("abc-123", 41, 3))
                .thenReturn(Map.of("userStats", List.of(Map.of(
                        "matchingMode", 3,
                        "matchingTeamMode", 3,
                        "mmr", 639,
                        "totalGames", 1
                ))));
        when(eternalReturnApiClient.getUserGames("abc-123"))
                .thenReturn(new UserGamesResponse(List.of(), null));

        UserOverviewResponse response = eternalReturnService.getUserOverview("testUser", 40, 3);

        assertThat(response.currentRank().get("userRank"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("seasonId", 41)
                .containsEntry("rankPoint", 639)
                .containsEntry("tierName", "브론즈 4");
        assertThat(response.previousRank().get("userRank"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("tierName", "실버 3");
    }

    @Test
    void getUserOverviewIgnoresCurrentRankApiWhenStatsExists() {
        when(eternalReturnApiClient.getUserByNickname("testUser"))
                .thenReturn(new UserSearchResponse("abc-123", "testUser", Map.of()));
        when(eternalReturnApiClient.getUserRank("abc-123", 39, 3))
                .thenReturn(Map.of("userRank", Map.of(
                        "serverCode", 10,
                        "mmr", 8461,
                        "rank", 321
                )));
        when(eternalReturnApiClient.getUserStats("abc-123", 41, 3))
                .thenReturn(Map.of("userStats", List.of(Map.of(
                        "matchingMode", 3,
                        "matchingTeamMode", 3,
                        "mmr", 639,
                        "rank", 123456
                ))));
        when(eternalReturnApiClient.getUserGames("abc-123"))
                .thenReturn(new UserGamesResponse(List.of(), null));

        UserOverviewResponse response = eternalReturnService.getUserOverview("testUser", 40, 3);

        assertThat(response.currentRank().get("userRank"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("seasonId", 41)
                .containsEntry("rank", 123456)
                .containsEntry("rankPoint", 639)
                .containsEntry("tierName", "브론즈 4");
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
        GameDetailResponse detail = gameDetailResponseWithRoute(98765, "testUser", 1, 123456);
        when(eternalReturnApiClient.getUserGames("abc-123")).thenReturn(games);
        when(eternalReturnApiClient.getGame(98765)).thenReturn(detail);

        UserGamesResponse response = eternalReturnService.getUserGames("abc-123", true, 1);

        assertThat(response.games()).hasSize(2);
        assertThat(response.games().get(0).routeId()).isEqualTo(123456);
        assertThat(response.games().get(1).routeId()).isNull();
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
                .containsEntry("averageKills", 4.0)
                .containsEntry("sampleConfidence", 0.4)
                .containsEntry("metaScore", 0.38);
    }

    @Test
    void getCurrentCharacterMetaUsesConfiguredSeasonModeAndTier() {
        EternalReturnService configuredService = new EternalReturnService(
                eternalReturnApiClient,
                Duration.ofSeconds(30),
                clock,
                10_000,
                40,
                39,
                2,
                "데미갓",
                1000
        );
        when(eternalReturnApiClient.getTopRankings(40, 2))
                .thenReturn(Map.of("code", 200, "topRanks", List.of()));

        Map<String, Object> response = configuredService.getCurrentCharacterMeta();

        assertThat(response)
                .containsEntry("seasonId", 40)
                .containsEntry("matchingTeamMode", 2)
                .containsEntry("tier", "데미갓")
                .containsEntry("rankingSampleLimit", 1000)
                .containsEntry("sampleGameCount", 0);
        verify(eternalReturnApiClient).getTopRankings(40, 2);
    }

    @Test
    void getCurrentCharacterMetaDoesNotFilterWhenRankingTierIsMissing() {
        when(eternalReturnApiClient.getTopRankings(41, 3))
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
                        List.of(userGame(98765, 1, "Jackie", 1, 6)),
                        null
                ));

        Map<String, Object> response = eternalReturnService.getCurrentCharacterMeta();

        assertThat(response)
                .containsEntry("seasonId", 41)
                .containsEntry("matchingTeamMode", 3)
                .containsEntry("tier", "")
                .containsEntry("sampleGameCount", 1);
        assertThat(response.get("characters"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .first()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("characterName", "Jackie")
                .containsEntry("metaScore", 0.68);
    }

    @Test
    void getCurrentCharacterMetaCachesUntilNineAmInKorea() {
        MutableClock morningClock = new MutableClock(Instant.parse("2026-06-16T23:59:00Z"));
        EternalReturnService service = new EternalReturnService(
                eternalReturnApiClient,
                Duration.ofSeconds(30),
                morningClock
        );
        when(eternalReturnApiClient.getTopRankings(41, 3))
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
                        List.of(userGame(98765, 1, "Jackie", 1, 6)),
                        null
                ));

        Map<String, Object> firstResponse = service.getCurrentCharacterMeta();
        morningClock.advance(Duration.ofSeconds(30));
        Map<String, Object> cachedResponse = service.getCurrentCharacterMeta();
        morningClock.advance(Duration.ofSeconds(31));
        Map<String, Object> refreshedResponse = service.getCurrentCharacterMeta();

        assertThat(cachedResponse).isSameAs(firstResponse);
        assertThat(refreshedResponse).isNotSameAs(firstResponse);
        verify(eternalReturnApiClient, times(2)).getTopRankings(41, 3);
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

    private GameDetailResponse gameDetailResponseWithRoute(
            long gameId,
            String nickname,
            int characterNum,
            int routeId
    ) {
        return new GameDetailResponse(
                gameId,
                39,
                3,
                3,
                "2026-06-09T13:44:20.020+0900",
                614,
                609,
                8,
                1,
                List.of(new com.toyproject.eron.erapi.dto.GameParticipantSummary(
                        nickname,
                        1,
                        3,
                        characterNum,
                        "Jackie",
                        20,
                        5,
                        2,
                        1,
                        12,
                        7,
                        12345,
                        10000,
                        5000,
                        1000,
                        300,
                        1,
                        18,
                        1620,
                        0,
                        609,
                        Map.of(),
                        null,
                        null,
                        null,
                        List.of(),
                        routeId
                ))
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
