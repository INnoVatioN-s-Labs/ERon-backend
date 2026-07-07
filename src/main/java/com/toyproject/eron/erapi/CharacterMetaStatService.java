package com.toyproject.eron.erapi;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.toyproject.eron.erapi.dto.UserGameSummary;
import com.toyproject.eron.erapi.dto.UserGamesResponse;
import com.toyproject.eron.erapi.dto.UserSearchResponse;
import com.toyproject.eron.global.config.EternalReturnApiProperties;

@Service
public class CharacterMetaStatService {

    private static final Logger log = LoggerFactory.getLogger(CharacterMetaStatService.class);
    private static final int TODAY_CHARACTER_LIMIT = 5;
    private static final String COLLECTION_STATE_KEY_PREFIX = "character-meta";
    private static final Duration RATE_LIMIT_COOLDOWN = Duration.ofSeconds(60);

    private final EternalReturnApiClient eternalReturnApiClient;
    private final CharacterMetaStatRepository characterMetaStatRepository;
    private final int currentSeasonId;
    private final int currentMatchingTeamMode;
    private final int currentMetaRankingSampleLimit;
    private final int currentMetaRankingBatchSize;
    private final int currentMetaMinimumCharacterGames;
    private final int currentMetaSampleRetentionDays;
    private volatile Instant rateLimitedUntil = Instant.EPOCH;

    public CharacterMetaStatService(
            EternalReturnApiClient eternalReturnApiClient,
            CharacterMetaStatRepository characterMetaStatRepository,
            EternalReturnApiProperties properties
    ) {
        this.eternalReturnApiClient = eternalReturnApiClient;
        this.characterMetaStatRepository = characterMetaStatRepository;
        this.currentSeasonId = properties.getCurrentSeasonId();
        this.currentMatchingTeamMode = properties.getCurrentMatchingTeamMode();
        this.currentMetaRankingSampleLimit = properties.getCurrentMetaRankingSampleLimit();
        this.currentMetaRankingBatchSize = properties.getCurrentMetaRankingBatchSize();
        this.currentMetaMinimumCharacterGames = properties.getCurrentMetaMinimumCharacterGames();
        this.currentMetaSampleRetentionDays = properties.getCurrentMetaSampleRetentionDays();
    }

    public Map<String, Object> getTodayCharacterMeta() {
        int sampleGameCount = characterMetaStatRepository.totalGames(
                currentSeasonId,
                currentMatchingTeamMode,
                currentMetaSampleRetentionDays
        );
        List<Map<String, Object>> characters = characterMetaStatRepository.findCharacterMeta(
                currentSeasonId,
                currentMatchingTeamMode,
                currentMetaMinimumCharacterGames,
                TODAY_CHARACTER_LIMIT,
                currentMetaSampleRetentionDays
        );

        return Map.of(
                "seasonId", currentSeasonId,
                "matchingTeamMode", currentMatchingTeamMode,
                "rankingSampleLimit", currentMetaRankingSampleLimit,
                "minimumCharacterGames", currentMetaMinimumCharacterGames,
                "sampleRetentionDays", currentMetaSampleRetentionDays,
                "sampleGameCount", sampleGameCount,
                "source", "stored",
                "characters", characters
        );
    }

