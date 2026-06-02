package com.toyproject.eron.erapi;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/users/{userNum}/stats")
    public Map<String, Object> getUserStats(
            @PathVariable int userNum,
            @RequestParam int seasonId
    ) {
        return eternalReturnApiClient.getUserStats(userNum, seasonId);
    }

    @GetMapping("/users/{userNum}/games")
    public Map<String, Object> getUserGames(@PathVariable int userNum) {
        return eternalReturnApiClient.getUserGames(userNum);
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
