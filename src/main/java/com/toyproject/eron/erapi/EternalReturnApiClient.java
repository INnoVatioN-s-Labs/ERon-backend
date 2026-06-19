package com.toyproject.eron.erapi;

import java.io.IOException;
import java.io.InputStream;
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
    private static final Map<Integer, String> LOCAL_CHARACTER_NAMES_BY_CODE = Map.ofEntries(
            Map.entry(1, "재키"),
            Map.entry(2, "아야"),
            Map.entry(3, "피오라"),
            Map.entry(4, "매그너스"),
            Map.entry(5, "자히르"),
            Map.entry(6, "나딘"),
            Map.entry(7, "현우"),
            Map.entry(8, "하트"),
            Map.entry(9, "아이솔"),
            Map.entry(10, "리 다이린"),
            Map.entry(11, "유키"),
            Map.entry(12, "혜진"),
            Map.entry(13, "쇼우"),
            Map.entry(14, "키아라"),
            Map.entry(15, "시셀라"),
            Map.entry(16, "실비아"),
            Map.entry(17, "아드리아나"),
            Map.entry(18, "쇼이치"),
            Map.entry(19, "엠마"),
            Map.entry(20, "레녹스"),
            Map.entry(21, "로지"),
            Map.entry(22, "루크"),
            Map.entry(23, "캐시"),
            Map.entry(24, "아델라"),
            Map.entry(25, "버니스"),
            Map.entry(26, "바바라"),
            Map.entry(27, "알렉스"),
            Map.entry(28, "수아"),
            Map.entry(29, "레온"),
            Map.entry(30, "일레븐"),
            Map.entry(31, "리오"),
            Map.entry(32, "윌리엄"),
            Map.entry(33, "니키"),
            Map.entry(34, "나타폰"),
            Map.entry(35, "얀"),
            Map.entry(36, "에바"),
            Map.entry(37, "다니엘"),
            Map.entry(38, "제니"),
            Map.entry(39, "카밀로"),
            Map.entry(40, "클로에"),
            Map.entry(41, "요한"),
            Map.entry(42, "비앙카"),
            Map.entry(43, "셀린"),
            Map.entry(44, "에키온"),
            Map.entry(45, "마이"),
            Map.entry(46, "에이든"),
            Map.entry(47, "라우라"),
            Map.entry(48, "띠아"),
            Map.entry(49, "펠릭스"),
            Map.entry(50, "엘레나"),
            Map.entry(51, "프리야"),
            Map.entry(52, "아디나"),
            Map.entry(53, "마커스"),
            Map.entry(54, "칼라"),
            Map.entry(55, "에스텔"),
            Map.entry(56, "피올로"),
            Map.entry(57, "마르티나"),
            Map.entry(58, "헤이즈"),
            Map.entry(59, "아이작"),
            Map.entry(60, "타지아"),
            Map.entry(61, "이렘"),
            Map.entry(62, "테오도르"),
            Map.entry(63, "리안"),
            Map.entry(64, "바냐"),
            Map.entry(68, "알론소")
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
        if (characterNum == null) {
            return null;
        }

        return characterNamesByCode.getOrDefault(characterNum, "실험체 " + characterNum);
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
        Map<Integer, String> characterNamesByCode = new java.util.HashMap<>(loadNamesByCode("Character"));
        characterNamesByCode.putAll(LOCAL_CHARACTER_NAMES_BY_CODE);

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
