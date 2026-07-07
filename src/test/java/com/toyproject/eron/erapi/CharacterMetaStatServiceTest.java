package com.toyproject.eron.erapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.toyproject.eron.erapi.dto.UserGameSummary;
import com.toyproject.eron.erapi.dto.UserGamesResponse;
import com.toyproject.eron.global.config.EternalReturnApiProperties;

class CharacterMetaStatServiceTest {

    private EternalReturnApiClient eternalReturnApiClient;
    private CharacterMetaStatRepository characterMetaStatRepository;
    private CharacterMetaStatService service;

    @BeforeEach
    void setUp() {
        eternalReturnApiClient = Mockito.mock(EternalReturnApiClient.class);
        characterMetaStatRepository = Mockito.mock(CharacterMetaStatRepository.class);

        EternalReturnApiProperties properties = new EternalReturnApiProperties();
        properties.setCurrentSeasonId(39);
        properties.setCurrentMatchingTeamMode(3);
        properties.setCurrentMetaRankingSampleLimit(1000);
        properties.setCurrentMetaRankingBatchSize(3);
        properties.setCurrentMetaMinimumCharacterGames(10);
        properties.setCurrentMetaSampleRetentionDays(7);

        service = new CharacterMetaStatService(eternalReturnApiClient, characterMetaStatRepository, properties);

        when(eternalReturnApiClient.getTopRankings(39, 3)).thenReturn(Map.of("topRanks", topRanks(10)));
        when(eternalReturnApiClient.getUserGames(anyString()))
                .thenReturn(new UserGamesResponse(List.of(rankedGame()), null));
        when(characterMetaStatRepository.saveSamples(anyString(), anyList())).thenReturn(1);
        when(characterMetaStatRepository.purgeSamplesOlderThan(anyInt())).thenReturn(0);
        when(characterMetaStatRepository.totalGames(anyInt(), anyInt(), anyInt())).thenReturn(3);
    }

    @Test
    void refreshVisitsOnlyOneBatchOfRankersInsteadOfEntirePool() {
        when(characterMetaStatRepository.nextRankIndex("character-meta:39:3")).thenReturn(0);

        Map<String, Object> result = service.refreshTodayCharacterMetaSamples();

        // Batch size 3 must cap ER API calls even though 1000 rankers are available.
        verify(eternalReturnApiClient, times(3)).getUserGames(anyString());
        verify(eternalReturnApiClient).getUserGames("100");
        verify(eternalReturnApiClient).getUserGames("101");
        verify(eternalReturnApiClient).getUserGames("102");
        verify(eternalReturnApiClient, never()).getUserGames("103");
        verify(characterMetaStatRepository).purgeSamplesOlderThan(7);
        assertThat(result).containsEntry("visitedRankerCount", 3);
        assertThat(result).containsEntry("rankingBatchSize", 3);
        assertThat(result).containsEntry("savedSampleCount", 3);
    }

    @Test
    void refreshResumesFromStoredRankIndexAndAdvancesByBatch() {
        when(characterMetaStatRepository.nextRankIndex("character-meta:39:3")).thenReturn(3);

        Map<String, Object> result = service.refreshTodayCharacterMetaSamples();

        verify(eternalReturnApiClient).getUserGames("103");
        verify(eternalReturnApiClient).getUserGames("104");
        verify(eternalReturnApiClient).getUserGames("105");
        verify(eternalReturnApiClient, never()).getUserGames("100");
        // Next run should continue right after the last visited ranker.
        verify(characterMetaStatRepository).saveNextRankIndex("character-meta:39:3", 6);
        assertThat(result).containsEntry("startRankIndex", 3);
        assertThat(result).containsEntry("nextRankIndex", 6);
    }

    private List<Map<String, Object>> topRanks(int count) {
        List<Map<String, Object>> ranks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ranks.add(Map.of(
                    "rank", i + 1,
                    "userNum", 100 + i,
                    "nickname", "ranker-" + i
            ));
        }
        return ranks;
    }

    private UserGameSummary rankedGame() {
        return new UserGameSummary(
                9_000L,
                "ranker",
                39,
                3,
                3,
                1,
                "Jackie",
                1,
                6,
                2,
                1,
                12345,
                7,
                1620,
                1574,
                46,
                1620,
                "2026-07-06T23:15:29.029+0900",
                551
        );
    }
}
