package com.toyproject.eron.erapi.dto;

import java.util.Map;

public record UserOverviewResponse(
        UserSearchResponse user,
        Integer currentSeasonId,
        Integer previousSeasonId,
        Map<String, Object> rank,
        Map<String, Object> currentRank,
        Map<String, Object> previousRank,
        UserStatsResponse seasonStats,
        Map<String, Object> seasonSummary,
        UserGamesResponse games,
        UserRecentStatsResponse recentStats
) {
    public UserOverviewResponse(
            UserSearchResponse user,
            Map<String, Object> rank,
            Map<String, Object> previousRank,
            UserStatsResponse seasonStats,
            Map<String, Object> seasonSummary,
            UserGamesResponse games,
            UserRecentStatsResponse recentStats
    ) {
        this(user, null, null, rank, rank, previousRank, seasonStats, seasonSummary, games, recentStats);
    }

    public UserOverviewResponse(
            UserSearchResponse user,
            Map<String, Object> rank,
            UserStatsResponse seasonStats,
            UserGamesResponse games,
            UserRecentStatsResponse recentStats
    ) {
        this(user, null, null, rank, rank, Map.of(), seasonStats, Map.of(), games, recentStats);
    }
}
