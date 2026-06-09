package com.toyproject.eron.erapi;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.toyproject.eron.erapi.dto.UserGameSummary;
import com.toyproject.eron.erapi.dto.UserGamesResponse;
import com.toyproject.eron.erapi.dto.UserOverviewResponse;
import com.toyproject.eron.erapi.dto.UserRecentStatsResponse;
import com.toyproject.eron.erapi.dto.UserSearchResponse;
import com.toyproject.eron.global.config.EternalReturnApiProperties;

@Component
public class EternalReturnApiClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final EternalReturnApiProperties properties;

    public EternalReturnApiClient(RestClient eternalReturnRestClient, EternalReturnApiProperties properties) {
        this.restClient = eternalReturnRestClient;
        this.properties = properties;
    }

    public UserSearchResponse getUserByNickname(String nickname) {
        assertApiKeyConfigured();

        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user/nickname")
                            .queryParam("query", nickname)
                            .build())
                    .retrieve()
                    .body(MAP_RESPONSE_TYPE);
        } catch (HttpStatusCodeException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw timeoutException();
        }

        Map<String, Object> user = asMap(response.get("user"));

        if (user == null || user.get("userId") == null) {
            throw new EternalReturnApiException(HttpStatus.NOT_FOUND, "Eternal Return user not found.");
        }

        return new UserSearchResponse(
                valueAsString(user.get("userId")),
                String.valueOf(user.get("nickname")),
                response
        );
    }

    public UserOverviewResponse getUserOverview(String nickname, int seasonId, int matchingTeamMode) {
        UserSearchResponse user = getUserByNickname(nickname);
        Map<String, Object> rank = getUserRank(user.userId(), seasonId, matchingTeamMode);
        UserGamesResponse games = getUserGames(user.userId());

        return new UserOverviewResponse(user, rank, games, UserRecentStatsResponse.from(games));
    }

    public Map<String, Object> getUserStats(String userId, int seasonId) {
        return getJson("/user/stats/uid/{userId}/{seasonId}", userId, seasonId);
    }

    public UserGamesResponse getUserGames(String userId) {
        Map<String, Object> response = getJson("/user/games/uid/{userId}", userId);
        return toUserGamesResponse(response);
    }

    public Map<String, Object> getUserRank(String userId, int seasonId, int matchingTeamMode) {
        return getJson("/rank/uid/{userId}/{seasonId}/{matchingTeamMode}", userId, seasonId, matchingTeamMode);
    }

    public Map<String, Object> getGame(int gameId) {
        return getJson("/games/{gameId}", gameId);
    }

    public Map<String, Object> getDataTable(String metaType) {
        return getJson("/data/{metaType}", metaType);
    }

    private Map<String, Object> getJson(String path, Object... uriVariables) {
        assertApiKeyConfigured();

        try {
            return restClient.get()
                    .uri(path, uriVariables)
                    .retrieve()
                    .body(MAP_RESPONSE_TYPE);
        } catch (HttpStatusCodeException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw timeoutException();
        }
    }

    private EternalReturnApiException toApiException(HttpStatusCodeException exception) {
        return new EternalReturnApiException(
                HttpStatus.valueOf(exception.getStatusCode().value()),
                Optional.ofNullable(exception.getResponseBodyAsString())
                        .filter(StringUtils::hasText)
                        .orElse("Eternal Return API request failed.")
        );
    }

    private EternalReturnApiException timeoutException() {
        return new EternalReturnApiException(
                HttpStatus.GATEWAY_TIMEOUT,
                "Eternal Return API did not respond in time."
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        return null;
    }

    private UserGamesResponse toUserGamesResponse(Map<String, Object> response) {
        List<UserGameSummary> games = List.of();
        if (response.get("userGames") instanceof List<?> userGames) {
            games = userGames.stream()
                    .map(this::asMap)
                    .filter(game -> game != null)
                    .map(this::toUserGameSummary)
                    .toList();
        }

        return new UserGamesResponse(games, toInteger(response.get("next")));
    }

    private UserGameSummary toUserGameSummary(Map<String, Object> game) {
        return new UserGameSummary(
                toInteger(game.get("gameId")),
                valueAsString(game.get("nickname")),
                toInteger(game.get("seasonId")),
                toInteger(game.get("matchingMode")),
                toInteger(game.get("matchingTeamMode")),
                toInteger(game.get("characterNum")),
                toInteger(game.get("gameRank")),
                toInteger(game.get("playerKill")),
                toInteger(game.get("playerAssistant")),
                toInteger(game.get("playerDeaths")),
                toInteger(game.get("damageToPlayer")),
                toInteger(game.get("teamKill")),
                toInteger(game.get("rankPoint")),
                toInteger(game.get("mmrBefore")),
                toInteger(game.get("mmrGain")),
                toInteger(game.get("mmrAfter")),
                valueAsString(game.get("startDtm")),
                toInteger(game.get("playTime"))
        );
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        return null;
    }

    private String valueAsString(Object value) {
        if (value == null) {
            return null;
        }

        return String.valueOf(value);
    }

    private void assertApiKeyConfigured() {
        if (!StringUtils.hasText(properties.getKey())) {
            throw new EternalReturnApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "ER_API_KEY environment variable is not configured."
            );
        }
    }
}
