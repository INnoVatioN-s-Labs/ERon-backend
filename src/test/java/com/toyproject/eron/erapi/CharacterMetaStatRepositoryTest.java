package com.toyproject.eron.erapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.toyproject.eron.erapi.dto.UserGameSummary;

class CharacterMetaStatRepositoryTest {

    private CharacterMetaStatRepository repository;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:character_meta_stats;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
        populator.execute(dataSource);

        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM character_meta_match_sample");
        repository = new CharacterMetaStatRepository(
                jdbcTemplate,
                Clock.fixed(Instant.parse("2026-06-17T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void saveSamplesSkipsDuplicateGameForSameSourceUser() {
        UserGameSummary game = userGame(1001L, "Jackie", 1, 1, 6);

        int firstSavedCount = repository.saveSamples("user-1", List.of(game));
        int secondSavedCount = repository.saveSamples("user-1", List.of(game));

        assertThat(firstSavedCount).isEqualTo(1);
        assertThat(secondSavedCount).isZero();
        assertThat(repository.totalGames(39, 3, 7)).isEqualTo(1);
    }

    @Test
    void findCharacterMetaOrdersByMetaScoreDescending() {
        repository.saveSamples("user-1", List.of(
                userGame(1001L, "Jackie", 1, 1, 6),
                userGame(1002L, "Jackie", 1, 4, 2),
                userGame(1003L, "Luke", 22, 3, 5)
        ));

        List<Map<String, Object>> characters = repository.findCharacterMeta(39, 3, 1, 5, 7);

        assertThat(characters).hasSize(2);
        // Jackie: metaScore 0.31 > Luke: metaScore 0.27 -> Jackie must come first.
        assertThat(characters.get(0))
                .containsEntry("characterNum", 1)
                .containsEntry("characterName", "Jackie")
                .containsEntry("gameCount", 2)
                .containsEntry("pickRate", 0.67)
                .containsEntry("winRate", 0.5)
                .containsEntry("top3Rate", 0.5)
                .containsEntry("averageRank", 2.5)
                .containsEntry("metaScore", 0.31);
        assertThat(characters.get(1))
                .containsEntry("characterNum", 22)
                .containsEntry("characterName", "Luke")
                .containsEntry("gameCount", 1)
                .containsEntry("pickRate", 0.33)
                .containsEntry("winRate", 0.0)
                .containsEntry("top3Rate", 1.0)
                .containsEntry("averageRank", 3.0)
                .containsEntry("averageKills", 5.0)
                .containsEntry("metaScore", 0.27);
        assertThat((double) characters.get(0).get("metaScore"))
                .isGreaterThan((double) characters.get(1).get("metaScore"));
    }

    @Test
    void findCharacterMetaExcludesCharactersBelowMinimumGames() {
        repository.saveSamples("user-1", List.of(
                userGame(1001L, "Nathapon", 34, 1, 6),
                userGame(1002L, "Jackie", 1, 1, 5),
                userGame(1003L, "Jackie", 1, 4, 2)
        ));

        List<Map<String, Object>> characters = repository.findCharacterMeta(39, 3, 2, 5, 7);

        assertThat(characters).hasSize(1);
        assertThat(characters.get(0))
                .containsEntry("characterNum", 1)
                .containsEntry("characterName", "Jackie")
                .containsEntry("gameCount", 2);
    }

    @Test
    void findCharacterMetaExcludesSamplesOutsideRetentionWindow() {
        repository.saveSamples("user-1", List.of(
                userGame(1001L, "Jackie", 1, 1, 6),
                userGame(1002L, "Jackie", 1, 3, 4)
        ));
        // Age one of the two Jackie samples beyond the 7-day retention window.
        jdbcTemplate.update(
                "UPDATE character_meta_match_sample SET collected_at = ? WHERE game_id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 6, 1, 0, 0)),
                1002L
        );

        assertThat(repository.totalGames(39, 3, 7)).isEqualTo(1);
        List<Map<String, Object>> characters = repository.findCharacterMeta(39, 3, 1, 5, 7);
        assertThat(characters).hasSize(1);
        assertThat(characters.get(0))
                .containsEntry("characterNum", 1)
                .containsEntry("gameCount", 1);
    }

    @Test
    void purgeSamplesOlderThanRemovesExpiredSamplesOnly() {
        repository.saveSamples("user-1", List.of(
                userGame(1001L, "Jackie", 1, 1, 6),
                userGame(1002L, "Luke", 22, 2, 4)
        ));
        jdbcTemplate.update(
                "UPDATE character_meta_match_sample SET collected_at = ? WHERE game_id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 6, 1, 0, 0)),
                1002L
        );

        int purgedCount = repository.purgeSamplesOlderThan(7);

        assertThat(purgedCount).isEqualTo(1);
        assertThat(repository.totalGames(39, 3, 0)).isEqualTo(1);
    }

    @Test
    void savesAndLoadsNextRankIndex() {
        assertThat(repository.nextRankIndex("character-meta:39:3")).isZero();

        repository.saveNextRankIndex("character-meta:39:3", 27);

        assertThat(repository.nextRankIndex("character-meta:39:3")).isEqualTo(27);

        repository.saveNextRankIndex("character-meta:39:3", 41);

        assertThat(repository.nextRankIndex("character-meta:39:3")).isEqualTo(41);
    }

    private UserGameSummary userGame(long gameId, String characterName, int characterNum, int gameRank, int playerKill) {
        return new UserGameSummary(
                gameId,
                "testUser",
                39,
                3,
                3,
                characterNum,
                characterName,
                gameRank,
                playerKill,
                2,
                1,
                12345,
                7,
                1620,
                1574,
                46,
                1620,
                "2026-05-30T23:15:29.029+0900",
                551
        );
    }
}
