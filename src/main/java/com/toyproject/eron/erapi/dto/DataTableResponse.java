package com.toyproject.eron.erapi.dto;

import java.util.Map;

public record DataTableResponse(
        String metaType,
        Map<String, Object> data
) {
}
