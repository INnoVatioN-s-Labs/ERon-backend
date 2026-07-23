package com.toyproject.eron.erapi;

import java.util.Map;

public final class TraitNameResolver {

    private static final Map<Integer, String> KNOWN_KOREAN_NAMES_BY_CODE = Map.ofEntries(
            Map.entry(7000201, "취약"),
            Map.entry(7000401, "흡혈마"),
            Map.entry(7000501, "벽력"),
            Map.entry(7000601, "아드레날린"),
            Map.entry(7000701, "엑셀러레이터"),
            Map.entry(7100101, "금강"),
            Map.entry(7100201, "불괴"),
            Map.entry(7100401, "빛의 수호"),
            Map.entry(7100501, "응징"),
            Map.entry(7200101, "초재생"),
            Map.entry(7200201, "증폭 드론"),
            Map.entry(7200301, "치유 드론"),
            Map.entry(7200501, "헌신"),
            Map.entry(7300101, "스텔라 차지"),
            Map.entry(7300201, "도깨비불"),
            Map.entry(7300301, "와류")
    );

    public String resolve(Integer traitCode, String resolvedName) {
        if (traitCode == null) {
            return resolvedName;
        }

        if (isResolvedName(resolvedName)) {
            return resolvedName;
        }

        String knownKoreanName = KNOWN_KOREAN_NAMES_BY_CODE.get(traitCode);
        if (knownKoreanName != null) {
            return knownKoreanName;
        }

        return "특성 " + traitCode;
    }

    private boolean isResolvedName(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        return !value.matches("특성 \\d+");
    }
}
