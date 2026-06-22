package com.toyproject.eron.erapi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.toyproject.eron.erapi.dto.DataTableResponse;
import com.toyproject.eron.erapi.dto.GameDetailResponse;
import com.toyproject.eron.erapi.dto.TopRankingsResponse;
import com.toyproject.eron.erapi.dto.UserGamesResponse;
import com.toyproject.eron.erapi.dto.UserOverviewResponse;
import com.toyproject.eron.erapi.dto.UserRankResponse;
import com.toyproject.eron.erapi.dto.UserSearchResponse;
import com.toyproject.eron.erapi.dto.UserStatsResponse;

@RestController
@RequestMapping("/api/er")
@Validated
public class EternalReturnController {

    private final EternalReturnService eternalReturnService;

    public EternalReturnController(EternalReturnService eternalReturnService) {
        this.eternalReturnService = eternalReturnService;
    }

    @GetMapping("/users/search")
    public UserSearchResponse searchUser(@RequestParam @NotBlank String nickname) {
        return eternalReturnService.getUserByNickname(nickname);
    }

    @GetMapping("/users/overview")
    public UserOverviewResponse getUserOverview(
            @RequestParam @NotBlank String nickname,
            @RequestParam @PositiveOrZero int seasonId,
            @RequestParam @Positive int matchingTeamMode
    ) {
        return eternalReturnService.getUserOverview(nickname, seasonId, matchingTeamMode);
    }

    @GetMapping("/users/{userId}/stats")
    public UserStatsResponse getUserStats(
            @PathVariable @NotBlank String userId,
            @RequestParam @PositiveOrZero int seasonId
    ) {
        return eternalReturnService.getUserStats(userId, seasonId);
    }

    @GetMapping("/users/{userId}/games")
    public UserGamesResponse getUserGames(
            @PathVariable @NotBlank String userId,
            @RequestParam(defaultValue = "false") boolean includeDetails,
            @RequestParam(defaultValue = "3") @PositiveOrZero int detailLimit,
            @RequestParam(required = false) @Positive Long next
    ) {
        if (next == null) {
            return eternalReturnService.getUserGames(userId, includeDetails, detailLimit);
        }

        UserGamesResponse response = eternalReturnService.getUserGames(userId, next);
        if (!includeDetails) {
            return response;
        }

        return eternalReturnService.withGameDetails(response, detailLimit);
    }

    @GetMapping("/users/{userId}/rank")
    public UserRankResponse getUserRank(
            @PathVariable @NotBlank String userId,
            @RequestParam @PositiveOrZero int seasonId,
            @RequestParam @Positive int matchingTeamMode
    ) {
        return eternalReturnService.getUserRank(userId, seasonId, matchingTeamMode);
    }

    @GetMapping("/rankings/top")
    public TopRankingsResponse getTopRankings(
            @RequestParam @PositiveOrZero int seasonId,
            @RequestParam @Positive int matchingTeamMode
    ) {
        return eternalReturnService.getTopRankings(seasonId, matchingTeamMode);
    }

    @GetMapping("/meta/characters")
    public java.util.Map<String, Object> getCharacterMeta(
            @RequestParam @PositiveOrZero int seasonId,
            @RequestParam @Positive int matchingTeamMode,
            @RequestParam(defaultValue = "이터니티") String tier
    ) {
        return eternalReturnService.getCharacterMeta(seasonId, matchingTeamMode, tier);
    }

    @GetMapping("/games/{gameId}")
    public GameDetailResponse getGame(@PathVariable @Positive long gameId) {
        return eternalReturnService.getGame(gameId);
    }

    @GetMapping("/data/{metaType}")
    public DataTableResponse getDataTable(@PathVariable @NotBlank String metaType) {
        return eternalReturnService.getDataTable(metaType);
    }
}
