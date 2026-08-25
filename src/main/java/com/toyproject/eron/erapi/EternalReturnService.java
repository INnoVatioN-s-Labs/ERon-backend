package com.toyproject.eron.erapi;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
import com.toyproject.eron.erapi.dto.SkinMetadataResponse;
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
    private static final int RANKED_MATCHING_MODE = 3;
    private static final int OVERVIEW_RANKED_STATS_PAGE_LIMIT = 5;
    private static final int OVERVIEW_RANKED_STATS_GAME_LIMIT = 20;
    private static final int USER_GAMES_DETAIL_LIMIT_MAX = 10;
    private static final int DEFAULT_CURRENT_SEASON_ID = 41;
    private static final int DEFAULT_PREVIOUS_SEASON_ID = 39;
    private static final int DEFAULT_CURRENT_MATCHING_TEAM_MODE = 3;
    private static final String DEFAULT_CURRENT_META_TIER = "";
    private static final int DEFAULT_CURRENT_META_RANKING_SAMPLE_LIMIT = 1000;
    private static final String TODAY_CHARACTER_CACHE_KEY = "today";
    private static final ZoneId TODAY_CHARACTER_REFRESH_ZONE = ZoneId.of("Asia/Seoul");
    private static final int TODAY_CHARACTER_REFRESH_HOUR = 9;

    private final EternalReturnApiClient eternalReturnApiClient;
    private final Duration userGamesCacheTtl;
    private final Clock clock;
    private final int currentSeasonId;
    private final int previousSeasonId;
    private final int currentMatchingTeamMode;
    private final String currentMetaTier;
    private final int currentMetaRankingSampleLimit;
    private final Cache<String, CacheEntry<UserGamesResponse>> userGamesCache;
    private final Cache<Long, CacheEntry<GameDetailResponse>> gameDetailCache;
    private final Cache<String, CacheEntry<Map<String, Object>>> userRankCache;
    private final Cache<String, CacheEntry<Map<String, Object>>> userStatsCache;
    private final Cache<String, CacheEntry<Map<String, Object>>> topRankingsCache;
    private final Cache<String, CacheEntry<UserSearchResponse>> userSearchCache;
    private final Cache<String, CacheEntry<Map<String, Object>>> todayCharacterCache;
    private final RankTierResolver rankTierResolver = new RankTierResolver();

    @Autowired
    public EternalReturnService(EternalReturnApiClient eternalReturnApiClient, EternalReturnApiProperties properties) {
        this(
                eternalReturnApiClient,
                properties.getUserGamesCacheTtl(),
                Clock.systemUTC(),
                properties.getCacheMaximumSize(),
                properties.getCurrentSeasonId(),
                properties.getPreviousSeasonId(),
                properties.getCurrentMatchingTeamMode(),
                properties.getCurrentMetaTier(),
                properties.getCurrentMetaRankingSampleLimit()
        );
    }

    EternalReturnService(EternalReturnApiClient eternalReturnApiClient, Duration userGamesCacheTtl, Clock clock) {
        this(
                eternalReturnApiClient,
                userGamesCacheTtl,
                clock,
                10_000,
                DEFAULT_CURRENT_SEASON_ID,
                DEFAULT_PREVIOUS_SEASON_ID,
                DEFAULT_CURRENT_MATCHING_TEAM_MODE,
                DEFAULT_CURRENT_META_TIER,
                DEFAULT_CURRENT_META_RANKING_SAMPLE_LIMIT
        );
    }

    EternalReturnService(
            EternalReturnApiClient eternalReturnApiClient,
            Duration userGamesCacheTtl,
            Clock clock,
            long cacheMaximumSize,
            int currentSeasonId,
            int previousSeasonId,
            int currentMatchingTeamMode,
            String currentMetaTier,
            int currentMetaRankingSampleLimit
    ) {
        this.eternalReturnApiClient = eternalReturnApiClient;
        this.userGamesCacheTtl = userGamesCacheTtl;
        this.clock = clock;
        this.currentSeasonId = currentSeasonId;
        this.previousSeasonId = previousSeasonId;
        this.currentMatchingTeamMode = currentMatchingTeamMode;
        this.currentMetaTier = currentMetaTier;
        this.currentMetaRankingSampleLimit = currentMetaRankingSampleLimit;
        long maximumSize = Math.max(1, cacheMaximumSize);
        this.userGamesCache = Caffeine.newBuilder().maximumSize(maximumSize).build();
        this.gameDetailCache = Caffeine.newBuilder().maximumSize(maximumSize).build();
        this.userRankCache = Caffeine.newBuilder().maximumSize(maximumSize).build();
        this.userStatsCache = Caffeine.newBuilder().maximumSize(maximumSize).build();
        this.topRankingsCache = Caffeine.newBuilder().maximumSize(maximumSize).build();
        this.userSearchCache = Caffeine.newBuilder().maximumSize(maximumSize).build();
        this.todayCharacterCache = Caffeine.newBuilder().maximumSize(maximumSize).build();
    }

    public UserSearchResponse getUserByNickname(String nickname) {
        return getCachedUserByNickname(nickname);
    }

    public UserOverviewResponse getUserOverview(String nickname, int seasonId, int matchingTeamMode) {
        UserSearchResponse user = getCachedUserByNickname(nickname);
        int rankSeasonId = currentSeasonId;
        UserStatsResponse seasonStats = getUserStatsOrEmpty(user.userId(), rankSeasonId, RANKED_MATCHING_MODE);
        Map<String, Object> rank = rankFromStatsOrEmpty(seasonStats, matchingTeamMode);
        Map<String, Object> previousRank = previousRank(user.userId(), matchingTeamMode);
        UserGamesResponse games = getUserGames(user.userId());
        UserGamesResponse currentSeasonRankedGames = currentSeasonRankedGames(user.userId(), games, rankSeasonId);
        return new UserOverviewResponse(
                user,
                rankSeasonId,
                previousSeasonId,
                rank,
                rank,
                previousRank,
                seasonStats,
                seasonSummary(seasonStats, matchingTeamMode),
                games,
                UserRecentStatsResponse.from(currentSeasonRankedGames)
        );
    }

    public UserStatsResponse getUserStats(String userId, int seasonId) {
        return getUserStats(userId, seasonId, RANKED_MATCHING_MODE);
    }

    public UserStatsResponse getUserStats(String userId, int seasonId, int matchingMode) {
        Map<String, Object> response = getCachedUserStats(userId, seasonId, matchingMode);
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

        return new UserGamesResponse(mergeRouteIds(response.games(), detailsByGameId), response.next(), detailsByGameId);
    }

    public UserRankResponse getUserRank(String userId, int seasonId, int matchingTeamMode) {
        Map<String, Object> enrichedResponse = enrichRankResponse(getCachedUserRank(
                userId,
                seasonId,
                matchingTeamMode
        ), seasonId, matchingTeamMode);
        Map<String, Object> userRank = asMap(enrichedResponse.get("userRank"));
        return new UserRankResponse(userId, seasonId, matchingTeamMode, userRank, enrichedResponse);
    }

    private Map<String, Object> rankOrEmpty(String userId, int seasonId, int matchingTeamMode, String label) {
        try {
            Map<String, Object> rank = enrichRankResponse(
                    getCachedUserRank(userId, seasonId, matchingTeamMode),
                    seasonId,
                    matchingTeamMode
            );
            if (!hasRank(asMap(rank.get("userRank")))) {
                return emptyRankResponse(seasonId, matchingTeamMode);
            }
            return rank;
        } catch (EternalReturnApiException exception) {
            log.debug(
                    "{} season rank is unavailable. userId={}, seasonId={}, matchingTeamMode={}, status={}",
                    label,
                    userId,
                    seasonId,
                    matchingTeamMode,
                    exception.getStatus()
            );
            return emptyRankResponse(seasonId, matchingTeamMode);
        }
    }

    private UserStatsResponse getUserStatsOrEmpty(String userId, int seasonId, int matchingMode) {
        try {
            return getUserStats(userId, seasonId, matchingMode);
        } catch (EternalReturnApiException exception) {
            log.debug(
                    "Season stats are unavailable. userId={}, seasonId={}, matchingMode={}, status={}",
                    userId,
                    seasonId,
                    matchingMode,
                    exception.getStatus()
            );
            return new UserStatsResponse(
                    userId,
                    seasonId,
                    List.of(),
                    Map.of(
                            "seasonId", seasonId,
                            "matchingMode", matchingMode,
                            "userStats", List.of()
                    )
            );
        }
    }

    private Map<String, Object> rankFromStatsOrEmpty(UserStatsResponse seasonStats, int matchingTeamMode) {
        return seasonStats.userStats()
                .stream()
                .filter(this::isRankedStat)
                .filter(stats -> isMatchingTeamStat(stats, matchingTeamMode))
                .findFirst()
                .map(stats -> rankFromStats(stats, seasonStats.seasonId(), matchingTeamMode))
                .orElseGet(() -> emptyRankResponse(seasonStats.seasonId(), matchingTeamMode));
    }

    private Map<String, Object> seasonSummary(UserStatsResponse seasonStats, int matchingTeamMode) {
        return seasonStats.userStats()
                .stream()
                .filter(this::isRankedStat)
                .filter(stats -> isMatchingTeamStat(stats, matchingTeamMode))
                .findFirst()
                .map(this::seasonSummary)
                .orElseGet(Map::of);
    }

    private Map<String, Object> seasonSummary(Map<String, Object> stats) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("gameCount", firstNumber(stats, "totalGames", "gameCount"));
        summary.put("averageTeamKill", firstNumber(stats, "averageTeamKill", "averageTk", "avgTeamKill"));
        summary.put("winRate", firstNumber(stats, "winRate"));
        summary.put("averageKills", firstNumber(stats, "averageKills", "averageKill", "avgKills"));
        summary.put("top2Rate", firstNumber(stats, "top2Rate", "top2", "top2Ratio"));
        summary.put("top3Rate", firstNumber(stats, "top3Rate", "top3", "top3Ratio"));
        summary.put("averageDamageToPlayer", firstNumber(stats, "averageDamageToPlayer", "averageDamage", "avgDamage"));
        summary.put("averageAssists", firstNumber(stats, "averageAssists", "averageAssist", "avgAssists"));
        summary.put("averageRank", firstNumber(stats, "averageRank", "avgRank"));
        summary.put("averageMonsterKill", firstNumber(stats, "averageMonsterKill", "averageAnimalKill", "avgMonsterKill"));
        summary.put("averageCredit", firstNumber(stats, "averageCredit", "avgCredit", "averageGainCredit"));
        summary.put("averageVision", firstNumber(stats, "averageVision", "avgVision", "averageSight"));
        return summary;
    }

    private boolean isRankedStat(Map<String, Object> stats) {
        Integer matchingMode = toInteger(stats.get("matchingMode"));
        return matchingMode == null || Integer.valueOf(RANKED_MATCHING_MODE).equals(matchingMode);
    }

    private boolean isMatchingTeamStat(Map<String, Object> stats, int matchingTeamMode) {
        Integer statMatchingTeamMode = toInteger(stats.get("matchingTeamMode"));
        return statMatchingTeamMode == null || Integer.valueOf(matchingTeamMode).equals(statMatchingTeamMode);
    }

    private Map<String, Object> rankFromStats(Map<String, Object> stats, int seasonId, int matchingTeamMode) {
        Map<String, Object> userRank = new LinkedHashMap<>();
        userRank.put("nickname", stats.get("nickname"));
        userRank.put("rank", stats.get("rank"));
        userRank.put("rankSize", stats.get("rankSize"));
        userRank.put("rankPercent", stats.get("rankPercent"));
        userRank.put("mmr", stats.get("mmr"));
        userRank.put("seasonId", seasonId);
        userRank.put("matchingMode", RANKED_MATCHING_MODE);
        userRank.put("matchingTeamMode", matchingTeamMode);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("seasonId", seasonId);
        response.put("matchingMode", RANKED_MATCHING_MODE);
        response.put("matchingTeamMode", matchingTeamMode);
        response.put("userRank", enrichRankTier(userRank, seasonId, matchingTeamMode));
        return response;
    }

    private Map<String, Object> previousRank(String userId, int matchingTeamMode) {
        if (previousSeasonId < 1) {
            return emptyRankResponse(previousSeasonId, matchingTeamMode);
        }

        return rankOrEmpty(userId, previousSeasonId, matchingTeamMode, "Previous");
    }

    private boolean hasRank(Map<String, Object> userRank) {
        return isPositiveInteger(userRank.get("rank"))
                || isPositiveInteger(userRank.get("serverRank"))
                || isPositiveInteger(userRank.get("serverCode"))
                || isPositiveInteger(userRank.get("rewardServerCode"))
                || isPositiveInteger(userRank.get("rankPoint"))
                || isPositiveInteger(userRank.get("rankScore"))
                || isPositiveInteger(userRank.get("mmr"));
    }

    private boolean isPositiveInteger(Object value) {
        Integer integer = toInteger(value);
        return integer != null && integer > 0;
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

    public Map<String, Object> getCurrentCharacterMeta() {
        Instant now = clock.instant();
        CacheEntry<Map<String, Object>> cached = todayCharacterCache.getIfPresent(TODAY_CHARACTER_CACHE_KEY);
        if (isAlive(cached, now)) {
            return cached.value();
        }
        todayCharacterCache.invalidate(TODAY_CHARACTER_CACHE_KEY);

        Map<String, Object> response = getCharacterMeta(
                currentSeasonId,
                currentMatchingTeamMode,
                currentMetaTier,
                currentMetaRankingSampleLimit
        );
        todayCharacterCache.put(
                TODAY_CHARACTER_CACHE_KEY,
                new CacheEntry<>(response, nextTodayCharacterRefreshAt(now))
        );
        return response;
    }

    public Map<String, Object> getCharacterMeta(int seasonId, int matchingTeamMode, String tier) {
        return getCharacterMeta(seasonId, matchingTeamMode, tier, RANKING_STATS_ENRICH_LIMIT);
    }

    private Map<String, Object> getCharacterMeta(
            int seasonId,
            int matchingTeamMode,
            String tier,
            int rankingSampleLimit
    ) {
        Map<String, Object> response = getCachedTopRankings(seasonId, matchingTeamMode);
        if (!(response.get("topRanks") instanceof List<?> topRanks)) {
            return Map.of(
                    "seasonId", seasonId,
                    "matchingTeamMode", matchingTeamMode,
                    "tier", tier,
                    "rankingSampleLimit", rankingSampleLimit,
                    "sampleGameCount", 0,
                    "characters", List.of()
            );
        }

        List<UserGameSummary> games = collectTopRankingGames(topRanks, tier, rankingSampleLimit);
        return Map.of(
                "seasonId", seasonId,
                "matchingTeamMode", matchingTeamMode,
                "tier", tier,
                "rankingSampleLimit", rankingSampleLimit,
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

    public SkinMetadataResponse getSkinMetadata() {
        return new SkinMetadataResponse(eternalReturnApiClient.getSkinMetadata());
    }

    public Map<String, Object> getTacticalSkillMetadata(List<Integer> tacticalSkillGroupCodes) {
        return Map.of(
                "tacticalSkills",
                eternalReturnApiClient.getTacticalSkillMetadata(tacticalSkillGroupCodes)
        );
    }

    public Map<String, Object> searchKoreanL10n(String query, int limit) {
        return Map.of(
                "query", query,
                "matches", eternalReturnApiClient.searchKoreanL10n(query, limit)
        );
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

    private Map<String, Object> getCachedUserRank(String userId, int seasonId, int matchingTeamMode) {
        if (userGamesCacheTtl.isZero() || userGamesCacheTtl.isNegative()) {
            return eternalReturnApiClient.getUserRank(userId, seasonId, matchingTeamMode);
        }

        String cacheKey = userId + ":" + seasonId + ":" + matchingTeamMode;
        Instant now = clock.instant();
        CacheEntry<Map<String, Object>> cached = userRankCache.getIfPresent(cacheKey);
        if (isAlive(cached, now)) {
            return cached.value();
        }
        userRankCache.invalidate(cacheKey);

        Map<String, Object> response = eternalReturnApiClient.getUserRank(userId, seasonId, matchingTeamMode);
        userRankCache.put(cacheKey, new CacheEntry<>(response, now.plus(userGamesCacheTtl)));
        return response;
    }

    private Map<String, Object> getCachedUserStats(String userId, int seasonId, int matchingMode) {
        if (userGamesCacheTtl.isZero() || userGamesCacheTtl.isNegative()) {
            return eternalReturnApiClient.getUserStats(userId, seasonId, matchingMode);
        }

        String cacheKey = userId + ":" + seasonId + ":" + matchingMode;
        Instant now = clock.instant();
        CacheEntry<Map<String, Object>> cached = userStatsCache.getIfPresent(cacheKey);
        if (isAlive(cached, now)) {
            return cached.value();
        }
        userStatsCache.invalidate(cacheKey);

        Map<String, Object> response = eternalReturnApiClient.getUserStats(userId, seasonId, matchingMode);
        userStatsCache.put(cacheKey, new CacheEntry<>(response, now.plus(userGamesCacheTtl)));
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

    private List<UserGameSummary> mergeRouteIds(
            List<UserGameSummary> games,
            Map<Long, GameDetailResponse> detailsByGameId
    ) {
        return games.stream()
                .map(game -> {
                    if (game.routeId() != null || game.gameId() == null) {
                        return game;
                    }

                    GameDetailResponse detail = detailsByGameId.get(game.gameId());
                    Integer routeId = routeIdFromDetail(game, detail);
                    if (routeId == null) {
                        return game;
                    }

                    return game.withRouteId(routeId);
                })
                .toList();
    }

    private Integer routeIdFromDetail(UserGameSummary game, GameDetailResponse detail) {
        if (detail == null) {
            return null;
        }

        return detail.participants().stream()
                .filter(participant -> java.util.Objects.equals(participant.nickname(), game.nickname()))
                .map(participant -> participant.routeId())
                .filter(routeId -> routeId != null)
                .findFirst()
                .orElseGet(() -> detail.participants().stream()
                        .filter(participant -> java.util.Objects.equals(participant.characterNum(), game.characterNum()))
                        .map(participant -> participant.routeId())
                        .filter(routeId -> routeId != null)
                        .findFirst()
                        .orElse(null));
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

    private List<UserGameSummary> collectTopRankingGames(List<?> topRanks, String tier, int rankingSampleLimit) {
        java.util.ArrayList<UserGameSummary> games = new java.util.ArrayList<>();
        int clampedLimit = Math.max(1, rankingSampleLimit);

        for (int index = 0; index < topRanks.size() && index < clampedLimit; index++) {
            try {
                Map<String, Object> ranking = toRankingMap(topRanks.get(index));
                putTierFallback(ranking);
                if (!sameTier(tier, ranking.get("tier"))) {
                    continue;
                }

                String userId = rankingUserId(ranking);
                if (userId != null) {
                    games.addAll(rankedOnly(getUserGames(userId)).games());
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
                        .comparing((CharacterMetaAccumulator accumulator) -> accumulator.metaScore(totalGames)).reversed()
                        .thenComparing(CharacterMetaAccumulator::gameCount, Comparator.reverseOrder())
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

    private UserGamesResponse currentSeasonRankedGames(String userId, UserGamesResponse firstPage, int seasonId) {
        List<UserGameSummary> rankedGames = new java.util.ArrayList<>();
        addCurrentSeasonRankedGames(rankedGames, firstPage, seasonId);

        Long next = firstPage.next();
        int pageCount = 1;
        while (next != null
                && pageCount < OVERVIEW_RANKED_STATS_PAGE_LIMIT
                && rankedGames.size() < OVERVIEW_RANKED_STATS_GAME_LIMIT) {
            UserGamesResponse page;
            try {
                page = getUserGames(userId, next);
            } catch (EternalReturnApiException exception) {
                log.debug(
                        "Additional current season ranked games are unavailable. userId={}, next={}, status={}",
                        userId,
                        next,
                        exception.getStatus()
                );
                break;
            }
            addCurrentSeasonRankedGames(rankedGames, page, seasonId);
            next = page.next();
            pageCount++;
        }

        if (rankedGames.size() > OVERVIEW_RANKED_STATS_GAME_LIMIT) {
            rankedGames = rankedGames.subList(0, OVERVIEW_RANKED_STATS_GAME_LIMIT);
        }

        return new UserGamesResponse(rankedGames, next);
    }

    private void addCurrentSeasonRankedGames(
            List<UserGameSummary> rankedGames,
            UserGamesResponse page,
            int seasonId
    ) {
        page.games()
                .stream()
                .filter(game -> isCurrentSeasonRankedGame(game, seasonId))
                .forEach(rankedGames::add);
    }

    private boolean isRankedGame(UserGameSummary game) {
        return game.seasonId() != null && game.seasonId() > 0;
    }

    private boolean isCurrentSeasonRankedGame(UserGameSummary game, int seasonId) {
        return Integer.valueOf(seasonId).equals(game.seasonId());
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

    private Map<String, Object> enrichRankResponse(Map<String, Object> response, int seasonId, int matchingTeamMode) {
        Map<String, Object> enrichedResponse = new LinkedHashMap<>(response);
        enrichedResponse.put("seasonId", seasonId);
        enrichedResponse.put("matchingTeamMode", matchingTeamMode);
        enrichedResponse.put("userRank", enrichRankTier(asMap(response.get("userRank")), seasonId, matchingTeamMode));
        return enrichedResponse;
    }

    private Map<String, Object> emptyRankResponse(int seasonId, int matchingTeamMode) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("seasonId", seasonId);
        response.put("matchingTeamMode", matchingTeamMode);
        response.put("userRank", Map.of());
        return response;
    }

    private Map<String, Object> enrichRankTier(Map<String, Object> userRank) {
        return enrichRankTier(userRank, null, null);
    }

    private Map<String, Object> enrichRankTier(Map<String, Object> userRank, Integer seasonId, Integer matchingTeamMode) {
        if (userRank.isEmpty()) {
            return userRank;
        }

        Map<String, Object> enrichedRank = new LinkedHashMap<>(userRank);
        putIfAbsent(enrichedRank, "seasonId", seasonId);
        putIfAbsent(enrichedRank, "matchingTeamMode", matchingTeamMode);
        Integer rankPoint = firstInteger(enrichedRank.get("rankPoint"), enrichedRank.get("rankScore"), enrichedRank.get("mmr"));
        putIfAbsent(enrichedRank, "rankPoint", rankPoint);

        String tier = firstText(enrichedRank.get("tier"), enrichedRank.get("tierName"));
        if (tier == null) {
            tier = rankTierResolver.resolve(
                    rankPoint,
                    toInteger(enrichedRank.get("rank"))
            );
        }
        if (tier != null) {
            putIfBlank(enrichedRank, "tier", tier);
            putIfBlank(enrichedRank, "tierName", tier);
        }
        enrichedRank.putIfAbsent("tierGrade", tierGrade(tier));

        return enrichedRank;
    }

    private Integer tierGrade(String tier) {
        if (tier == null || tier.isBlank()) {
            return null;
        }

        char lastCharacter = tier.charAt(tier.length() - 1);
        if (!Character.isDigit(lastCharacter)) {
            return null;
        }

        return Character.digit(lastCharacter, 10);
    }

    private boolean hasText(Object value) {
        return value instanceof String text && !text.isBlank();
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (hasText(value)) {
                return String.valueOf(value);
            }
        }

        return null;
    }

    private Integer firstInteger(Object... values) {
        for (Object value : values) {
            Integer integer = toInteger(value);
            if (integer != null) {
                return integer;
            }
        }

        return null;
    }

    private Number firstNumber(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Number number = toNumber(map.get(key));
            if (number != null) {
                return number;
            }
        }

        return null;
    }

    private Number toNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return text.contains(".") ? Double.parseDouble(text) : Integer.parseInt(text);
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        return null;
    }

    private void putIfBlank(Map<String, Object> map, String key, String value) {
        if (!hasText(map.get(key))) {
            map.put(key, value);
        }
    }

    private void putIfAbsent(Map<String, Object> map, String key, Object value) {
        if (value != null && !map.containsKey(key)) {
            map.put(key, value);
        }
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

    private Instant nextTodayCharacterRefreshAt(Instant now) {
        ZonedDateTime currentTime = now.atZone(TODAY_CHARACTER_REFRESH_ZONE);
        ZonedDateTime refreshTime = currentTime
                .withHour(TODAY_CHARACTER_REFRESH_HOUR)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        if (!currentTime.isBefore(refreshTime)) {
            refreshTime = refreshTime.plusDays(1);
        }

        return refreshTime.toInstant();
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

        private double pickRate(int totalGames) {
            return rate(gameCount, totalGames);
        }

        private double winRate() {
            return rate(winCount, gameCount);
        }

        private double top3Rate() {
            return rate(top3Count, gameCount);
        }

        private double sampleConfidence() {
            return round(Math.min(1.0, gameCount / 5.0));
        }

        private double metaScore(int totalGames) {
            Double averageRank = averageRank();
            double rankScore = averageRank == null || averageRank <= 0 ? 0.0 : 1.0 / averageRank;
            double rawScore = (winRate() * 0.40)
                    + (top3Rate() * 0.35)
                    + (rankScore * 0.15)
                    + (pickRate(totalGames) * 0.10);
            double confidenceWeight = 0.60 + (sampleConfidence() * 0.40);

            return round(rawScore * confidenceWeight);
        }

        private Map<String, Object> toMap(int totalGames) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("characterNum", characterNum);
            meta.put("characterName", characterName);
            meta.put("gameCount", gameCount);
            meta.put("pickRate", pickRate(totalGames));
            meta.put("winCount", winCount);
            meta.put("winRate", winRate());
            meta.put("top3Count", top3Count);
            meta.put("top3Rate", top3Rate());
            meta.put("averageRank", averageRank());
            meta.put("averageKills", rate(killSum, gameCount));
            meta.put("sampleConfidence", sampleConfidence());
            meta.put("metaScore", metaScore(totalGames));
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