    public Map<String, Object> refreshTodayCharacterMetaSamples() {
        Instant now = Instant.now();
        if (now.isBefore(rateLimitedUntil)) {
            return rateLimitedResult(0, 0, 0, 0, 0);
        }

        int purgedSampleCount = characterMetaStatRepository.purgeSamplesOlderThan(currentMetaSampleRetentionDays);

        Map<String, Object> response;
        try {
            response = eternalReturnApiClient.getTopRankings(currentSeasonId, currentMatchingTeamMode);
        } catch (EternalReturnApiException exception) {
            if (exception.getStatus().value() == 429) {
                markRateLimited();
                return rateLimitedResult(0, 0, 0, 0, 0);
            }
            throw exception;
        }
        if (!(response.get("topRanks") instanceof List<?> topRanks)) {
            return refreshResult(0, 0, 0, 0, 0, 0, purgedSampleCount);
        }

        int visitedRankerCount = 0;
        int savedSampleCount = 0;
        int availableRankerCount = topRanks.size();
        int rankerPoolSize = Math.min(availableRankerCount, Math.max(1, currentMetaRankingSampleLimit));
        int batchSize = Math.min(rankerPoolSize, Math.max(1, currentMetaRankingBatchSize));
        String stateKey = collectionStateKey();
        int startRankIndex = normalizeRankIndex(characterMetaStatRepository.nextRankIndex(stateKey), rankerPoolSize);
        int nextRankIndex = startRankIndex;
        for (int visitedSlotCount = 0; visitedSlotCount < batchSize; visitedSlotCount++) {
            int index = (startRankIndex + visitedSlotCount) % rankerPoolSize;
            Map<String, Object> ranking = asMap(topRanks.get(index));
            if (ranking.isEmpty()) {
                nextRankIndex = (index + 1) % rankerPoolSize;
                continue;
            }

            try {
                String userId = rankingUserId(ranking);
                if (userId == null) {
                    nextRankIndex = (index + 1) % rankerPoolSize;
                    continue;
                }

                UserGamesResponse games = eternalReturnApiClient.getUserGames(userId);
                List<UserGameSummary> rankedGames = games.games()
                        .stream()
                        .filter(this::isCurrentRankedGame)
                        .toList();
                savedSampleCount += characterMetaStatRepository.saveSamples(userId, rankedGames);
                visitedRankerCount++;
                nextRankIndex = (index + 1) % rankerPoolSize;
            } catch (EternalReturnApiException exception) {
                log.warn(
                        "Failed to collect character meta sample: rank={}, nickname={}, status={}",
                        ranking.get("rank"),
                        ranking.get("nickname"),
                        exception.getStatus()
                );
                if (exception.getStatus().value() == 429) {
                    markRateLimited();
                    break;
                }
                nextRankIndex = (index + 1) % rankerPoolSize;
            }
        }

        characterMetaStatRepository.saveNextRankIndex(stateKey, nextRankIndex);
        return refreshResult(visitedRankerCount, savedSampleCount, startRankIndex, nextRankIndex, availableRankerCount, batchSize, purgedSampleCount);
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void refreshTodayCharacterMetaSamplesOnSchedule() {
        Map<String, Object> result = refreshTodayCharacterMetaSamples();
        log.info("Refreshed today character meta samples: {}", result);
    }

    private Map<String, Object> refreshResult(
            int visitedRankerCount,
            int savedSampleCount,
            int startRankIndex,
            int nextRankIndex,
            int availableRankerCount,
            int batchSize,
            int purgedSampleCount
    ) {
        return Map.ofEntries(
                Map.entry("seasonId", currentSeasonId),
                Map.entry("matchingTeamMode", currentMatchingTeamMode),
                Map.entry("rankingSampleLimit", currentMetaRankingSampleLimit),
                Map.entry("rankingBatchSize", batchSize),
                Map.entry("sampleRetentionDays", currentMetaSampleRetentionDays),
                Map.entry("availableRankerCount", availableRankerCount),
                Map.entry("startRankIndex", startRankIndex),
                Map.entry("nextRankIndex", nextRankIndex),
                Map.entry("visitedRankerCount", visitedRankerCount),
                Map.entry("savedSampleCount", savedSampleCount),
                Map.entry("purgedSampleCount", purgedSampleCount),
                Map.entry("sampleGameCount", characterMetaStatRepository.totalGames(
                        currentSeasonId,
                        currentMatchingTeamMode,
                        currentMetaSampleRetentionDays
                ))
        );
    }

    private Map<String, Object> rateLimitedResult(
            int visitedRankerCount,
            int savedSampleCount,
            int startRankIndex,
            int nextRankIndex,
            int availableRankerCount
    ) {
        return Map.ofEntries(
                Map.entry("seasonId", currentSeasonId),
                Map.entry("matchingTeamMode", currentMatchingTeamMode),
                Map.entry("rankingSampleLimit", currentMetaRankingSampleLimit),
                Map.entry("availableRankerCount", availableRankerCount),
                Map.entry("startRankIndex", startRankIndex),
                Map.entry("nextRankIndex", nextRankIndex),
                Map.entry("visitedRankerCount", visitedRankerCount),
                Map.entry("savedSampleCount", savedSampleCount),
                Map.entry("sampleGameCount", characterMetaStatRepository.totalGames(
                        currentSeasonId,
                        currentMatchingTeamMode,
                        currentMetaSampleRetentionDays
                )),
                Map.entry("rateLimited", true),
                Map.entry("retryAfterSeconds", retryAfterSeconds())
        );
    }

    private void markRateLimited() {
        rateLimitedUntil = Instant.now().plus(RATE_LIMIT_COOLDOWN);
    }

    private long retryAfterSeconds() {
        long seconds = Duration.between(Instant.now(), rateLimitedUntil).toSeconds();
        return Math.max(0, seconds);
    }

    private String collectionStateKey() {
        return COLLECTION_STATE_KEY_PREFIX + ":" + currentSeasonId + ":" + currentMatchingTeamMode;
    }

    private int normalizeRankIndex(int rankIndex, int limit) {
        if (limit <= 0) {
            return 0;
        }

        return Math.floorMod(rankIndex, limit);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }

        return Map.of();
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

        UserSearchResponse user = eternalReturnApiClient.getUserByNickname(String.valueOf(nickname));
        return user.userId();
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

    private boolean isCurrentRankedGame(UserGameSummary game) {
        return Integer.valueOf(currentSeasonId).equals(game.seasonId())
                && Integer.valueOf(currentMatchingTeamMode).equals(game.matchingTeamMode());
    }
}
