package com.iplytics.analytics.query;

/** Breakdown dimension for player and participation queries. Columns exist on every analytics player view. */
public enum Grouping {
    SEASON("season_year"),
    OPPONENT("opponent_team_id"),
    VENUE("venue_id");

    final String column;

    Grouping(String column) {
        this.column = column;
    }
}
