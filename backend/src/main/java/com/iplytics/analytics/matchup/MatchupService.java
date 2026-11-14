package com.iplytics.analytics.matchup;

import com.iplytics.analytics.query.AnalyticsFilter;
import com.iplytics.analytics.query.MatchupQueries;
import com.iplytics.analytics.query.MatchupQueries.Matchup;
import com.iplytics.common.exception.ResourceNotFoundException;
import com.iplytics.common.exception.UnprocessableRequestException;
import com.iplytics.common.web.RefLookup;
import com.iplytics.common.web.Refs.PlayerRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Batter-versus-bowler figures from core.delivery (super overs excluded unless the filter says otherwise). */
@Service
public class MatchupService {

    private final MatchupQueries queries;
    private final RefLookup refs;

    public MatchupService(MatchupQueries queries, RefLookup refs) {
        this.queries = queries;
        this.refs = refs;
    }

    public MatchupDto matchup(int batterId, int bowlerId, AnalyticsFilter filter) {
        if (batterId == bowlerId) {
            throw new UnprocessableRequestException("'batterId' and 'bowlerId' must be different players");
        }
        Map<Integer, PlayerRef> players = refs.players(List.of(batterId, bowlerId));
        for (int id : List.of(batterId, bowlerId)) {
            if (!players.containsKey(id)) {
                throw ResourceNotFoundException.of("Player", id);
            }
        }
        Matchup m = queries.matchup(batterId, bowlerId, filter);
        return new MatchupDto(players.get(batterId), players.get(bowlerId), m.balls() > 0 || m.innings() > 0,
                m.innings(), m.balls(), m.runs(), m.dismissals(), m.fours(), m.sixes(), m.dotBalls(), m.strikeRate(),
                m.average(), m.dotBallPercentage(), m.boundaryPercentage());
    }

    @Schema(description = """
            Balls exclude wides; runs are off the bat; dismissals are wickets credited to the bowler (run outs are not).
            strikeRate = 100 x runs / balls; average = runs / dismissals; percentages are of balls. A pair that never
            met returns occurred = false with zero counts and null ratios.""")
    public record MatchupDto(PlayerRef batter, PlayerRef bowler, boolean occurred, long innings, long balls, long runs,
                             long dismissals, long fours, long sixes, long dotBalls, BigDecimal strikeRate,
                             BigDecimal average, BigDecimal dotBallPercentage, BigDecimal boundaryPercentage) {
    }
}
