package com.toyproject.eron.erapi;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.toyproject.eron.erapi.dto.DataTableResponse;
import com.toyproject.eron.erapi.dto.GameDetailResponse;
import com.toyproject.eron.erapi.dto.TopRankingsResponse;
import com.toyproject.eron.erapi.dto.UserGameSummary;
import com.toyproject.eron.erapi.dto.UserGamesResponse;
import com.toyproject.eron.erapi.dto.UserOverviewResponse;
import com.toyproject.eron.erapi.dto.UserRankResponse;
import com.toyproject.eron.erapi.dto.UserRecentStatsResponse;
import com.toyproject.eron.erapi.dto.UserSearchResponse;
import com.toyproject.eron.erapi.dto.UserStatsResponse;
import com.toyproject.eron.global.config.EternalReturnApiProperties;

@Service
public class EternalReturnService {

    private static final Logger log = LoggerFactory.getLogger(EternalReturnService.class);
    private static final int RANKING_STATS_ENRICH_LIMIT = 10;
    private static final int USER_GAMES_DETAIL_LIMIT_MAX = 5;

    private final EternalReturnApiClient eternalReturnApiClient;
    private final Duration userGamesCacheTtl;
    private final Clock clock;
    private final Cache<String, CacheEntry<UserGamesResponse>> userGamesCache;
    private final Cache<Long, CacheEntry<GameDetailResponse>> gameDetailCache;
    private final Cache<String, CacheEntry<Map<String, Object>>> topRankingsCache;
    private final Cache<String, CacheEntry<UserSearchResponse>> userSearchCache;

    @Autowired
    public EternalReturnService(EternalReturnApiClient eternalReturnApiClient, EternalReturnApiProperties properties) {
        this(
                eternalReturnApiClient,
                properties.getUserGamesCacheTtl(),
                Clock.systemUTC(),
                properties.getCacheMaximumSize()
        );
    }

    EternalReturnService(EternalReturnApiClient eternalReturnApiClient, Duration userGamesCacheTtl, Clock clock) {
        this(eternalReturnApiClient, userGamesCacheTtl, clock, 10_000);
    }

    EternalReturnService(
            EternalReturnApiClient eternalReturnApiClient,
            Duration userGamesCacheTtl,
            Clock clock,
            long cacheMaximumSize
    ) {
        this.eternalReturnApiClient = eternalReturnApiClient;
        this.userGamesCacheTtl = userGamesCacheTtl;
        this.clock = clock;
        long maximumSize = Math.max(1, cacheMaximumSize);
        this.userGamesCache = Caffeine.newBuilder().maximumSize(maximumSize).build();
        this.gameDetailCache = Caffeine.newBuilder().maximumSize(maximumSize).build();
        this.topRankingsCache = Caffeine.newBuilder().maximumSize(maximumSize).build();
        this.userSearchCache = Caffeine.newBuilder().maximumSize(maximumSize).build();
    }

    public UserSearchResponse getUserByNickname(String nickname) {
        return getCachedUserByNickname(nickname);
    }

    public UserOverviewResponse getUserOverview(String nickname, int seasonId, int matchingTeamMode) {
        UserSearchResponse user = getCachedUserByNickname(nickname);
        Map<String, Object> rank = eternalReturnApiClient.getUserRank(user.userId(), seasonId, matchingTeamMode);
        UserStatsResponse seasonStats = getUserStats(user.userId(), seasonId);
        UserGamesResponse games = getUserGames(user.userId());
        return new UserOverviewResponse(user, rank, seasonStats, games, UserRecentStatsResponse.from(rankedOnly(games)));
    }

    public UserStatsResponse getUserStats(String userId, int seasonId) {
        Map<String, Object> response = eternalReturnApiClient.getUserStats(userId, seasonId);
        return new UserStatsResponse(userId, seasonId, asListOfMaps(response.get("userStats")), response);
    }

    public UserGamesResponse getUserGames(String userId) {
        return getUserGames(userId, null);
    }

    public UserGamesResponse getUserGames(String userId, Long next) {
        if (userGamesCacheTtl.isZero() || userGamesCacheTtl.isNegative()) {
            return next == null
                    ? eternalReturnApiClient.getUserGames(userId)
                    : eternalReturnApiClient.getUserGames(userId, next);
        }

        String cacheKey = userGamesCacheKey(userId, next);
        Instant now = clock.instant();
        CacheEntry<UserGamesResponse> cached = userGamesCache.getIfPresent(cacheKey);
        if (isAlive(cached, now)) {
            return cached.value();
        }
        userGamesCache.invalidate(cacheKey);

        UserGamesResponse response = next == null
                ? eternalReturnApiClient.getUserGames(userId)
                : eternalReturnApiClient.getUserGames(userId, next);
        userGamesCache.put(cacheKey, new CacheEntry<>(response, now.plus(userGamesCacheTtl)));
        return response;
    }

