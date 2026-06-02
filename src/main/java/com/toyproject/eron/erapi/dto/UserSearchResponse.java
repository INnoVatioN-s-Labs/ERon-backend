package com.toyproject.eron.erapi.dto;

import java.util.Map;

public record UserSearchResponse(
        int userNum,
        String nickname,
        Map<String, Object> raw
) {
}
