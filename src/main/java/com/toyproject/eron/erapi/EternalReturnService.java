package com.toyproject.eron.erapi;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.toyproject.eron.erapi.dto.GameDetailResponse;
import com.toyproject.eron.erapi.dto.UserGamesResponse;
import com.toyproject.eron.erapi.dto.UserOverviewResponse;
import com.toyproject.eron.erapi.dto.UserSearchResponse;

@Service
public class EternalReturnService {

    private final EternalReturnApiClient eternalReturnApiClient;

    public EternalReturnService(EternalReturnApiClient eternalReturnApiClient) {
        this.eternalReturnApiClient = eternalReturnApiClient;
    }

    public UserSearchResponse getUserByNickname(String nickname) {
        return eternalReturnApiClient.getUserByNickname(nickname);
    }

    public UserOverviewResponse getUserOverview(String nickname, int seasonId, int matchingTeamMode) {
        return eternalReturnApiClient.getUserOverview(nickname, seasonId, matchingTeamMode);
    }

    public Map<String, Object> getUserStats(String userId, int seasonId) {
        return eternalReturnApiClient.getUserStats(userId, seasonId);
    }

    public UserGamesResponse getUserGames(String userId) {
        return eternalReturnApiClient.getUserGames(userId);
    }

    public Map<String, Object> getUserRank(String userId, int seasonId, int matchingTeamMode) {
        return eternalReturnApiClient.getUserRank(userId, seasonId, matchingTeamMode);
    }

    public GameDetailResponse getGame(int gameId) {
        return eternalReturnApiClient.getGame(gameId);
    }

    public Map<String, Object> getDataTable(String metaType) {
        return eternalReturnApiClient.getDataTable(metaType);
    }
}
