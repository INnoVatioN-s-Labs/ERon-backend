package com.toyproject.eron.erapi.dto;

import java.util.Map;

public record UserOverviewResponse(
        UserSearchResponse user,
        Map<String, Object> rank,
        UserGamesResponse games
) {
}
