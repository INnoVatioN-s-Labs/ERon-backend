package com.toyproject.eron.erapi;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.toyproject.eron.erapi.dto.GameDetailResponse;
import com.toyproject.eron.erapi.dto.UserGamesResponse;
import com.toyproject.eron.erapi.dto.UserOverviewResponse;
import com.toyproject.eron.erapi.dto.UserSearchResponse;
import com.toyproject.eron.global.config.EternalReturnApiProperties;

@Service
public class EternalReturnService {

    private final EternalReturnApiClient eternalReturnApiClient;
    private final Duration userGamesCacheTtl;
    private final Clock clock;
    private final Map<String, CacheEntry<UserGamesResponse>> userGamesCache = new ConcurrentHashMap<>();

    @Autowired
    public EternalReturnService(EternalReturnApiClient eternalReturnApiClient, EternalReturnApiProperties properties) {
        this(eternalReturnApiClient, properties.getUserGamesCacheTtl(), Clock.systemUTC());
    }

    EternalReturnService(EternalReturnApiClient eternalReturnApiClient, Duration userGamesCacheTtl, Clock clock) {
        this.eternalReturnApiClient = eternalReturnApiClient;
        this.userGamesCacheTtl = userGamesCacheTtl;
        this.clock = clock;
    }

    public UserSearchResponse getUserByNickname(String nickname) {
        return eternalReturnApiClient.getUserByNickname(nickname);
    }

    public UserOverviewResponse getUserOverview(String nickname, int seasonId, int matchingTeamMode) {
        return eternalReturnApiClient.getUserOverview(nickname, seasonId, matchingTeamMode);
    }

    public Map<String, Object> getUserStats(String userId, int seasonId) {
        return eternalReturnApiClient.getUserStats(userId, seasonId);
    }

    public UserGamesResponse getUserGames(String userId) {
        if (userGamesCacheTtl.isZero() || userGamesCacheTtl.isNegative()) {
            return eternalReturnApiClient.getUserGames(userId);
        }

        Instant now = clock.instant();
        CacheEntry<UserGamesResponse> cached = userGamesCache.get(userId);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.value();
        }

        UserGamesResponse response = eternalReturnApiClient.getUserGames(userId);
        userGamesCache.put(userId, new CacheEntry<>(response, now.plus(userGamesCacheTtl)));
        return response;
    }

    public Map<String, Object> getUserRank(String userId, int seasonId, int matchingTeamMode) {
        return eternalReturnApiClient.getUserRank(userId, seasonId, matchingTeamMode);
    }

    public GameDetailResponse getGame(int gameId) {
        return eternalReturnApiClient.getGame(gameId);
    }

    public Map<String, Object> getDataTable(String metaType) {
        return eternalReturnApiClient.getDataTable(metaType);
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
    }
}
