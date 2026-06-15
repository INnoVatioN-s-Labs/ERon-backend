package com.toyproject.eron.erapi;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.toyproject.eron.erapi.dto.GameDetailResponse;
import com.toyproject.eron.erapi.dto.GameParticipantSummary;
import com.toyproject.eron.erapi.dto.EquipmentSummary;
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
    private static final Map<Integer, String> LOCAL_CHARACTER_NAMES_BY_CODE = Map.ofEntries(
            Map.entry(68, "Alonso")
    );

    private final RestClient restClient;
    private final EternalReturnApiProperties properties;
    private volatile Map<Integer, String> characterNamesByCodeCache;
    private volatile Map<Integer, String> equipmentNamesByCodeCache;

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
        return toUserGamesResponse(response, getCharacterNamesByCode());
    }

    public Map<String, Object> getUserRank(String userId, int seasonId, int matchingTeamMode) {
        return getJson("/rank/uid/{userId}/{seasonId}/{matchingTeamMode}", userId, seasonId, matchingTeamMode);
    }

    public GameDetailResponse getGame(int gameId) {
        Map<String, Object> response = getJson("/games/{gameId}", gameId);
        return toGameDetailResponse(response, getCharacterNamesByCode(), getEquipmentNamesByCode());
    }

    public Map<String, Object> getDataTable(String metaType) {
        return getJson("/data/{metaType}", metaType);
    }

    private Map<String, Object> getJson(String path, Object... uriVariables) {
        assertApiKeyConfigured();

        try {
            Map<String, Object> response = restClient.get()
                    .uri(path, uriVariables)
                    .retrieve()
                    .body(MAP_RESPONSE_TYPE);
            return response == null ? Map.of() : response;
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

    private UserGamesResponse toUserGamesResponse(Map<String, Object> response, Map<Integer, String> characterNamesByCode) {
        List<UserGameSummary> games = List.of();
        if (response.get("userGames") instanceof List<?> userGames) {
            games = userGames.stream()
                    .map(this::asMap)
                    .filter(game -> game != null)
                    .map(game -> toUserGameSummary(game, characterNamesByCode))
                    .toList();
        }

        return new UserGamesResponse(games, toInteger(response.get("next")));
    }

    private UserGameSummary toUserGameSummary(Map<String, Object> game, Map<Integer, String> characterNamesByCode) {
        Integer characterNum = toInteger(game.get("characterNum"));

        return new UserGameSummary(
                toInteger(game.get("gameId")),
                valueAsString(game.get("nickname")),
                toInteger(game.get("seasonId")),
                toInteger(game.get("matchingMode")),
                toInteger(game.get("matchingTeamMode")),
                characterNum,
                characterNameFor(characterNum, characterNamesByCode),
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

    private GameDetailResponse toGameDetailResponse(
            Map<String, Object> response,
            Map<Integer, String> characterNamesByCode,
            Map<Integer, String> equipmentNamesByCode
    ) {
        List<GameParticipantSummary> participants = List.of();
        if (response.get("userGames") instanceof List<?> userGames) {
            participants = userGames.stream()
                    .map(this::asMap)
                    .filter(game -> game != null)
                    .map(game -> toGameParticipantSummary(game, characterNamesByCode, equipmentNamesByCode))
                    .toList();
        }

        Map<String, Object> firstGame = firstUserGame(response);

        return new GameDetailResponse(
                toInteger(firstGame.get("gameId")),
                toInteger(firstGame.get("seasonId")),
                toInteger(firstGame.get("matchingMode")),
                toInteger(firstGame.get("matchingTeamMode")),
                valueAsString(firstGame.get("startDtm")),
                toInteger(firstGame.get("duration")),
                toInteger(firstGame.get("playTime")),
                toInteger(firstGame.get("matchSize")),
                participants.size(),
                participants
        );
    }

    private Map<String, Object> firstUserGame(Map<String, Object> response) {
        if (response.get("userGames") instanceof List<?> userGames && !userGames.isEmpty()) {
            Map<String, Object> firstGame = asMap(userGames.get(0));
            if (firstGame != null) {
                return firstGame;
            }
        }

        return Map.of();
    }

    private GameParticipantSummary toGameParticipantSummary(
            Map<String, Object> game,
            Map<Integer, String> characterNamesByCode,
            Map<Integer, String> equipmentNamesByCode
    ) {
        Integer characterNum = toInteger(game.get("characterNum"));

        return new GameParticipantSummary(
                valueAsString(game.get("nickname")),
                toInteger(game.get("teamNumber")),
                toInteger(game.get("gameRank")),
                characterNum,
                characterNameFor(characterNum, characterNamesByCode),
                toInteger(game.get("characterLevel")),
                toInteger(game.get("playerKill")),
                toInteger(game.get("playerAssistant")),
                toInteger(game.get("playerDeaths")),
                toInteger(game.get("monsterKill")),
                toInteger(game.get("teamKill")),
                toInteger(game.get("damageToPlayer")),
                toInteger(game.get("damageFromPlayer")),
                toInteger(game.get("damageToMonster")),
                toInteger(game.get("healAmount")),
                toInteger(game.get("protectAbsorb")),
                toInteger(game.get("bestWeapon")),
                toInteger(game.get("bestWeaponLevel")),
                toInteger(game.get("rankPoint")),
                toInteger(game.get("victory")),
                toInteger(game.get("playTime")),
                toEquipmentSummaries(asMap(game.get("equipment")), asMap(game.get("equipmentGrade")), equipmentNamesByCode)
        );
    }

    private Map<String, EquipmentSummary> toEquipmentSummaries(
            Map<String, Object> equipment,
            Map<String, Object> equipmentGrade,
            Map<Integer, String> equipmentNamesByCode
    ) {
        if (equipment == null) {
            return Map.of();
        }

        return equipment.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            Integer itemCode = toInteger(entry.getValue());
                            return new EquipmentSummary(
                                    itemCode,
                                    equipmentNameFor(itemCode, equipmentNamesByCode),
                                    equipmentGrade == null ? null : toInteger(equipmentGrade.get(entry.getKey()))
                            );
                        },
                        (first, second) -> first
                ));
    }

    private String characterNameFor(Integer characterNum, Map<Integer, String> characterNamesByCode) {
        if (characterNum == null) {
            return null;
        }

        return characterNamesByCode.getOrDefault(characterNum, "Unknown Character (" + characterNum + ")");
    }

    private String equipmentNameFor(Integer itemCode, Map<Integer, String> equipmentNamesByCode) {
        if (itemCode == null) {
            return null;
        }

        return equipmentNamesByCode.getOrDefault(itemCode, "Unknown Item (" + itemCode + ")");
    }

    private Map<Integer, String> getCharacterNamesByCode() {
        Map<Integer, String> cachedCharacterNames = characterNamesByCodeCache;
        if (cachedCharacterNames != null) {
            return cachedCharacterNames;
        }

        synchronized (this) {
            if (characterNamesByCodeCache == null) {
                characterNamesByCodeCache = loadCharacterNamesByCode();
            }

            return characterNamesByCodeCache;
        }
    }

    private Map<Integer, String> loadCharacterNamesByCode() {
        Map<Integer, String> characterNamesByCode = new java.util.HashMap<>(LOCAL_CHARACTER_NAMES_BY_CODE);
        characterNamesByCode.putAll(loadNamesByCode("Character"));

        return Map.copyOf(characterNamesByCode);
    }

    private Map<Integer, String> getEquipmentNamesByCode() {
        Map<Integer, String> cachedEquipmentNames = equipmentNamesByCodeCache;
        if (cachedEquipmentNames != null) {
            return cachedEquipmentNames;
        }

        synchronized (this) {
            if (equipmentNamesByCodeCache == null) {
                equipmentNamesByCodeCache = loadEquipmentNamesByCode();
            }

            return equipmentNamesByCodeCache;
        }
    }

    private Map<Integer, String> loadEquipmentNamesByCode() {
        Map<Integer, String> weaponNamesByCode = loadNamesByCode("ItemWeapon");
        Map<Integer, String> armorNamesByCode = loadNamesByCode("ItemArmor");

        return java.util.stream.Stream
                .concat(weaponNamesByCode.entrySet().stream(), armorNamesByCode.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (first, second) -> first
                ));
    }

    private Map<Integer, String> loadNamesByCode(String metaType) {
        try {
            return toNamesByCode(getDataTable(metaType));
        } catch (EternalReturnApiException exception) {
            return Map.of();
        }
    }

    private Map<Integer, String> toNamesByCode(Map<String, Object> response) {
        if (!(response.get("data") instanceof List<?> characters)) {
            return Map.of();
        }

        return characters.stream()
                .map(this::asMap)
                .filter(data -> data != null)
                .filter(data -> toInteger(data.get("code")) != null)
                .filter(data -> valueAsString(data.get("name")) != null)
                .collect(Collectors.toMap(
                        data -> toInteger(data.get("code")),
                        data -> valueAsString(data.get("name")),
                        (first, second) -> first
                ));
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
