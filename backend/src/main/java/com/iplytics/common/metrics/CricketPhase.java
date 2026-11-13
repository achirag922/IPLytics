package com.iplytics.common.metrics;

import java.util.Locale;

/**
 * Innings phase of a delivery (docs/metrics.md section 3). Over numbers are 0-indexed, as stored in core:
 * overs 0-5 are the powerplay, 6-14 the middle overs and 15+ the death overs. Super-over balls never belong to a
 * regular phase. Mirrors the SQL function {@code analytics.phase} (consistency-tested).
 */
public enum CricketPhase {
    POWERPLAY,
    MIDDLE,
    DEATH,
    SUPER_OVER;

    public static final int LAST_POWERPLAY_OVER = 5;
    public static final int LAST_MIDDLE_OVER = 14;

    public static CricketPhase of(int overNumber, boolean superOver) {
        if (superOver) {
            return SUPER_OVER;
        }
        if (overNumber <= LAST_POWERPLAY_OVER) {
            return POWERPLAY;
        }
        return overNumber <= LAST_MIDDLE_OVER ? MIDDLE : DEATH;
    }

    /** The value used by SQL ({@code analytics.phase}). */
    public String sqlName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
