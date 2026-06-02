package com.toyproject.eron.erapi;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

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

        if (user == null || user.get("userNum") == null) {
            throw new EternalReturnApiException(HttpStatus.NOT_FOUND, "Eternal Return user not found.");
        }

        return new UserSearchResponse(
                ((Number) user.get("userNum")).intValue(),
                String.valueOf(user.get("nickname")),
                response
        );
    }

    public Map<String, Object> getUserStats(int userNum, int seasonId) {
        return getJson("/user/stats/{userNum}/{seasonId}", userNum, seasonId);
    }

    public Map<String, Object> getUserGames(int userNum) {
        return getJson("/user/games/{userNum}", userNum);
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

    private void assertApiKeyConfigured() {
        if (!StringUtils.hasText(properties.getKey())) {
            throw new EternalReturnApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "ER_API_KEY environment variable is not configured."
            );
        }
    }
}
