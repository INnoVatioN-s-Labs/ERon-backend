package com.toyproject.eron.erapi;

import java.util.Map;

public final class TraitIconCodeResolver {

    private static final Map<Integer, Integer> KNOWN_ICON_CODES_BY_TRAIT_CODE = Map.ofEntries(
            Map.entry(7000201, 7000201),
            Map.entry(7000401, 7000401),
            Map.entry(7000501, 7000501),
            Map.entry(7000601, 7000601),
            Map.entry(7000701, 7000701),
            Map.entry(7100101, 7100101),
            Map.entry(7100201, 7100201),
            Map.entry(7100401, 7100401),
            Map.entry(7100501, 7100501),
            Map.entry(7200101, 7200101),
            Map.entry(7200201, 7200201),
            Map.entry(7200301, 7200301),
            Map.entry(7200501, 7200501),
            Map.entry(7300101, 7300101),
            Map.entry(7300201, 7300201),
            Map.entry(7300301, 7300301)
    );

    public Integer resolve(Integer traitCode) {
        if (traitCode == null) {
            return null;
        }

        return KNOWN_ICON_CODES_BY_TRAIT_CODE.getOrDefault(traitCode, traitCode);
    }
}
