package com.toyproject.eron.erapi.dto;

public record SkinMetadata(
        Integer skinCode,
        Integer characterNum,
        String characterName,
        String skinName,
        Integer skinVariant
) {
}
