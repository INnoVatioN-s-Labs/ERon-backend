package com.toyproject.eron.erapi.dto;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserGamesResponse(
        List<UserGameSummary> games,
        Integer next,
        Map<Integer, GameDetailResponse> detailsByGameId
) {

    public UserGamesResponse(List<UserGameSummary> games, Integer next) {
        this(games, next, Map.of());
    }

    @JsonProperty("statsByMode")
    public Map<String, ModeStatsResponse> statsByMode() {
        return games.stream()
                .collect(Collectors.groupingBy(
                        UserGameSummary::modeKey,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()
                ))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> ModeStatsResponse.from(entry.getValue()),
                        (first, second) -> first,
                        java.util.LinkedHashMap::new
                ));
    }

    public record ModeStatsResponse(
            String modeKey,
            String modeName,
            Integer matchingMode,
            Integer matchingTeamMode,
            UserRecentStatsResponse stats
    ) {

        private static ModeStatsResponse from(List<UserGameSummary> games) {
            UserGameSummary firstGame = games.get(0);
            return new ModeStatsResponse(
                    firstGame.modeKey(),
                    firstGame.modeName(),
                    firstGame.matchingMode(),
                    firstGame.matchingTeamMode(),
                    UserRecentStatsResponse.from(games)
            );
        }
    }
}
