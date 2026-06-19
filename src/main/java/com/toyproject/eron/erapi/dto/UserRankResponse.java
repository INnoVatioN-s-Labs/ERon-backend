package com.toyproject.eron.erapi.dto;

import java.util.Map;

public record UserRankResponse(
        String userId,
        Integer seasonId,
        Integer matchingTeamMode,
        Map<String, Object> userRank,
        Map<String, Object> raw
) {
}
