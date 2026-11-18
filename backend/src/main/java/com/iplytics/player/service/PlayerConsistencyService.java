package com.iplytics.player.service;

import com.iplytics.analytics.query.ConsistencyQueries;
import com.iplytics.common.exception.ResourceNotFoundException;
import com.iplytics.common.web.RefLookup;
import com.iplytics.common.web.Refs.PlayerRef;
import com.iplytics.common.web.Refs.TeamRef;
import com.iplytics.common.web.StatsFilterParams;
import com.iplytics.player.api.ConsistencyDtos.BattingConsistencyDto;
import com.iplytics.player.api.ConsistencyDtos.BattingFormDto;
import com.iplytics.player.api.ConsistencyDtos.BowlingConsistencyDto;
import com.iplytics.player.api.ConsistencyDtos.BowlingFormDto;
import com.iplytics.player.api.ConsistencyDtos.PlayerConsistencyDto;
import com.iplytics.player.api.ConsistencyDtos.InningsBandDto;
import com.iplytics.player.repository.PlayerRepository;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Maps the consistency queries; opponent ids become team references (one lookup). */
@Service
public class PlayerConsistencyService {

    private final PlayerRepository players;
    private final ConsistencyQueries consistency;
    private final RefLookup refs;

    public PlayerConsistencyService(PlayerRepository players, ConsistencyQueries consistency, RefLookup refs) {
        this.players = players;
        this.consistency = consistency;
        this.refs = refs;
    }

    public PlayerConsistencyDto consistency(int id, StatsFilterParams params) {
        var player = players.find(id).orElseThrow(() -> ResourceNotFoundException.of("Player", id));
        params.requireKnownReferences(refs);
        var filter = params.toFilter();
        Map<Integer, TeamRef> teams = refs.teams();
        ConsistencyQueries.BattingConsistency bat = consistency.batting(id, filter);
        ConsistencyQueries.BowlingConsistency bowl = consistency.bowling(id, filter);
        return new PlayerConsistencyDto(new PlayerRef(player.id(), player.name()),
                new BattingConsistencyDto(bat.innings(), bat.average(), bat.medianScore(), bat.standardDeviation(),
                        bat.coefficientOfVariation(), bat.thirtyPlusPercentage(), bat.fiftyPlusPercentage(),
                        bat.underTenPercentage(),
                        bat.scoreBands().stream().map(b -> new InningsBandDto(b.label(), b.innings())).toList(),
                        bat.recentInnings().stream().map(i -> new BattingFormDto(i.matchId(), i.date(),
                                teams.get(i.opponentId()), i.runs(), i.balls(), i.notOut(), i.strikeRate()))
                                .toList()),
                new BowlingConsistencyDto(bowl.innings(), bowl.wicketInningsPercentage(),
                        bowl.multiWicketInningsPercentage(), bowl.medianWickets(), bowl.economyStandardDeviation(),
                        bowl.economyCoefficientOfVariation(), bowl.economicalInningsPercentage(),
                        bowl.recentInnings().stream().map(i -> new BowlingFormDto(i.matchId(), i.date(),
                                teams.get(i.opponentId()), i.overs(), i.runsConceded(), i.wickets(), i.economy()))
                                .toList()));
    }
}
