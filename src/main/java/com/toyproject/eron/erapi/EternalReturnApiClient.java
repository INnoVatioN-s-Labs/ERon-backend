package com.toyproject.eron.erapi;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
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
import com.toyproject.eron.erapi.dto.UserStatsResponse;
import com.toyproject.eron.global.config.EternalReturnApiProperties;

@Component
public class EternalReturnApiClient {

    private static final Logger log = LoggerFactory.getLogger(EternalReturnApiClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final Pattern LOCAL_NAME_ENTRY_PATTERN =
            Pattern.compile("\"code\"\\s*:\\s*(\\d+).*?\"name\"\\s*:\\s*\"([^\"]+)\"");
    // 공식 l10n 텍스트의 한 줄 형식: Character/Name/{코드}<구분자>{한글명}
    // (구분자는 '┃' U+2503 이지만 버전에 흔들려도 되도록 \D 한 글자로 매칭)
    private static final Pattern L10N_CHARACTER_NAME_PATTERN =
            Pattern.compile("^Character/Name/(\\d+)\\D(.+)$", Pattern.MULTILINE);

    private final RestClient restClient;
    private final EternalReturnApiProperties properties;
    private final CharacterNameResolver characterNameResolver = new CharacterNameResolver();
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
        Map<String, Object> stats = getUserStats(user.userId(), seasonId);
        UserGamesResponse games = getUserGames(user.userId());

        return new UserOverviewResponse(
                user,
                rank,
                new UserStatsResponse(user.userId(), seasonId, asListOfMaps(stats.get("userStats")), stats),
                games,
                UserRecentStatsResponse.from(games)
        );
    }

    public Map<String, Object> getUserStats(String userId, int seasonId) {
        return getJson("/user/stats/uid/{userId}/{seasonId}", userId, seasonId);
    }

    public UserGamesResponse getUserGames(String userId) {
        return getUserGames(userId, null);
    }

    public UserGamesResponse getUserGames(String userId, Long next) {
        Map<String, Object> response;
        if (next == null) {
            response = getJson("/user/games/uid/{userId}", userId);
        } else {
            response = getUserGamesJson(userId, next);
        }
        return toUserGamesResponse(response, getCharacterNamesByCode());
    }

