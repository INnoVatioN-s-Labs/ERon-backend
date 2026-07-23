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
        String subTraitStyle,
        List<TraitSummary> traits,
        Integer routeId
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
            Map<String, EquipmentSummary> equipment,
            Integer tacticalSkillGroupCode,
            String tacticalSkill,
            List<TraitSummary> traits
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
                tacticalSkillGroupCode,
                tacticalSkill,
                null,
                traits,
                null
        );
    }

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
            Map<String, EquipmentSummary> equipment,
            Integer tacticalSkillGroupCode,
            String tacticalSkill,
            String subTraitStyle,
            List<TraitSummary> traits
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
                tacticalSkillGroupCode,
                tacticalSkill,
                subTraitStyle,
                traits,
                null
        );
    }

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
                null,
                List.of(),
                null
        );
    }

    @JsonProperty("mainTrait")
    public TraitSummary mainTrait() {
        if (traits == null || traits.isEmpty()) {
            return null;
        }

        return traits.get(0);
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
