package com.toyproject.eron.erapi.dto;

public record UserGameSummary(
        Integer gameId,
        String nickname,
        Integer seasonId,
        Integer matchingMode,
        Integer matchingTeamMode,
        Integer characterNum,
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
}