    public UserGamesResponse getUserGames(String userId, boolean includeDetails, int detailLimit) {
        UserGamesResponse response = getUserGames(userId);
        if (!includeDetails) {
            return response;
        }

        return withGameDetails(response, detailLimit);
    }

    public UserGamesResponse withGameDetails(UserGamesResponse response, int detailLimit) {
        if (detailLimit <= 0 || response.games().isEmpty()) {
            return response;
        }

        int clampedDetailLimit = Math.min(detailLimit, USER_GAMES_DETAIL_LIMIT_MAX);
        Map<Long, GameDetailResponse> detailsByGameId = new LinkedHashMap<>();
        for (int index = 0; index < response.games().size() && index < clampedDetailLimit; index++) {
            if (!addGameDetail(detailsByGameId, response.games().get(index).gameId())) {
                break;
            }
        }

        return new UserGamesResponse(response.games(), response.next(), detailsByGameId);
    }

    public UserRankResponse getUserRank(String userId, int seasonId, int matchingTeamMode) {
        Map<String, Object> response = eternalReturnApiClient.getUserRank(userId, seasonId, matchingTeamMode);
        return new UserRankResponse(userId, seasonId, matchingTeamMode, asMap(response.get("userRank")), response);
    }

    public TopRankingsResponse getTopRankings(int seasonId, int matchingTeamMode) {
        Map<String, Object> response = getCachedTopRankings(seasonId, matchingTeamMode);
        if (!(response.get("topRanks") instanceof List<?> topRanks)) {
            return new TopRankingsResponse(seasonId, matchingTeamMode, List.of(), response);
        }

        Map<String, Object> enrichedResponse = new LinkedHashMap<>(response);
        enrichedResponse.put("topRanks", enrichTopRankings(topRanks));
        return new TopRankingsResponse(
                seasonId,
                matchingTeamMode,
                asListOfMaps(enrichedResponse.get("topRanks")),
                enrichedResponse
        );
    }

    public Map<String, Object> getCharacterMeta(int seasonId, int matchingTeamMode, String tier) {
        Map<String, Object> response = getCachedTopRankings(seasonId, matchingTeamMode);
        if (!(response.get("topRanks") instanceof List<?> topRanks)) {
            return Map.of(
                    "seasonId", seasonId,
                    "matchingTeamMode", matchingTeamMode,
                    "tier", tier,
                    "sampleGameCount", 0,
                    "characters", List.of()
            );
        }

        List<UserGameSummary> games = collectTopRankingGames(topRanks, tier);
        return Map.of(
                "seasonId", seasonId,
                "matchingTeamMode", matchingTeamMode,
                "tier", tier,
                "sampleGameCount", games.size(),
                "characters", toCharacterMeta(games)
        );
    }

    public GameDetailResponse getGame(long gameId) {
        return getCachedGame(gameId);
    }

