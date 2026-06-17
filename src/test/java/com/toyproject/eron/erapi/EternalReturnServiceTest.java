package com.toyproject.eron.erapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.toyproject.eron.erapi.dto.UserGameSummary;
import com.toyproject.eron.erapi.dto.UserGamesResponse;
import com.toyproject.eron.erapi.dto.UserSearchResponse;

class EternalReturnServiceTest {

    private final EternalReturnApiClient eternalReturnApiClient = Mockito.mock(EternalReturnApiClient.class);
    private final EternalReturnService eternalReturnService = new EternalReturnService(eternalReturnApiClient);

    @Test
    void getUserByNicknameDelegatesToApiClient() {
        UserSearchResponse expected = new UserSearchResponse(
                "abc-123",
                "testUser",
                Map.of("code", 200)
        );
        when(eternalReturnApiClient.getUserByNickname("testUser")).thenReturn(expected);

        UserSearchResponse response = eternalReturnService.getUserByNickname("testUser");

        assertThat(response).isSameAs(expected);
        verify(eternalReturnApiClient).getUserByNickname("testUser");
    }

    @Test
    void getUserGamesDelegatesToApiClient() {
        UserGamesResponse expected = new UserGamesResponse(
                List.of(new UserGameSummary(
                        98765,
                        "testUser",
                        39,
                        3,
                        3,
                        1,
                        "Jackie",
                        3,
                        5,
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
                )),
                98765
        );
        when(eternalReturnApiClient.getUserGames("abc-123")).thenReturn(expected);

        UserGamesResponse response = eternalReturnService.getUserGames("abc-123");

        assertThat(response).isSameAs(expected);
        verify(eternalReturnApiClient).getUserGames("abc-123");
    }
}
