-- V1.2 core model: normalized, typed IPL data built from staging by the transform step
-- (see docs/adr/0010-core-model-and-transformation.md and docs/data-source.md section 10).
-- Every table is populated set-based by CoreTransformService; nothing here is written by Hibernate.

-- Seasons: canonical year from the match date; Cricsheet's label kept verbatim.
CREATE TABLE core.season (
    id            SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    year          SMALLINT NOT NULL UNIQUE CHECK (year BETWEEN 2008 AND 2100),
    source_season TEXT     NOT NULL UNIQUE, -- e.g. 2007/08 for IPL 2008
    name          TEXT GENERATED ALWAYS AS ('IPL ' || year::text) STORED
);

-- Franchise identity (Delhi Daredevils and Delhi Capitals are one team).
CREATE TABLE core.team (
    id        SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code      TEXT    NOT NULL UNIQUE CHECK (code ~ '^[A-Z]{2,5}$'),
    name      TEXT    NOT NULL UNIQUE, -- current (or final) franchise name
    is_active BOOLEAN NOT NULL
);

-- Every name a franchise has played under, exactly as spelled by the source.
CREATE TABLE core.team_name_history (
    id           SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    team_id      SMALLINT NOT NULL REFERENCES core.team (id),
    name         TEXT     NOT NULL UNIQUE,
    first_season SMALLINT NOT NULL,
    last_season  SMALLINT, -- NULL: still in use
    CHECK (last_season IS NULL OR last_season >= first_season)
);
CREATE INDEX ix_team_name_history_team ON core.team_name_history (team_id);

CREATE TABLE core.venue (
    id         INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       TEXT    NOT NULL,
    city       TEXT,
    is_curated BOOLEAN NOT NULL, -- false: created from a source name missing from reference data (reported)
    CONSTRAINT uq_venue_name_city UNIQUE NULLS NOT DISTINCT (name, city)
);

CREATE TABLE core.venue_alias (
    id                 INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venue_id           INTEGER NOT NULL REFERENCES core.venue (id),
    alias              TEXT    NOT NULL UNIQUE, -- exact source spelling
    is_curated         BOOLEAN NOT NULL,
    cricsheet_venue_id TEXT                     -- Cricsheet registry id seen for this name, if any
);
CREATE INDEX ix_venue_alias_venue ON core.venue_alias (venue_id);

-- Player identity is the Cricsheet registry id; names are attributes (see player_alias).
CREATE TABLE core.player (
    id           INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cricsheet_id TEXT UNIQUE, -- NULL only for a name the source gives no id for
    display_name TEXT NOT NULL -- name used in the player's most recent match
);
CREATE UNIQUE INDEX ux_player_name_without_id ON core.player (display_name) WHERE cricsheet_id IS NULL;
CREATE INDEX ix_player_display_name ON core.player (display_name);

CREATE TABLE core.player_alias (
    id           INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    player_id    INTEGER  NOT NULL REFERENCES core.player (id) ON DELETE CASCADE,
    name         TEXT     NOT NULL,
    first_season SMALLINT NOT NULL,
    last_season  SMALLINT NOT NULL,
    UNIQUE (player_id, name),
    CHECK (last_season >= first_season)
);
CREATE INDEX ix_player_alias_name ON core.player_alias (name);