    public DataTableResponse getDataTable(String metaType) {
        return new DataTableResponse(metaType, eternalReturnApiClient.getDataTable(metaType));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toRankingMap(Object ranking) {
        if (ranking instanceof Map<?, ?> rankingMap) {
            return new LinkedHashMap<>((Map<String, Object>) rankingMap);
        }

        return new LinkedHashMap<>();
    }

    private Map<String, Object> getCachedTopRankings(int seasonId, int matchingTeamMode) {
        if (userGamesCacheTtl.isZero() || userGamesCacheTtl.isNegative()) {
            return eternalReturnApiClient.getTopRankings(seasonId, matchingTeamMode);
        }

        String cacheKey = seasonId + ":" + matchingTeamMode;
        Instant now = clock.instant();
        CacheEntry<Map<String, Object>> cached = topRankingsCache.getIfPresent(cacheKey);
        if (isAlive(cached, now)) {
            return cached.value();
        }
        topRankingsCache.invalidate(cacheKey);

        Map<String, Object> response = eternalReturnApiClient.getTopRankings(seasonId, matchingTeamMode);
        topRankingsCache.put(cacheKey, new CacheEntry<>(response, now.plus(userGamesCacheTtl)));
        return response;
    }

    private boolean addGameDetail(Map<Long, GameDetailResponse> detailsByGameId, Long gameId) {
        if (gameId == null) {
            return true;
        }

        try {
            detailsByGameId.put(gameId, getCachedGame(gameId));
            return true;
        } catch (EternalReturnApiException exception) {
            return exception.getStatus() != HttpStatus.TOO_MANY_REQUESTS;
        }
    }

    private GameDetailResponse getCachedGame(long gameId) {
        if (userGamesCacheTtl.isZero() || userGamesCacheTtl.isNegative()) {
            return eternalReturnApiClient.getGame(gameId);
        }

        Instant now = clock.instant();
        CacheEntry<GameDetailResponse> cached = gameDetailCache.getIfPresent(gameId);
        if (isAlive(cached, now)) {
            return cached.value();
        }
        gameDetailCache.invalidate(gameId);

        GameDetailResponse response = eternalReturnApiClient.getGame(gameId);
        gameDetailCache.put(gameId, new CacheEntry<>(response, now.plus(userGamesCacheTtl)));
        return response;
    }

    private List<Map<String, Object>> enrichTopRankings(List<?> topRanks) {
        java.util.ArrayList<Map<String, Object>> rankings = new java.util.ArrayList<>(topRanks.size());

        for (int index = 0; index < topRanks.size(); index++) {
            Map<String, Object> ranking = toRankingMap(topRanks.get(index));
            if (index < RANKING_STATS_ENRICH_LIMIT) {
                try {
                    ranking = enrichRankingWithRecentStats(ranking);
                } catch (EternalReturnApiException exception) {
                    log.warn(
                            "Failed to enrich top ranking stats: rank={}, nickname={}, status={}",
                            ranking.get("rank"),
                            ranking.get("nickname"),
                            exception.getStatus()
                    );
                    ranking = enrichRankingWithEmptyStats(ranking);
                }
            } else {
                ranking = enrichRankingWithEmptyStats(ranking);
            }

            rankings.add(ranking);
        }

        return rankings;
    }

    private List<UserGameSummary> collectTopRankingGames(List<?> topRanks, String tier) {
        java.util.ArrayList<UserGameSummary> games = new java.util.ArrayList<>();

        for (int index = 0; index < topRanks.size() && index < RANKING_STATS_ENRICH_LIMIT; index++) {
            try {
                Map<String, Object> ranking = toRankingMap(topRanks.get(index));
                putTierFallback(ranking);
                if (!sameTier(tier, ranking.get("tier"))) {
                    continue;
                }

                String userId = rankingUserId(ranking);
                if (userId != null) {
                    games.addAll(getUserGames(userId).games());
                }
            } catch (EternalReturnApiException exception) {
                if (exception.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                    break;
                }
            }
        }

        return games;
    }

    private boolean sameTier(String requestedTier, Object rankingTier) {
        if (requestedTier == null || requestedTier.isBlank()) {
            return true;
        }
        if (rankingTier == null) {
            return false;
        }

        return normalizeTier(requestedTier).equals(normalizeTier(String.valueOf(rankingTier)));
    }

    private String normalizeTier(String tier) {
        return tier.replace(" ", "").replace("_", "").toLowerCase(java.util.Locale.ROOT);
    }

    private List<Map<String, Object>> toCharacterMeta(List<UserGameSummary> games) {
        if (games.isEmpty()) {
            return List.of();
        }

        Map<Integer, CharacterMetaAccumulator> accumulators = new java.util.HashMap<>();
        for (UserGameSummary game : games) {
            if (game.characterNum() == null) {
                continue;
            }

            accumulators.computeIfAbsent(
                    game.characterNum(),
                    characterNum -> new CharacterMetaAccumulator(characterNum, game.characterName())
            ).add(game);
        }

        int totalGames = accumulators.values()
                .stream()
                .mapToInt(CharacterMetaAccumulator::gameCount)
                .sum();

        return accumulators.values()
                .stream()
                .sorted(Comparator
                        .comparing(CharacterMetaAccumulator::gameCount).reversed()
                        .thenComparing(CharacterMetaAccumulator::averageRank, Comparator.nullsLast(Double::compareTo))
                        .thenComparing(CharacterMetaAccumulator::characterNum))
                .map(accumulator -> accumulator.toMap(totalGames))
                .toList();
    }

    private Map<String, Object> enrichRankingWithRecentStats(Map<String, Object> ranking) {
        String userId = rankingUserId(ranking);
        if (userId == null) {
            return enrichRankingWithEmptyStats(ranking);
        }

        ranking.putIfAbsent("userId", userId);
        return putRecentStats(ranking, UserRecentStatsResponse.from(rankedOnly(getUserGames(userId))));
    }

    private UserGamesResponse rankedOnly(UserGamesResponse games) {
        List<UserGameSummary> rankedGames = games.games()
                .stream()
                .filter(this::isRankedGame)
                .toList();
        return new UserGamesResponse(rankedGames, games.next());
    }

    private boolean isRankedGame(UserGameSummary game) {
        return game.seasonId() != null && game.seasonId() > 0;
    }

    private String userGamesCacheKey(String userId, Long next) {
        return userId + ":" + (next == null ? "first" : next);
    }

    private Map<String, Object> enrichRankingWithEmptyStats(Map<String, Object> ranking) {
        return putRecentStats(ranking, emptyRecentStats());
    }

    private Map<String, Object> putRecentStats(Map<String, Object> ranking, UserRecentStatsResponse recentStats) {
        putTierFallback(ranking);
        ranking.put("winCount", recentStats.winCount());
        ranking.put("winRate", recentStats.winRate());
        ranking.put("top3Count", recentStats.top3Count());
        ranking.put("top3Rate", recentStats.top3Rate());
        ranking.put("averageRank", recentStats.averageRank());
        ranking.put("averageKills", recentStats.averageKills());
        ranking.put("averageAssists", recentStats.averageAssists());
        ranking.put("averageDeaths", recentStats.averageDeaths());
        ranking.put("averageKda", recentStats.averageKda());
        ranking.put("averageDamageToPlayer", recentStats.averageDamageToPlayer());
        ranking.put("totalMmrGain", recentStats.totalMmrGain());
        ranking.put("mostPlayedCharacterNum", recentStats.mostPlayedCharacterNum());
        ranking.put("mostPlayedCharacterName", recentStats.mostPlayedCharacterName());
        return ranking;
    }

    private String rankingUserId(Map<String, Object> ranking) {
        Object userId = firstPresent(ranking, "userId", "userNum", "uid");
        if (userId != null) {
            return String.valueOf(userId);
        }

        Object nickname = ranking.get("nickname");
        if (nickname == null) {
            return null;
        }

        return getCachedUserByNickname(String.valueOf(nickname)).userId();
    }

    private Object firstPresent(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    private void putTierFallback(Map<String, Object> ranking) {
        if (ranking.get("tier") != null) {
            return;
        }

        log.debug("Ranking tier is missing; leaving tier unset. rank={}", ranking.get("rank"));
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        return null;
    }

    private UserSearchResponse getCachedUserByNickname(String nickname) {
        if (userGamesCacheTtl.isZero() || userGamesCacheTtl.isNegative()) {
            return eternalReturnApiClient.getUserByNickname(nickname);
        }

        Instant now = clock.instant();
        CacheEntry<UserSearchResponse> cached = userSearchCache.getIfPresent(nickname);
        if (isAlive(cached, now)) {
            return cached.value();
        }
        userSearchCache.invalidate(nickname);

        UserSearchResponse response = eternalReturnApiClient.getUserByNickname(nickname);
        userSearchCache.put(nickname, new CacheEntry<>(response, now.plus(userGamesCacheTtl)));
        return response;
    }

    private boolean isAlive(CacheEntry<?> cacheEntry, Instant now) {
        return cacheEntry != null && cacheEntry.expiresAt().isAfter(now);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }

        return Map.of();
    }

    private List<Map<String, Object>> asListOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        return list.stream()
                .map(this::asMap)
                .filter(map -> !map.isEmpty())
                .toList();
    }

