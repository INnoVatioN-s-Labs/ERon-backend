package com.toyproject.eron.erapi.dto;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public record UserRecentStatsResponse(
        int gameCount,
        int winCount,
        double winRate,
        int top3Count,
        double top3Rate,
        Double averageRank,
        Double averageKills,
        Double averageAssists,
        Double averageDeaths,
        Double averageKda,
        Double averageDamageToPlayer,
        Integer totalMmrGain,
        Integer mostPlayedCharacterNum,
        String mostPlayedCharacterName
) {

    public static UserRecentStatsResponse from(UserGamesResponse gamesResponse) {
        List<UserGameSummary> games = gamesResponse.games();
        int gameCount = games.size();

        if (gameCount == 0) {
            return new UserRecentStatsResponse(
                    0,
                    0,
                    0.0,
                    0,
                    0.0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        int winCount = countByRankAtMost(games, 1);
        int top3Count = countByRankAtMost(games, 3);
        int totalKills = sum(games, UserGameSummary::playerKill);
        int totalAssists = sum(games, UserGameSummary::playerAssistant);
        int totalDeaths = sum(games, UserGameSummary::playerDeaths);
        Integer mostPlayedCharacterNum = mostPlayedCharacterNum(games);

        return new UserRecentStatsResponse(
                gameCount,
                winCount,
                rate(winCount, gameCount),
                top3Count,
                rate(top3Count, gameCount),
                average(games, UserGameSummary::gameRank),
                average(games, UserGameSummary::playerKill),
                average(games, UserGameSummary::playerAssistant),
                average(games, UserGameSummary::playerDeaths),
                round((double) (totalKills + totalAssists) / Math.max(totalDeaths, 1)),
                average(games, UserGameSummary::damageToPlayer),
                nullableSum(games, UserGameSummary::mmrGain),
                mostPlayedCharacterNum,
                characterNameFor(games, mostPlayedCharacterNum)
        );
    }

    private static int countByRankAtMost(List<UserGameSummary> games, int rank) {
        return (int) games.stream()
                .map(UserGameSummary::gameRank)
                .filter(Objects::nonNull)
                .filter(gameRank -> gameRank <= rank)
                .count();
    }

    private static int sum(List<UserGameSummary> games, Function<UserGameSummary, Integer> valueExtractor) {
        return games.stream()
                .map(valueExtractor)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private static Integer nullableSum(List<UserGameSummary> games, Function<UserGameSummary, Integer> valueExtractor) {
        List<Integer> values = games.stream()
                .map(valueExtractor)
                .filter(Objects::nonNull)
                .toList();

        if (values.isEmpty()) {
            return null;
        }

        return values.stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    private static Double average(List<UserGameSummary> games, Function<UserGameSummary, Integer> valueExtractor) {
        return games.stream()
                .map(valueExtractor)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .stream()
                .map(UserRecentStatsResponse::round)
                .boxed()
                .findFirst()
                .orElse(null);
    }

    private static Integer mostPlayedCharacterNum(List<UserGameSummary> games) {
        return games.stream()
                .map(UserGameSummary::characterNum)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .max(Comparator
                        .comparing(Map.Entry<Integer, Long>::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static String characterNameFor(List<UserGameSummary> games, Integer characterNum) {
        if (characterNum == null) {
            return null;
        }

        return games.stream()
                .filter(game -> Objects.equals(game.characterNum(), characterNum))
                .map(UserGameSummary::characterName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static double rate(int numerator, int denominator) {
        return round((double) numerator / denominator);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
