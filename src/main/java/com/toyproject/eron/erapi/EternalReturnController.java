package com.toyproject.eron.erapi;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toyproject.eron.erapi.dto.UserGamesResponse;
import com.toyproject.eron.erapi.dto.UserOverviewResponse;
import com.toyproject.eron.erapi.dto.UserSearchResponse;

@RestController
@RequestMapping("/api/er")
public class EternalReturnController {

    private final EternalReturnApiClient eternalReturnApiClient;

    public EternalReturnController(EternalReturnApiClient eternalReturnApiClient) {
        this.eternalReturnApiClient = eternalReturnApiClient;
    }

    @GetMapping("/users/search")
    public UserSearchResponse searchUser(@RequestParam String nickname) {
        return eternalReturnApiClient.getUserByNickname(nickname);
    }

    @GetMapping("/users/overview")
    public UserOverviewResponse getUserOverview(
            @RequestParam String nickname,
            @RequestParam int seasonId,
            @RequestParam int matchingTeamMode
    ) {
        return eternalReturnApiClient.getUserOverview(nickname, seasonId, matchingTeamMode);
    }

    @GetMapping("/users/{userId}/stats")
    public Map<String, Object> getUserStats(
            @PathVariable String userId,
            @RequestParam int seasonId
    ) {
        return eternalReturnApiClient.getUserStats(userId, seasonId);
    }

    @GetMapping("/users/{userId}/games")
    public UserGamesResponse getUserGames(@PathVariable String userId) {
        return eternalReturnApiClient.getUserGames(userId);
    }

    @GetMapping("/users/{userId}/rank")
    public Map<String, Object> getUserRank(
            @PathVariable String userId,
            @RequestParam int seasonId,
            @RequestParam int matchingTeamMode
    ) {
        return eternalReturnApiClient.getUserRank(userId, seasonId, matchingTeamMode);
    }

    @GetMapping("/games/{gameId}")
    public Map<String, Object> getGame(@PathVariable int gameId) {
        return eternalReturnApiClient.getGame(gameId);
    }

    @GetMapping("/data/{metaType}")
    public Map<String, Object> getDataTable(@PathVariable String metaType) {
        return eternalReturnApiClient.getDataTable(metaType);
    }
}
