package com.toyproject.eron.erapi;

import java.util.Map;

public final class TacticalSkillNameResolver {

    private static final Map<Integer, String> KNOWN_KOREAN_NAMES_BY_CODE = Map.ofEntries(
            Map.entry(30, "블링크"),
            Map.entry(40, "퀘이크"),
            Map.entry(50, "프로토콜 위반"),
            Map.entry(60, "붉은 폭풍"),
            Map.entry(70, "초월"),
            Map.entry(80, "아티팩트"),
            Map.entry(90, "무효화"),
            Map.entry(110, "강한 결속"),
            Map.entry(120, "스트라이더 - A13"),
            Map.entry(130, "진실의 칼날"),
            Map.entry(140, "블링크"),
            Map.entry(150, "치유의 바람"),
            Map.entry(160, "리펄서 미사일"),
            Map.entry(170, "플라즈마 대시"),
            Map.entry(190, "라이트 윙"),
            Map.entry(50030, "롤링썬더"),
            Map.entry(500030, "롤링썬더"),
            Map.entry(500120, "블링크"),
            Map.entry(500130, "퀘이크"),
            Map.entry(500150, "붉은 폭풍"),
            Map.entry(500160, "초월"),
            Map.entry(500180, "무효화"),
            Map.entry(500250, "리펄서 미사일"),
            Map.entry(500260, "플라즈마 대시"),
            Map.entry(500270, "쇠약")
    );

    public String resolve(Integer tacticalSkillGroupCode, String resolvedName) {
        if (tacticalSkillGroupCode == null) {
            return resolvedName;
        }

        if (isResolvedName(resolvedName)) {
            return resolvedName;
        }

        String knownKoreanName = KNOWN_KOREAN_NAMES_BY_CODE.get(tacticalSkillGroupCode);
        if (knownKoreanName != null) {
            return knownKoreanName;
        }

        return "전술 스킬 " + tacticalSkillGroupCode;
    }

    private boolean isResolvedName(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        return !value.matches("전술 스킬 \\d+");
    }
}
