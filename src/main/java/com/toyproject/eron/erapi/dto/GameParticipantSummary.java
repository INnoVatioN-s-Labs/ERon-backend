package com.toyproject.eron.erapi.dto;

import java.util.Map;

public record GameParticipantSummary(
        String nickname,
        Integer teamNumber,
        Integer gameRank,
        Integer characterNum,
        Integer characterLevel,
        Integer playerKill,
        Integer playerAssistant,
        Integer playerDeaths,
        Integer monsterKill,
        Integer teamKill,
        Integer damageToPlayer,
        Integer damageFromPlayer,
        Integer damageToMonster,
        Integer healAmount,
        Integer protectAbsorb,
        Integer bestWeapon,
        Integer bestWeaponLevel,
        Integer rankPoint,
        Integer victory,
        Integer playTime,
        Map<String, Object> equipment,
        Map<String, Object> equipmentGrade
) {
}
