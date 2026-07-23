package com.toyproject.eron.erapi.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserGameSummary(
        Long gameId,
        String nickname,
        Integer seasonId,
        Integer matchingMode,
        Integer matchingTeamMode,
        Integer characterNum,
        String characterName,
        Integer gameRank,
        Integer playerKill,
        Integer playerAssistant,
        Integer playerDeaths,
        Integer damageToPlayer,
        Integer teamKill,
        Integer rankPoint,
        Integer mmrBefore,
        Integer mmrGain,
        Integer mmrAfter,
        String startDtm,
        Integer playTime,
        Integer bestWeapon,
        String bestWeaponName,
        Integer bestWeaponLevel,
        Integer tacticalSkillGroupCode,
        String tacticalSkill,
        String subTraitStyle,
        List<TraitSummary> traits,
        Integer skinCode,
        Integer routeId
) {

    public UserGameSummary(
            Long gameId,
            String nickname,
            Integer seasonId,
            Integer matchingMode,
            Integer matchingTeamMode,
            Integer characterNum,
            String characterName,
            Integer gameRank,
            Integer playerKill,
            Integer playerAssistant,
            Integer playerDeaths,
            Integer damageToPlayer,
            Integer teamKill,
            Integer rankPoint,
            Integer mmrBefore,
            Integer mmrGain,
            Integer mmrAfter,
            String startDtm,
            Integer playTime,
            Integer bestWeapon,
            String bestWeaponName,
            Integer bestWeaponLevel,
            Integer tacticalSkillGroupCode,
            String tacticalSkill,
            List<TraitSummary> traits,
            Integer skinCode
    ) {
        this(
                gameId,
                nickname,
                seasonId,
                matchingMode,
                matchingTeamMode,
                characterNum,
                characterName,
                gameRank,
                playerKill,
                playerAssistant,
                playerDeaths,
                damageToPlayer,
                teamKill,
                rankPoint,
                mmrBefore,
                mmrGain,
                mmrAfter,
                startDtm,
                playTime,
                bestWeapon,
                bestWeaponName,
                bestWeaponLevel,
                tacticalSkillGroupCode,
                tacticalSkill,
                null,
                traits,
                skinCode,
                null
        );
    }

    public UserGameSummary(
            Long gameId,
            String nickname,
            Integer seasonId,
            Integer matchingMode,
            Integer matchingTeamMode,
            Integer characterNum,
            String characterName,
            Integer gameRank,
            Integer playerKill,
            Integer playerAssistant,
            Integer playerDeaths,
            Integer damageToPlayer,
            Integer teamKill,
            Integer rankPoint,
            Integer mmrBefore,
            Integer mmrGain,
            Integer mmrAfter,
            String startDtm,
            Integer playTime
    ) {
        this(
                gameId,
                nickname,
                seasonId,
                matchingMode,
                matchingTeamMode,
                characterNum,
                characterName,
                gameRank,
                playerKill,
                playerAssistant,
                playerDeaths,
                damageToPlayer,
                teamKill,
                rankPoint,
                mmrBefore,
                mmrGain,
                mmrAfter,
                startDtm,
                playTime,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
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

    public UserGameSummary withRouteId(Integer routeId) {
        return new UserGameSummary(
                gameId,
                nickname,
                seasonId,
                matchingMode,
                matchingTeamMode,
                characterNum,
                characterName,
                gameRank,
                playerKill,
                playerAssistant,
                playerDeaths,
                damageToPlayer,
                teamKill,
                rankPoint,
                mmrBefore,
                mmrGain,
                mmrAfter,
                startDtm,
                playTime,
                bestWeapon,
                bestWeaponName,
                bestWeaponLevel,
                tacticalSkillGroupCode,
                tacticalSkill,
                subTraitStyle,
                traits,
                skinCode,
                routeId
        );
    }

    @JsonProperty("modeKey")
    public String modeKey() {
        if (isCobalt()) {
            return "cobalt";
        }
        if (isRanked()) {
            return "ranked";
        }
        if (isLoneWolf()) {
            return "loneWolf";
        }
        if (isNormal()) {
            return "normal";
        }

        return "mode-" + valueOrUnknown(matchingMode) + "-team-" + valueOrUnknown(matchingTeamMode);
    }

    @JsonProperty("modeName")
    public String modeName() {
        return switch (modeKey()) {
            case "ranked" -> "랭크";
            case "normal" -> "일반";
            case "cobalt" -> "코발트";
            case "loneWolf" -> "론 울프";
            default -> "기타";
        };
    }

    private boolean isRanked() {
        return seasonId != null && seasonId > 0;
    }

    private boolean isNormal() {
        return Integer.valueOf(2).equals(matchingMode);
    }

    private boolean isCobalt() {
        return Integer.valueOf(6).equals(matchingMode);
    }

    private boolean isLoneWolf() {
        return Integer.valueOf(1).equals(matchingTeamMode);
    }

    private String valueOrUnknown(Integer value) {
        return value == null ? "unknown" : String.valueOf(value);
    }
}