CREATE TABLE core.match (
    id                        INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_match_id           BIGINT   NOT NULL UNIQUE, -- Cricsheet match id
    season_id                 SMALLINT NOT NULL REFERENCES core.season (id),
    match_date                DATE     NOT NULL,
    end_date                  DATE     NOT NULL,
    match_number              SMALLINT,
    stage                     TEXT     NOT NULL, -- League, Qualifier 1, Eliminator, Final, ...
    venue_id                  INTEGER  NOT NULL REFERENCES core.venue (id),
    venue_name                TEXT     NOT NULL, -- as recorded by the source
    city                      TEXT,              -- as recorded by the source
    team1_id                  SMALLINT NOT NULL REFERENCES core.team (id),
    team1_name                TEXT     NOT NULL, -- historical name, e.g. Delhi Daredevils
    team2_id                  SMALLINT NOT NULL REFERENCES core.team (id),
    team2_name                TEXT     NOT NULL,
    toss_winner_team_id       SMALLINT REFERENCES core.team (id),
    toss_decision             TEXT CHECK (toss_decision IN ('bat', 'field')),
    result                    TEXT     NOT NULL CHECK (result IN ('runs', 'wickets', 'tie', 'no result')),
    result_margin             SMALLINT CHECK (result_margin > 0),
    winner_team_id            SMALLINT REFERENCES core.team (id),
    super_over_winner_team_id SMALLINT REFERENCES core.team (id),
    method                    TEXT,              -- e.g. D/L
    has_super_over            BOOLEAN  NOT NULL,
    player_of_match_id        INTEGER REFERENCES core.player (id),
    target_runs               SMALLINT CHECK (target_runs > 0),
    target_overs              NUMERIC(4, 1) CHECK (target_overs > 0),
    scheduled_overs           SMALLINT CHECK (scheduled_overs > 0),
    umpires                   TEXT[],
    tv_umpires                TEXT[],
    match_referees            TEXT[],
    reserve_umpires           TEXT[],
    source_revision           INTEGER,
    source_data_version       TEXT,
    source_created            DATE,
    source_ingestion_run_id   BIGINT REFERENCES core.ingestion_run (id) ON DELETE SET NULL,
    CHECK (end_date >= match_date),
    CHECK (team1_id <> team2_id),
    CHECK (toss_winner_team_id IS NULL OR toss_winner_team_id IN (team1_id, team2_id)),
    CHECK (winner_team_id IS NULL OR winner_team_id IN (team1_id, team2_id)),
    CHECK (super_over_winner_team_id IS NULL OR super_over_winner_team_id IN (team1_id, team2_id)),
    CHECK ((result = 'no result') = (winner_team_id IS NULL)),
    CHECK ((result IN ('runs', 'wickets')) = (result_margin IS NOT NULL)),
    CHECK (has_super_over OR super_over_winner_team_id IS NULL)
);
CREATE INDEX ix_match_season ON core.match (season_id);
CREATE INDEX ix_match_venue ON core.match (venue_id);
CREATE INDEX ix_match_team1 ON core.match (team1_id);
CREATE INDEX ix_match_team2 ON core.match (team2_id);

CREATE TABLE core.innings (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    match_id        INTEGER  NOT NULL REFERENCES core.match (id) ON DELETE CASCADE,
    innings_number  SMALLINT NOT NULL CHECK (innings_number >= 1),
    batting_team_id SMALLINT NOT NULL REFERENCES core.team (id),
    bowling_team_id SMALLINT NOT NULL REFERENCES core.team (id),
    is_super_over   BOOLEAN  NOT NULL,
    UNIQUE (match_id, innings_number),
    CHECK (batting_team_id <> bowling_team_id),
    CHECK (is_super_over = (innings_number > 2)) -- T20: innings 1-2 regular, 3+ super overs
);

