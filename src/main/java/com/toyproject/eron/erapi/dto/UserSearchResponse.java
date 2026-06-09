package com.toyproject.eron.erapi.dto;

import java.util.Map;

public record UserSearchResponse(
        String userId,
        String nickname,
        Map<String, Object> raw
) {
}
