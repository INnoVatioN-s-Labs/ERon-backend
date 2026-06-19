package com.toyproject.eron.erapi.dto;

import java.util.List;
import java.util.Map;

public record TopRankingsResponse(
        Integer seasonId,
        Integer matchingTeamMode,
        List<Map<String, Object>> topRanks,
        Map<String, Object> raw
) {
}
