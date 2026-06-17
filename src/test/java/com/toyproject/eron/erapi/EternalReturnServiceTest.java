package com.toyproject.eron.erapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.toyproject.eron.erapi.dto.UserGameSummary;
import com.toyproject.eron.erapi.dto.UserGamesResponse;
import com.toyproject.eron.erapi.dto.UserSearchResponse;

class EternalReturnServiceTest {

    private final EternalReturnApiClient eternalReturnApiClient = Mockito.mock(EternalReturnApiClient.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-06-17T00:00:00Z"));
    private final EternalReturnService eternalReturnService =
            new EternalReturnService(eternalReturnApiClient, Duration.ofSeconds(30), clock);

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
    void getUserGamesCachesResponseWithinTtl() {
        UserGamesResponse expected = userGamesResponse(98765);
        when(eternalReturnApiClient.getUserGames("abc-123")).thenReturn(expected);

        UserGamesResponse firstResponse = eternalReturnService.getUserGames("abc-123");
        UserGamesResponse secondResponse = eternalReturnService.getUserGames("abc-123");

        assertThat(firstResponse).isSameAs(expected);
        assertThat(secondResponse).isSameAs(expected);
        verify(eternalReturnApiClient, times(1)).getUserGames("abc-123");
    }

    @Test
    void getUserGamesReloadsResponseAfterCacheExpires() {
        UserGamesResponse first = userGamesResponse(98765);
        UserGamesResponse second = userGamesResponse(98766);
        when(eternalReturnApiClient.getUserGames("abc-123")).thenReturn(first, second);

        UserGamesResponse firstResponse = eternalReturnService.getUserGames("abc-123");
        clock.advance(Duration.ofSeconds(31));
        UserGamesResponse secondResponse = eternalReturnService.getUserGames("abc-123");

        assertThat(firstResponse).isSameAs(first);
        assertThat(secondResponse).isSameAs(second);
        verify(eternalReturnApiClient, times(2)).getUserGames("abc-123");
    }

    @Test
    void getUserGamesBypassesCacheWhenTtlIsZero() {
        EternalReturnService serviceWithoutCache =
                new EternalReturnService(eternalReturnApiClient, Duration.ZERO, clock);
        UserGamesResponse first = userGamesResponse(98765);
        UserGamesResponse second = userGamesResponse(98766);
        when(eternalReturnApiClient.getUserGames("abc-123")).thenReturn(first, second);

        UserGamesResponse firstResponse = serviceWithoutCache.getUserGames("abc-123");
        UserGamesResponse secondResponse = serviceWithoutCache.getUserGames("abc-123");

        assertThat(firstResponse).isSameAs(first);
        assertThat(secondResponse).isSameAs(second);
        verify(eternalReturnApiClient, times(2)).getUserGames("abc-123");
    }

    private UserGamesResponse userGamesResponse(int gameId) {
        return new UserGamesResponse(
                List.of(new UserGameSummary(
                        gameId,
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
                gameId
        );
    }

    private static class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
