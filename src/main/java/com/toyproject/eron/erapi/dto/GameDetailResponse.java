package com.toyproject.eron.erapi.dto;

import java.util.List;
import java.util.Map;

public record GameDetailResponse(
        Long gameId,
        Integer seasonId,
        Integer matchingMode,
        Integer matchingTeamMode,
        String startDtm,
        Integer duration,
        Integer playTime,
        Integer matchSize,
        int participantCount,
        List<GameParticipantSummary> participants
) {
}
