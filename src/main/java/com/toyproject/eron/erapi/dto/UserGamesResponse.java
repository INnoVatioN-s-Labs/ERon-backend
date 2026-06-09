package com.toyproject.eron.erapi.dto;

import java.util.List;

public record UserGamesResponse(
        List<UserGameSummary> games,
        Integer next
) {
}
