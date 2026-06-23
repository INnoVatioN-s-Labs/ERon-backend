package com.toyproject.eron.erapi.dto;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GameParticipantSummary(
        String nickname,
        Integer teamNumber,
        Integer gameRank,
        Integer characterNum,
        String characterName,
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
        Map<String, EquipmentSummary> equipment,
        Integer tacticalSkillGroupCode,
        String tacticalSkill,
        List<TraitSummary> traits
) {

    public GameParticipantSummary(
            String nickname,
            Integer teamNumber,
            Integer gameRank,
            Integer characterNum,
            String characterName,
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
            Map<String, EquipmentSummary> equipment
    ) {
        this(
                nickname,
                teamNumber,
                gameRank,
                characterNum,
                characterName,
                characterLevel,
                playerKill,
                playerAssistant,
                playerDeaths,
                monsterKill,
                teamKill,
                damageToPlayer,
                damageFromPlayer,
                damageToMonster,
                healAmount,
                protectAbsorb,
                bestWeapon,
                bestWeaponLevel,
                rankPoint,
                victory,
                playTime,
                equipment,
                null,
                null,
                List.of()
        );
    }

    @JsonProperty("equipmentList")
    public List<EquipmentSummary> equipmentList() {
        return equipment.entrySet()
                .stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .toList();
    }
}
