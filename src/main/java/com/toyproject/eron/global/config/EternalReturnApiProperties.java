package com.toyproject.eron.global.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eternal-return.api")
public class EternalReturnApiProperties {

    private String baseUrl = "https://open-api.bser.io/v1";
    private String key;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(10);
    private Duration userGamesCacheTtl = Duration.ofSeconds(30);
    private long cacheMaximumSize = 10_000;
    private String[] corsAllowedOrigins = {"http://localhost:5173"};
    private int currentSeasonId = 39;
    private int currentMatchingTeamMode = 3;
    private String currentMetaTier = "";
    private int currentMetaRankingSampleLimit = 1000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getUserGamesCacheTtl() {
        return userGamesCacheTtl;
    }

    public void setUserGamesCacheTtl(Duration userGamesCacheTtl) {
        this.userGamesCacheTtl = userGamesCacheTtl;
    }

    public long getCacheMaximumSize() {
        return cacheMaximumSize;
    }

    public void setCacheMaximumSize(long cacheMaximumSize) {
        this.cacheMaximumSize = cacheMaximumSize;
    }

    public String[] getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(String[] corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public int getCurrentSeasonId() {
        return currentSeasonId;
    }

    public void setCurrentSeasonId(int currentSeasonId) {
        this.currentSeasonId = currentSeasonId;
    }

    public int getCurrentMatchingTeamMode() {
        return currentMatchingTeamMode;
    }

    public void setCurrentMatchingTeamMode(int currentMatchingTeamMode) {
        this.currentMatchingTeamMode = currentMatchingTeamMode;
    }

    public String getCurrentMetaTier() {
        return currentMetaTier;
    }

    public void setCurrentMetaTier(String currentMetaTier) {
        this.currentMetaTier = currentMetaTier;
    }

    public int getCurrentMetaRankingSampleLimit() {
        return currentMetaRankingSampleLimit;
    }

    public void setCurrentMetaRankingSampleLimit(int currentMetaRankingSampleLimit) {
        this.currentMetaRankingSampleLimit = currentMetaRankingSampleLimit;
    }
}
