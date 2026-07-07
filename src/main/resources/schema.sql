CREATE TABLE IF NOT EXISTS character_meta_match_sample (
    game_id BIGINT NOT NULL,
    source_user_id VARCHAR(64) NOT NULL,
    nickname VARCHAR(64),
    season_id INTEGER,
    matching_team_mode INTEGER,
    character_num INTEGER,
    character_name VARCHAR(64),
    game_rank INTEGER,
    player_kill INTEGER,
    player_assistant INTEGER,
    player_deaths INTEGER,
    damage_to_player INTEGER,
    team_kill INTEGER,
    rank_point INTEGER,
    mmr_gain INTEGER,
    start_dtm VARCHAR(40),
    collected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (game_id, source_user_id)
);

CREATE INDEX IF NOT EXISTS idx_character_meta_sample_scope
    ON character_meta_match_sample (season_id, matching_team_mode, character_num);

CREATE TABLE IF NOT EXISTS character_meta_collection_state (
    state_key VARCHAR(80) PRIMARY KEY,
    next_rank_index INTEGER NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