    private Map<String, Object> getUserGamesJson(String userId, Long next) {
        assertApiKeyConfigured();

        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user/games/uid/{userId}")
                            .queryParam("next", next)
                            .build(userId))
                    .retrieve()
                    .body(MAP_RESPONSE_TYPE);
            return response == null ? Map.of() : response;
        } catch (HttpStatusCodeException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw timeoutException();
        }
    }

    public Map<String, Object> getUserRank(String userId, int seasonId, int matchingTeamMode) {
        return getJson("/rank/uid/{userId}/{seasonId}/{matchingTeamMode}", userId, seasonId, matchingTeamMode);
    }

    public Map<String, Object> getTopRankings(int seasonId, int matchingTeamMode) {
        return getJson("/rank/top/{seasonId}/{matchingTeamMode}", seasonId, matchingTeamMode);
    }

    public GameDetailResponse getGame(long gameId) {
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

    private List<Map<String, Object>> asListOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        return list.stream()
                .map(this::asMap)
                .filter(map -> map != null)
                .toList();
    }

    private UserGamesResponse toUserGamesResponse(Map<String, Object> response, Map<Integer, String> characterNamesByCode) {
        List<UserGameSummary> games = List.of();
        List<?> userGames = userGamesFrom(response);
        if (!userGames.isEmpty()) {
            games = userGames.stream()
                    .map(this::asMap)
                    .filter(game -> game != null)
                    .map(game -> toUserGameSummary(game, characterNamesByCode))
                    .toList();
        }

        return new UserGamesResponse(games, toLong(response.get("next")));
    }

    private UserGameSummary toUserGameSummary(Map<String, Object> game, Map<Integer, String> characterNamesByCode) {
        Integer characterNum = toInteger(game.get("characterNum"));

        return new UserGameSummary(
                toLong(game.get("gameId")),
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
        List<?> userGames = userGamesFrom(response);
        List<GameParticipantSummary> participants = userGames.stream()
                .map(this::asMap)
                .filter(game -> game != null)
                .map(game -> toGameParticipantSummary(game, characterNamesByCode, equipmentNamesByCode))
                .toList();

        Map<String, Object> firstGame = firstUserGame(response);

        return new GameDetailResponse(
                toLong(firstGame.get("gameId")),
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
        List<?> userGames = userGamesFrom(response);
        if (!userGames.isEmpty()) {
            Map<String, Object> firstGame = asMap(userGames.get(0));
            if (firstGame != null) {
                return firstGame;
            }
        }

        return Map.of();
    }

    private List<?> userGamesFrom(Map<String, Object> response) {
        if (response.get("userGames") instanceof List<?> userGames) {
            return userGames;
        }
        if (response.get("userGame") instanceof List<?> userGame) {
            return userGame;
        }
        if (response.get("userGame") instanceof Map<?, ?> userGame) {
            return List.of(userGame);
        }

        return List.of();
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
        return characterNameResolver.resolve(characterNum, characterNamesByCode.get(characterNum));
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
                Map<Integer, String> loaded = loadCharacterNamesByCode();
                if (loaded.isEmpty()) {
                    // 일시적 l10n/API 장애를 영구 캐싱하지 않는다. 다음 호출에서 재시도해 자가복구.
                    return loaded;
                }
                characterNamesByCodeCache = loaded;
            }

            return characterNamesByCodeCache;
        }
    }

    private Map<Integer, String> loadCharacterNamesByCode() {
        // 공식 /data/Character 는 영문명을 주고 코드 64까지만 반영돼 신규 실험체가 누락된다.
        // 대신 공식 l10n(한글) 파일에서 코드→한글명을 불러온다. 신규 실험체도 자동 포함된다.
        Map<Integer, String> fromL10n = loadCharacterNamesFromL10n("Korean");
        if (!fromL10n.isEmpty()) {
            return fromL10n;
        }

        // l10n 호출/파싱 실패 시, 영문이라도 표시되도록 /data/Character 로 폴백한다.
        log.warn("Falling back to /data/Character (English) because Korean l10n character names were unavailable.");
        return loadNamesByCode("Character");
    }

    /**
     * 공식 l10n 엔드포인트에서 실험체 코드→한글명을 불러온다.
     *
     * <p>두 단계로 동작한다. (1) {@code /l10n/{language}} 가 실제 l10n 텍스트 파일의 URL을 주고,
     * (2) 그 URL을 내려받아 {@code Character/Name/{코드}} 항목만 추려 맵으로 만든다.
     */
    private Map<Integer, String> loadCharacterNamesFromL10n(String language) {
        try {
            String fileUrl = l10nFilePath(getJson("/l10n/{language}", language));
            if (fileUrl == null) {
                return Map.of();
            }

            byte[] body = restClient.get()
                    .uri(URI.create(fileUrl))
                    .retrieve()
                    .body(byte[].class);
            if (body == null) {
                return Map.of();
            }

            return parseCharacterNamesFromL10n(new String(body, StandardCharsets.UTF_8));
        } catch (EternalReturnApiException | HttpStatusCodeException | ResourceAccessException exception) {
            log.warn("Failed to load Korean character names from l10n: {}", exception.toString());
            return Map.of();
        }
    }

    private String l10nFilePath(Map<String, Object> l10nMeta) {
        Map<String, Object> data = asMap(l10nMeta.get("data"));
        if (data == null) {
            return null;
        }

        String path = valueAsString(data.get("l10Path"));
        return StringUtils.hasText(path) ? path : null;
    }

    private Map<Integer, String> parseCharacterNamesFromL10n(String body) {
        Map<Integer, String> namesByCode = new java.util.HashMap<>();
        Matcher matcher = L10N_CHARACTER_NAME_PATTERN.matcher(body);
        while (matcher.find()) {
            String name = matcher.group(2).trim();
            if (!name.isEmpty()) {
                namesByCode.put(Integer.parseInt(matcher.group(1)), name);
            }
        }

        return namesByCode;
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
        Map<Integer, String> namesByCode = new java.util.HashMap<>();
        namesByCode.putAll(loadLocalNamesByCode("item-weapon.json"));
        namesByCode.putAll(loadLocalNamesByCode("item-armor.json"));
        namesByCode.putAll(loadLocalNamesByCode("item-special.json"));
        namesByCode.putAll(loadLocalNamesByCode("item-consumable.json"));
        namesByCode.putAll(loadNamesByCode("ItemWeapon"));
        namesByCode.putAll(loadNamesByCode("ItemArmor"));

        return Map.copyOf(namesByCode);
    }

    private Map<Integer, String> loadNamesByCode(String metaType) {
        try {
            return toNamesByCode(getDataTable(metaType));
        } catch (EternalReturnApiException exception) {
            log.warn("Failed to load ER metadata table: metaType={}, status={}", metaType, exception.getStatus());
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

    private Map<Integer, String> loadLocalNamesByCode(String filename) {
        ClassPathResource resource = new ClassPathResource("er-data/" + filename);
        if (!resource.exists()) {
            log.warn("Local ER metadata file is missing from classpath: {}", filename);
            return Map.of();
        }

        try (InputStream inputStream = resource.getInputStream()) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            Map<Integer, String> namesByCode = new java.util.HashMap<>();
            Matcher matcher = LOCAL_NAME_ENTRY_PATTERN.matcher(content);
            while (matcher.find()) {
                namesByCode.put(Integer.parseInt(matcher.group(1)), matcher.group(2));
            }

            return namesByCode;
        } catch (IOException exception) {
            log.warn("Failed to read local ER metadata file: {}", filename, exception);
            return Map.of();
        }
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        return null;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
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
