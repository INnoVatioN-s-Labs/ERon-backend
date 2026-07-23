package com.toyproject.eron.erapi;

import java.util.List;

public final class TraitStyleResolver {

    public String resolveFromSecondSubTraits(List<Integer> traitCodes) {
        if (traitCodes == null || traitCodes.isEmpty()) {
            return null;
        }

        return resolve(traitCodes.get(0));
    }

    public String resolve(Integer traitCode) {
        if (traitCode == null) {
            return null;
        }

        int traitGroup = traitCode / 100_000;
        return switch (traitGroup) {
            case 70 -> "파괴";
            case 71 -> "저항";
            case 72 -> "지원";
            case 73, 79 -> "혼돈";
            default -> null;
        };
    }
}
