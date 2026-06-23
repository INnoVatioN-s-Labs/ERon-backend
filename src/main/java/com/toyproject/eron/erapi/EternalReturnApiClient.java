package com.toyproject.eron.erapi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
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
import com.toyproject.eron.erapi.dto.TraitSummary;
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
    private static final Map<Integer, Integer> TACTICAL_SKILL_GROUPS = Map.ofEntries(
            Map.entry(30, 4_000_000),
            Map.entry(40, 4_001_000),
            Map.entry(50, 4_101_000),
            Map.entry(60, 4_102_000),
            Map.entry(70, 4_103_000),
            Map.entry(80, 4_104_000),
            Map.entry(90, 4_105_000),
            Map.entry(110, 4_107_000),
            Map.entry(120, 4_110_000),
            Map.entry(130, 4_112_000),
            Map.entry(140, 4_113_000),
            Map.entry(150, 4_108_000),
            Map.entry(500010, 4_501_000),
            Map.entry(500020, 4_502_000),
            Map.entry(500030, 4_503_000),
            Map.entry(500040, 4_504_000),
            Map.entry(500050, 4_505_000),
            Map.entry(500060, 4_506_000),
            Map.entry(500070, 4_507_000),
            Map.entry(500080, 4_508_000),
            Map.entry(500090, 4_509_000),
            Map.entry(500100, 4_510_000),
            Map.entry(500110, 4_511_000),
            Map.entry(500120, 4_000_000),
            Map.entry(500130, 4_001_000),
            Map.entry(500140, 4_101_000),
            Map.entry(500150, 4_102_000),
            Map.entry(500160, 4_103_000),
            Map.entry(500170, 4_104_000),
            Map.entry(500180, 4_105_000),
            Map.entry(500190, 4_107_000),
            Map.entry(500200, 4_110_000),
            Map.entry(500210, 4_112_000),
            Map.entry(500220, 4_113_000),
            Map.entry(500230, 4_108_000)
    );
    private static final Map<Integer, String> WEAPON_MASTERY_NAMES = Map.ofEntries(
            Map.entry(1, "글러브"),
            Map.entry(2, "톤파"),
            Map.entry(3, "방망이"),
            Map.entry(4, "채찍"),
            Map.entry(5, "투척"),
            Map.entry(6, "암기"),
            Map.entry(7, "활"),
            Map.entry(8, "석궁"),
            Map.entry(9, "권총"),
            Map.entry(10, "돌격소총"),
            Map.entry(11, "저격총"),
            Map.entry(13, "망치"),
            Map.entry(14, "도끼"),
            Map.entry(15, "한손검"),
            Map.entry(16, "양손검"),
            Map.entry(17, "창"),
            Map.entry(18, "쌍검"),
            Map.entry(19, "창"),
            Map.entry(20, "쌍절곤"),
            Map.entry(21, "레이피어"),
            Map.entry(22, "기타"),
            Map.entry(23, "카메라"),
            Map.entry(24, "아르카나"),
            Map.entry(25, "VF 의수")
    );

    private final RestClient restClient;
    private final EternalReturnApiProperties properties;
    private final CharacterNameResolver characterNameResolver = new CharacterNameResolver();
    private volatile Map<Integer, String> characterNamesByCodeCache;
    private volatile Map<Integer, String> equipmentNamesByCodeCache;
    private volatile Map<String, String> koreanL10nCache;

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
        Map<String, String> koreanL10n = hasTacticalSkillOrTraits(response)
                ? getKoreanL10n()
                : Map.of();
        return toUserGamesResponse(response, getCharacterNamesByCode(), koreanL10n);
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
        Map<String, String> koreanL10n = hasTacticalSkillOrTraits(response)
                ? getKoreanL10n()
                : Map.of();
        return toGameDetailResponse(response, getCharacterNamesByCode(), getEquipmentNamesByCode(), koreanL10n);
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

    private UserGamesResponse toUserGamesResponse(
            Map<String, Object> response,
            Map<Integer, String> characterNamesByCode,
            Map<String, String> koreanL10n
    ) {
        List<UserGameSummary> games = List.of();
        List<?> userGames = userGamesFrom(response);
        if (!userGames.isEmpty()) {
            games = userGames.stream()
                    .map(this::asMap)
                    .filter(game -> game != null)
                    .map(game -> toUserGameSummary(game, characterNamesByCode, koreanL10n))
                    .toList();
        }

        return new UserGamesResponse(games, toLong(response.get("next")));
    }

    private UserGameSummary toUserGameSummary(
            Map<String, Object> game,
            Map<Integer, String> characterNamesByCode,
            Map<String, String> koreanL10n
    ) {
        Integer characterNum = toInteger(game.get("characterNum"));
        Integer tacticalSkillGroupCode = tacticalSkillGroupCode(game);
        Integer bestWeapon = toInteger(game.get("bestWeapon"));

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
                toInteger(game.get("playTime")),
                bestWeapon,
                weaponMasteryNameFor(bestWeapon),
                toInteger(game.get("bestWeaponLevel")),
                tacticalSkillGroupCode,
                tacticalSkillNameFor(tacticalSkillGroupCode, koreanL10n),
                toTraitSummaries(game, koreanL10n)
        );
    }

    private String weaponMasteryNameFor(Integer bestWeapon) {
        if (bestWeapon == null) {
            return null;
        }

        return WEAPON_MASTERY_NAMES.getOrDefault(bestWeapon, "무기 마스터리 " + bestWeapon);
    }

    private GameDetailResponse toGameDetailResponse(
            Map<String, Object> response,
            Map<Integer, String> characterNamesByCode,
            Map<Integer, String> equipmentNamesByCode,
            Map<String, String> koreanL10n
    ) {
        List<?> userGames = userGamesFrom(response);
        List<GameParticipantSummary> participants = userGames.stream()
                .map(this::asMap)
                .filter(game -> game != null)
                .map(game -> toGameParticipantSummary(game, characterNamesByCode, equipmentNamesByCode, koreanL10n))
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
            Map<Integer, String> equipmentNamesByCode,
            Map<String, String> koreanL10n
    ) {
        Integer characterNum = toInteger(game.get("characterNum"));
        Integer tacticalSkillGroupCode = tacticalSkillGroupCode(game);

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
                toEquipmentSummaries(asMap(game.get("equipment")), asMap(game.get("equipmentGrade")), equipmentNamesByCode),
                tacticalSkillGroupCode,
                tacticalSkillNameFor(tacticalSkillGroupCode, koreanL10n),
                toTraitSummaries(game, koreanL10n)
        );
    }

    private boolean hasTacticalSkillOrTraits(Map<String, Object> response) {
        return userGamesFrom(response).stream()
                .map(this::asMap)
                .filter(game -> game != null)
                .anyMatch(game -> tacticalSkillGroupCode(game) != null || !traitCodes(game).isEmpty());
    }

    private Integer tacticalSkillGroupCode(Map<String, Object> game) {
        Integer tacticalSkillGroupCode = toInteger(game.get("tacticalSkillGroupCode"));
        if (tacticalSkillGroupCode != null) {
            return tacticalSkillGroupCode;
        }

        return toInteger(game.get("tacticalSkillGroup"));
    }

    private List<TraitSummary> toTraitSummaries(Map<String, Object> game, Map<String, String> koreanL10n) {
        return traitCodes(game).stream()
                .map(traitCode -> new TraitSummary(traitCode, traitNameFor(traitCode, koreanL10n)))
                .toList();
    }

    private List<Integer> traitCodes(Map<String, Object> game) {
        List<Integer> traitCodes = new ArrayList<>();
        Integer firstCore = toInteger(game.get("traitFirstCore"));
        if (firstCore != null) {
            traitCodes.add(firstCore);
        }
        traitCodes.addAll(toIntegerList(game.get("traitFirstSub")));
        traitCodes.addAll(toIntegerList(game.get("traitSecondSub")));

        return traitCodes;
    }

    private List<Integer> toIntegerList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }

        return values.stream()
                .map(this::toInteger)
                .filter(code -> code != null)
                .toList();
    }

    private String tacticalSkillNameFor(Integer tacticalSkillGroupCode, Map<String, String> koreanL10n) {
        if (tacticalSkillGroupCode == null) {
            return null;
        }

        Integer skillGroupCode = TACTICAL_SKILL_GROUPS.getOrDefault(tacticalSkillGroupCode, tacticalSkillGroupCode);
        return koreanL10n.get("Skill/Group/Name/" + skillGroupCode);
    }

    private String traitNameFor(Integer traitCode, Map<String, String> koreanL10n) {
        return koreanL10n.get("Trait/Name/" + traitCode);
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
                characterNamesByCodeCache = loadCharacterNamesByCode();
            }

            return characterNamesByCodeCache;
        }
    }

    private Map<Integer, String> loadCharacterNamesByCode() {
        return Map.copyOf(loadNamesByCode("Character"));
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

    private Map<String, String> getKoreanL10n() {
        Map<String, String> cachedKoreanL10n = koreanL10nCache;
        if (cachedKoreanL10n != null) {
            return cachedKoreanL10n;
        }

        synchronized (this) {
            if (koreanL10nCache == null) {
                Map<String, String> loadedKoreanL10n = loadKoreanL10n();
                if (!loadedKoreanL10n.isEmpty()) {
                    koreanL10nCache = loadedKoreanL10n;
                }
                return loadedKoreanL10n;
            }

            return koreanL10nCache;
        }
    }

    private Map<String, String> loadKoreanL10n() {
        try {
            Map<String, Object> response = getJson("/l10n/{language}", "Korean");
            Map<String, Object> data = asMap(response.get("data"));
            String l10nPath = l10nPathFrom(response, data);
            if (!StringUtils.hasText(l10nPath)) {
                log.warn("Korean ER language data response did not include a download path.");
                return Map.of();
            }

            String l10n = getText(l10nPath);
            Map<String, String> entries = new HashMap<>();
            l10n.lines().forEach(line -> {
                int delimiterIndex = line.indexOf('\u2503');
                if (delimiterIndex < 0) {
                    delimiterIndex = line.indexOf('\t');
                }
                if (delimiterIndex > 0) {
                    entries.put(line.substring(0, delimiterIndex), line.substring(delimiterIndex + 1));
                }
            });
            return Map.copyOf(entries);
        } catch (EternalReturnApiException exception) {
            log.warn("Failed to load Korean ER language data: status={}", exception.getStatus());
            return Map.of();
        }
    }

    private String l10nPathFrom(Map<String, Object> response, Map<String, Object> data) {
        if (data != null) {
            String l10nPath = valueAsString(data.get("l10Path"));
            if (StringUtils.hasText(l10nPath)) {
                return l10nPath;
            }
            l10nPath = valueAsString(data.get("l10nPath"));
            if (StringUtils.hasText(l10nPath)) {
                return l10nPath;
            }
        }

        return valueAsString(response.get("l10nPath"));
    }

    private String getText(String url) {
        assertApiKeyConfigured();

        try {
            String response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);
            return response == null ? "" : response;
        } catch (HttpStatusCodeException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw timeoutException();
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
