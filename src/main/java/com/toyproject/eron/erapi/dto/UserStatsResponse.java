package com.toyproject.eron.erapi.dto;

import java.util.List;
import java.util.Map;

public record UserStatsResponse(
        String userId,
        Integer seasonId,
        List<Map<String, Object>> userStats,
        Map<String, Object> raw
) {
}
