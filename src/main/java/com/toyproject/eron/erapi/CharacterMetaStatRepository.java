package com.toyproject.eron.erapi;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.toyproject.eron.erapi.dto.UserGameSummary;

@Repository
public class CharacterMetaStatRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final TacticalSkillNameResolver tacticalSkillNameResolver = new TacticalSkillNameResolver();

    @Autowired
    public CharacterMetaStatRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    CharacterMetaStatRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public int saveSamples(String sourceUserId, List<UserGameSummary> games) {
        int savedCount = 0;
        for (UserGameSummary game : games) {
            if (game.gameId() == null || sourceUserId == null || sourceUserId.isBlank()) {
                continue;
            }
            if (exists(game.gameId(), sourceUserId)) {
                continue;
            }

            savedCount += insertSample(sourceUserId, game);
        }

        return savedCount;
    }

    public List<Map<String, Object>> findCharacterMeta(
            int seasonId,
            int matchingTeamMode,
            int minimumCharacterGames,
            int limit
    ) {
        String sql = """
                SELECT
                    character_num AS characterNum,
                    MIN(character_name) AS characterName,
                    COUNT(*) AS gameCount,
                    SUM(CASE WHEN game_rank = 1 THEN 1 ELSE 0 END) AS winCount,
                    SUM(CASE WHEN game_rank <= 3 THEN 1 ELSE 0 END) AS top3Count,
                    ROUND(AVG(CAST(game_rank AS DOUBLE)), 2) AS averageRank,
                    ROUND(AVG(CAST(player_kill AS DOUBLE)), 2) AS averageKills,
                    ROUND(AVG(CAST(damage_to_player AS DOUBLE)), 2) AS averageDamageToPlayer,
                    ROUND(AVG(CAST(team_kill AS DOUBLE)), 2) AS averageTeamKill
                FROM character_meta_match_sample
                WHERE season_id = ?
                  AND matching_team_mode = ?
                  AND character_num IS NOT NULL
                GROUP BY character_num
                HAVING COUNT(*) >= ?
                ORDER BY
                    ROUND(AVG(CASE WHEN game_rank <= 3 THEN 1.0 ELSE 0.0 END), 4) DESC,
                    COUNT(*) DESC,
                    ROUND(AVG(CAST(game_rank AS DOUBLE)), 2) ASC,
                    character_num ASC
                LIMIT ?
                """;

        int totalGames = totalGames(seasonId, matchingTeamMode);
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            int gameCount = rs.getInt("gameCount");
            int winCount = rs.getInt("winCount");
            int top3Count = rs.getInt("top3Count");
            double top3Rate = rate(top3Count, gameCount);
            double winRate = rate(winCount, gameCount);
            double pickRate = rate(gameCount, totalGames);
            double averageRank = rs.getDouble("averageRank");
            double metaScore = metaScore(winRate, top3Rate, averageRank, pickRate, gameCount);

            return Map.ofEntries(
                    Map.entry("characterNum", rs.getInt("characterNum")),
                    Map.entry("characterName", rs.getString("characterName")),
                    Map.entry("gameCount", gameCount),
                    Map.entry("pickRate", pickRate),
                    Map.entry("winCount", winCount),
                    Map.entry("winRate", winRate),
                    Map.entry("top3Count", top3Count),
                    Map.entry("top3Rate", top3Rate),
                    Map.entry("averageRank", averageRank),
                    Map.entry("averageKills", rs.getDouble("averageKills")),
                    Map.entry("averageDamageToPlayer", rs.getDouble("averageDamageToPlayer")),
                    Map.entry("averageTeamKill", rs.getDouble("averageTeamKill")),
                    Map.entry("sampleConfidence", sampleConfidence(gameCount)),
                    Map.entry("metaScore", metaScore)
            );
        }, seasonId, matchingTeamMode, Math.max(1, minimumCharacterGames), Math.max(1, limit));
    }

    public List<Map<String, Object>> findCharacterWeaponMeta(
            int seasonId,
            int matchingTeamMode,
            int minimumCombinationGames,
            int limit
    ) {
        String sql = """
                SELECT
                    character_num AS characterNum,
                    MIN(character_name) AS characterName,
                    best_weapon AS weaponType,
                    MIN(best_weapon_name) AS weaponName,
                    tactical_skill_group_code AS tacticalSkillGroupCode,
                    MIN(CASE
                        WHEN tactical_skill IS NOT NULL
                         AND tactical_skill NOT LIKE '전술 스킬 %'
                        THEN tactical_skill
                        ELSE NULL
                    END) AS tacticalSkill,
                    COUNT(*) AS gameCount,
                    SUM(CASE WHEN game_rank = 1 THEN 1 ELSE 0 END) AS winCount,
                    SUM(CASE WHEN game_rank <= 3 THEN 1 ELSE 0 END) AS top3Count,
                    ROUND(AVG(CAST(game_rank AS DOUBLE)), 2) AS averageRank,
                    ROUND(AVG(CAST(player_kill AS DOUBLE)), 2) AS averageKills,
                    ROUND(AVG(CAST(damage_to_player AS DOUBLE)), 2) AS averageDamageToPlayer,
                    ROUND(AVG(CAST(team_kill AS DOUBLE)), 2) AS averageTeamKill
                FROM character_meta_match_sample
                WHERE season_id = ?
                  AND matching_team_mode = ?
                  AND character_num IS NOT NULL
                  AND best_weapon IS NOT NULL
                  AND tactical_skill_group_code IS NOT NULL
                GROUP BY character_num, best_weapon, tactical_skill_group_code
                HAVING COUNT(*) >= ?
                ORDER BY
                    ROUND(AVG(CASE WHEN game_rank <= 3 THEN 1.0 ELSE 0.0 END), 4) DESC,
                    COUNT(*) DESC,
                    ROUND(AVG(CAST(game_rank AS DOUBLE)), 2) ASC,
                    character_num ASC,
                    best_weapon ASC,
                    tactical_skill_group_code ASC
                LIMIT ?
                """;

        int totalGames = totalGames(seasonId, matchingTeamMode);
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            int gameCount = rs.getInt("gameCount");
            int winCount = rs.getInt("winCount");
            int top3Count = rs.getInt("top3Count");
            double top3Rate = rate(top3Count, gameCount);
            double winRate = rate(winCount, gameCount);
            double pickRate = rate(gameCount, totalGames);
            double averageRank = rs.getDouble("averageRank");
            double metaScore = metaScore(winRate, top3Rate, averageRank, pickRate, gameCount);

            return Map.ofEntries(
                    Map.entry("characterNum", rs.getInt("characterNum")),
                    Map.entry("characterName", rs.getString("characterName")),
                    Map.entry("weaponType", rs.getInt("weaponType")),
                    Map.entry("weaponName", weaponNameOrFallback(rs.getString("weaponName"), rs.getInt("weaponType"))),
                    Map.entry("tacticalSkillGroupCode", integerOrNull(rs, "tacticalSkillGroupCode")),
                    Map.entry("tacticalSkill", tacticalSkillOrFallback(
                            rs.getString("tacticalSkill"),
                            integerOrNull(rs, "tacticalSkillGroupCode")
                    )),
                    Map.entry("gameCount", gameCount),
                    Map.entry("pickRate", pickRate),
                    Map.entry("winCount", winCount),
                    Map.entry("winRate", winRate),
                    Map.entry("top3Count", top3Count),
                    Map.entry("top3Rate", top3Rate),
                    Map.entry("averageRank", averageRank),
                    Map.entry("averageKills", rs.getDouble("averageKills")),
                    Map.entry("averageDamageToPlayer", rs.getDouble("averageDamageToPlayer")),
                    Map.entry("averageTeamKill", rs.getDouble("averageTeamKill")),
                    Map.entry("sampleConfidence", sampleConfidence(gameCount)),
                    Map.entry("metaScore", metaScore)
            );
        }, seasonId, matchingTeamMode, Math.max(1, minimumCombinationGames), Math.max(1, limit));
    }

    public int totalGames(int seasonId, int matchingTeamMode) {
        Integer totalGames = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM character_meta_match_sample
                WHERE season_id = ? AND matching_team_mode = ?
                """,
                Integer.class,
                seasonId,
                matchingTeamMode
        );
        return totalGames == null ? 0 : totalGames;
    }

    public int nextRankIndex(String stateKey) {
        List<Integer> indexes = jdbcTemplate.query(
                """
                SELECT next_rank_index
                FROM character_meta_collection_state
                WHERE state_key = ?
                """,
                (rs, rowNum) -> rs.getInt("next_rank_index"),
                stateKey
        );
        return indexes.isEmpty() ? 0 : Math.max(0, indexes.get(0));
    }

    public void saveNextRankIndex(String stateKey, int nextRankIndex) {
        int updatedCount = jdbcTemplate.update(
                """
                UPDATE character_meta_collection_state
                SET next_rank_index = ?, updated_at = ?
                WHERE state_key = ?
                """,
                Math.max(0, nextRankIndex),
                Timestamp.from(Instant.now(clock)),
                stateKey
        );
        if (updatedCount > 0) {
            return;
        }

        jdbcTemplate.update(
                """
                INSERT INTO character_meta_collection_state (
                    state_key,
                    next_rank_index,
                    updated_at
                ) VALUES (?, ?, ?)
                """,
                stateKey,
                Math.max(0, nextRankIndex),
                Timestamp.from(Instant.now(clock))
        );
    }

    private boolean exists(Long gameId, String sourceUserId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM character_meta_match_sample
                WHERE game_id = ? AND source_user_id = ?
                """,
                Integer.class,
                gameId,
                sourceUserId
        );
        return count != null && count > 0;
    }

    private int insertSample(String sourceUserId, UserGameSummary game) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        return jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO character_meta_match_sample (
                        game_id,
                        source_user_id,
                        nickname,
                        season_id,
                        matching_team_mode,
                        character_num,
                        character_name,
                        best_weapon,
                        best_weapon_name,
                        tactical_skill_group_code,
                        tactical_skill,
                        game_rank,
                        player_kill,
                        player_assistant,
                        player_deaths,
                        damage_to_player,
                        team_kill,
                        rank_point,
                        mmr_gain,
                        start_dtm,
                        collected_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.NO_GENERATED_KEYS
            );
            statement.setLong(1, game.gameId());
            statement.setString(2, sourceUserId);
            statement.setString(3, game.nickname());
            setInteger(statement, 4, game.seasonId());
            setInteger(statement, 5, game.matchingTeamMode());
            setInteger(statement, 6, game.characterNum());
            statement.setString(7, game.characterName());
            setInteger(statement, 8, game.bestWeapon());
            statement.setString(9, game.bestWeaponName());
            setInteger(statement, 10, game.tacticalSkillGroupCode());
            statement.setString(11, game.tacticalSkill());
            setInteger(statement, 12, game.gameRank());
            setInteger(statement, 13, game.playerKill());
            setInteger(statement, 14, game.playerAssistant());
            setInteger(statement, 15, game.playerDeaths());
            setInteger(statement, 16, game.damageToPlayer());
            setInteger(statement, 17, game.teamKill());
            setInteger(statement, 18, game.rankPoint());
            setInteger(statement, 19, game.mmrGain());
            statement.setString(20, game.startDtm());
            statement.setTimestamp(21, Timestamp.from(Instant.now(clock)));
            return statement;
        }, keyHolder);
    }

    private Integer integerOrNull(java.sql.ResultSet rs, String columnLabel) throws java.sql.SQLException {
        int value = rs.getInt(columnLabel);
        return rs.wasNull() ? null : value;
    }

    private String weaponNameOrFallback(String weaponName, int weaponType) {
        if (weaponName != null && !weaponName.isBlank()) {
            return weaponName;
        }

        return "무기 마스터리 " + weaponType;
    }

    private String tacticalSkillOrFallback(String tacticalSkill, Integer tacticalSkillGroupCode) {
        return tacticalSkillNameResolver.resolve(tacticalSkillGroupCode, tacticalSkill);
    }

    private void setInteger(PreparedStatement statement, int index, Integer value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
            return;
        }

        statement.setInt(index, value);
    }

    private double rate(int numerator, int denominator) {
        if (denominator == 0) {
            return 0.0;
        }

        return round((double) numerator / denominator);
    }

    private double sampleConfidence(int gameCount) {
        return round(Math.min(1.0, gameCount / 100.0));
    }

    private double metaScore(double winRate, double top3Rate, double averageRank, double pickRate, int gameCount) {
        double rankScore = averageRank <= 0 ? 0.0 : 1.0 / averageRank;
        double rawScore = (winRate * 0.35)
                + (top3Rate * 0.35)
                + (rankScore * 0.15)
                + (pickRate * 0.15);
        double confidenceWeight = 0.60 + (sampleConfidence(gameCount) * 0.40);

        return round(rawScore * confidenceWeight);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