    private UserRecentStatsResponse emptyRecentStats() {
        return UserRecentStatsResponse.from(new UserGamesResponse(List.of(), null));
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
    }

    private static class CharacterMetaAccumulator {

        private final Integer characterNum;
        private final String characterName;
        private int gameCount;
        private int top3Count;
        private int winCount;
        private int killSum;
        private int rankSum;
        private int rankCount;

        private CharacterMetaAccumulator(Integer characterNum, String characterName) {
            this.characterNum = characterNum;
            this.characterName = characterName;
        }

        private void add(UserGameSummary game) {
            gameCount++;
            if (game.gameRank() != null) {
                rankSum += game.gameRank();
                rankCount++;
                if (game.gameRank() <= 3) {
                    top3Count++;
                }
                if (game.gameRank() <= 1) {
                    winCount++;
                }
            }
            if (game.playerKill() != null) {
                killSum += game.playerKill();
            }
        }

        private Integer characterNum() {
            return characterNum;
        }

        private int gameCount() {
            return gameCount;
        }

        private Double averageRank() {
            if (rankCount == 0) {
                return null;
            }

            return round((double) rankSum / rankCount);
        }

        private Map<String, Object> toMap(int totalGames) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("characterNum", characterNum);
            meta.put("characterName", characterName);
            meta.put("gameCount", gameCount);
            meta.put("pickRate", rate(gameCount, totalGames));
            meta.put("winCount", winCount);
            meta.put("winRate", rate(winCount, gameCount));
            meta.put("top3Count", top3Count);
            meta.put("top3Rate", rate(top3Count, gameCount));
            meta.put("averageRank", averageRank());
            meta.put("averageKills", rate(killSum, gameCount));
            return meta;
        }

        private static double rate(int numerator, int denominator) {
            if (denominator == 0) {
                return 0.0;
            }

            return round((double) numerator / denominator);
        }

        private static double round(double value) {
            return Math.round(value * 100.0) / 100.0;
        }
    }
}
