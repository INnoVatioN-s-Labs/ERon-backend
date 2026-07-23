package com.toyproject.eron.erapi.dto;

public record TraitSummary(
        Integer traitCode,
        String traitName,
        Integer traitIconCode
) {
    public TraitSummary(Integer traitCode, String traitName) {
        this(traitCode, traitName, traitCode);
    }
}
