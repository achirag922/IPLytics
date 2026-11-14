package com.iplytics.analytics.query;

import com.iplytics.common.metrics.CricketMetrics;
import java.math.BigDecimal;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Batter vs bowler, ball by ball from core, with the same rules as the analytics views: balls faced exclude
 * wides, boundaries exclude run fours, dismissals are wickets credited to the bowler (run outs are not).
 * Super overs are excluded unless the filter asks for them. A matchup that never happened returns zeros.
 */
@Repository
public class MatchupQueries {

    private final NamedParameterJdbcTemplate jdbc;

    public MatchupQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Matchup matchup(int batterId, int bowlerId, AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("batterId", batterId)
                .addValue("bowlerId", bowlerId);
        String sql = """
                with balls as (
                    select d.id, d.innings_id, d.wides, d.batter_runs, d.non_boundary
                      from core.delivery d join analytics.innings_context c on c.innings_id = d.innings_id
                     where d.batter_id = :batterId and d.bowler_id = :bowlerId %s)
                select count(distinct innings_id) as innings,
                       count(*) filter (where wides = 0) as balls,
                       coalesce(sum(batter_runs), 0) as runs,
                       count(*) filter (where batter_runs = 4 and not non_boundary) as fours,
                       count(*) filter (where batter_runs = 6 and not non_boundary) as sixes,
                       count(*) filter (where wides = 0 and batter_runs = 0) as dot_balls,
                       (select count(*) from core.wicket w join balls b on b.id = w.delivery_id
                         where w.player_out_id = :batterId and analytics.is_bowler_wicket(w.kind)) as dismissals
                  from balls""".formatted(filter.conditions("c", true, params));
        return jdbc.queryForObject(sql, params, (rs, n) -> {
            long balls = rs.getLong("balls");
            long runs = rs.getLong("runs");
            long dismissals = rs.getLong("dismissals");
            long boundaries = rs.getLong("fours") + rs.getLong("sixes");
            long dots = rs.getLong("dot_balls");
            return new Matchup(rs.getLong("innings"), balls, runs, dismissals, rs.getLong("fours"), rs.getLong("sixes"),
                    dots, CricketMetrics.strikeRate(runs, balls), CricketMetrics.battingAverage(runs, dismissals),
                    CricketMetrics.dotBallPercentage(dots, balls), CricketMetrics.boundaryPercentage(boundaries, balls));
        });
    }

    /** {@code innings} = innings in which the batter faced the bowler at least once (0: never met). */
    public record Matchup(long innings, long balls, long runs, long dismissals, long fours, long sixes, long dotBalls,
                          BigDecimal strikeRate, BigDecimal average, BigDecimal dotBallPercentage,
                          BigDecimal boundaryPercentage) {
    }
}