CREATE TABLE core.delivery (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    innings_id      INTEGER  NOT NULL REFERENCES core.innings (id) ON DELETE CASCADE,
    over_number     SMALLINT NOT NULL CHECK (over_number >= 0), -- 0-indexed, as in the source
    ball_in_over    SMALLINT NOT NULL CHECK (ball_in_over >= 1), -- sequence in the over, incl. wides/no-balls
    actual_delivery TEXT,                                        -- source label over.ball, legal balls only
    is_super_over   BOOLEAN  NOT NULL,                           -- copy of innings.is_super_over for filtering
    batter_id       INTEGER  NOT NULL REFERENCES core.player (id),
    non_striker_id  INTEGER  NOT NULL REFERENCES core.player (id),
    bowler_id       INTEGER  NOT NULL REFERENCES core.player (id),
    batter_runs     SMALLINT NOT NULL CHECK (batter_runs >= 0),
    extra_runs      SMALLINT NOT NULL CHECK (extra_runs >= 0),
    total_runs      SMALLINT NOT NULL CHECK (total_runs >= 0),
    wides           SMALLINT NOT NULL CHECK (wides >= 0),
    noballs         SMALLINT NOT NULL CHECK (noballs >= 0),
    byes            SMALLINT NOT NULL CHECK (byes >= 0),
    legbyes         SMALLINT NOT NULL CHECK (legbyes >= 0),
    penalty         SMALLINT NOT NULL CHECK (penalty >= 0),
    is_legal_ball   BOOLEAN GENERATED ALWAYS AS (wides = 0 AND noballs = 0) STORED,
    non_boundary    BOOLEAN  NOT NULL,
    is_wicket       BOOLEAN  NOT NULL, -- true when the ball has at least one core.wicket row
    UNIQUE (innings_id, over_number, ball_in_over),
    CHECK (total_runs = batter_runs + extra_runs),
    CHECK (extra_runs = wides + noballs + byes + legbyes + penalty)
);
CREATE INDEX ix_delivery_batter ON core.delivery (batter_id);
CREATE INDEX ix_delivery_bowler ON core.delivery (bowler_id);
CREATE INDEX ix_delivery_non_striker ON core.delivery (non_striker_id);

-- Dismissals: a ball can carry several wickets and a wicket several fielders (ADR 0010).
CREATE TABLE core.wicket (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    delivery_id   BIGINT   NOT NULL REFERENCES core.delivery (id) ON DELETE CASCADE,
    wicket_number SMALLINT NOT NULL CHECK (wicket_number >= 1), -- order on the ball
    player_out_id INTEGER  NOT NULL REFERENCES core.player (id),
    kind          TEXT     NOT NULL CHECK (kind IN (
        'bowled', 'caught', 'caught and bowled', 'lbw', 'stumped', 'run out', 'hit wicket',
        'retired hurt', 'retired out', 'retired not out', 'obstructing the field',
        'hit the ball twice', 'handled the ball', 'timed out')),
    UNIQUE (delivery_id, wicket_number)
);
CREATE INDEX ix_wicket_player_out ON core.wicket (player_out_id);

CREATE TABLE core.wicket_fielder (
    wicket_id      BIGINT   NOT NULL REFERENCES core.wicket (id) ON DELETE CASCADE,
    fielder_number SMALLINT NOT NULL CHECK (fielder_number >= 1),
    player_id      INTEGER  NOT NULL REFERENCES core.player (id),
    is_substitute  BOOLEAN  NOT NULL,
    PRIMARY KEY (wicket_id, fielder_number)
);
CREATE INDEX ix_wicket_fielder_player ON core.wicket_fielder (player_id);

-- Playing lists: includes players who neither batted, bowled nor fielded (and Impact Players).
CREATE TABLE core.match_player (
    match_id      INTEGER  NOT NULL REFERENCES core.match (id) ON DELETE CASCADE,
    player_id     INTEGER  NOT NULL REFERENCES core.player (id),
    team_id       SMALLINT NOT NULL REFERENCES core.team (id),
    list_position SMALLINT NOT NULL CHECK (list_position >= 1),
    PRIMARY KEY (match_id, player_id)
);
CREATE INDEX ix_match_player_player ON core.match_player (player_id);

-- Audit of each staging -> core transformation.
CREATE TABLE core.transform_run (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    status               TEXT        NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    matches_run_id       BIGINT, -- staging batches used (core.ingestion_run ids)
    deliveries_run_id    BIGINT,
    match_players_run_id BIGINT,
    started_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at         TIMESTAMPTZ,
    summary              JSONB,      -- row counts, changes, reconciliation results, warnings
    error_summary        TEXT,
    CHECK ((status = 'RUNNING') = (completed_at IS NULL))
);
