package com.iplytics.match.repository;

import java.time.LocalDate;

/** Match list criteria; every field is optional. {@code team} and {@code opponent} must both have played. */
public record MatchCriteria(Integer seasonFrom, Integer seasonTo, Integer team, Integer opponent, Integer venue,
                           LocalDate dateFrom, LocalDate dateTo, String stage) {

    public static MatchCriteria between(int team, int opponent, Integer seasonFrom, Integer seasonTo, Integer venue) {
        return new MatchCriteria(seasonFrom, seasonTo, team, opponent, venue, null, null, null);
    }
}
