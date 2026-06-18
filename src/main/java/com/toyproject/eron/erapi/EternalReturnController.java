package com.toyproject.eron.erapi;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toyproject.eron.erapi.dto.GameDetailResponse;
import com.toyproject.eron.erapi.dto.UserGamesResponse;
import com.toyproject.eron.erapi.dto.UserOverviewResponse;
import com.toyproject.eron.erapi.dto.UserSearchResponse;

@RestController
@RequestMapping("/api/er")
public class EternalReturnController {

    private final EternalReturnService eternalReturnService;

    public EternalReturnController(EternalReturnService eternalReturnService) {
        this.eternalReturnService = eternalReturnService;
    }

    @GetMapping("/users/search")
    public UserSearchResponse searchUser(@RequestParam String nickname) {
        return eternalReturnService.getUserByNickname(nickname);
    }

    @GetMapping("/users/overview")
    public UserOverviewResponse getUserOverview(
            @RequestParam String nickname,
            @RequestParam int seasonId,
            @RequestParam int matchingTeamMode
    ) {
        return eternalReturnService.getUserOverview(nickname, seasonId, matchingTeamMode);
    }

    @GetMapping("/users/{userId}/stats")
    public Map<String, Object> getUserStats(
            @PathVariable String userId,
            @RequestParam int seasonId
    ) {
        return eternalReturnService.getUserStats(userId, seasonId);
    }

    @GetMapping("/users/{userId}/games")
    public UserGamesResponse getUserGames(
            @PathVariable String userId,
            @RequestParam(defaultValue = "false") boolean includeDetails,
            @RequestParam(defaultValue = "3") int detailLimit
    ) {
        return eternalReturnService.getUserGames(userId, includeDetails, detailLimit);
    }

    @GetMapping("/users/{userId}/rank")
    public Map<String, Object> getUserRank(
            @PathVariable String userId,
            @RequestParam int seasonId,
            @RequestParam int matchingTeamMode
    ) {
        return eternalReturnService.getUserRank(userId, seasonId, matchingTeamMode);
    }

    @GetMapping("/rankings/top")
    public Map<String, Object> getTopRankings(
            @RequestParam int seasonId,
            @RequestParam int matchingTeamMode
    ) {
        return eternalReturnService.getTopRankings(seasonId, matchingTeamMode);
    }

    @GetMapping("/meta/characters")
    public Map<String, Object> getCharacterMeta(
            @RequestParam int seasonId,
            @RequestParam int matchingTeamMode,
            @RequestParam(defaultValue = "이터니티") String tier
    ) {
        return eternalReturnService.getCharacterMeta(seasonId, matchingTeamMode, tier);
    }

    @GetMapping("/games/{gameId}")
    public GameDetailResponse getGame(@PathVariable int gameId) {
        return eternalReturnService.getGame(gameId);
    }

    @GetMapping("/data/{metaType}")
    public Map<String, Object> getDataTable(@PathVariable String metaType) {
        return eternalReturnService.getDataTable(metaType);
    }
}
