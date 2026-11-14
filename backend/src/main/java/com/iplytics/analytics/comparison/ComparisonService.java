package com.iplytics.analytics.comparison;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iplytics.analytics.query.AnalyticsFilter;
import com.iplytics.analytics.query.BattingQueries;
import com.iplytics.analytics.query.BattingQueries.BattingStats;
import com.iplytics.analytics.query.BowlingQueries;
import com.iplytics.analytics.query.BowlingQueries.BowlingStats;
import com.iplytics.analytics.query.ParticipationQueries;
import com.iplytics.common.exception.ResourceNotFoundException;
import com.iplytics.common.exception.UnprocessableRequestException;
import com.iplytics.common.web.RefLookup;
import com.iplytics.common.web.Refs.PlayerRef;
import com.iplytics.player.api.PlayerDtos.BattingSummaryDto;
import com.iplytics.player.api.PlayerDtos.BowlingSummaryDto;
import com.iplytics.player.api.PlayerStatsMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Side-by-side player figures: three set-based queries for any number of players (no N+1). */
@Service
public class ComparisonService {

    private final BattingQueries batting;
    private final BowlingQueries bowling;
    private final ParticipationQueries participation;
    private final RefLookup refs;

    public ComparisonService(BattingQueries batting, BowlingQueries bowling, ParticipationQueries participation,
                             RefLookup refs) {
        this.batting = batting;
        this.bowling = bowling;
        this.participation = participation;
        this.refs = refs;
    }

    public ComparisonDto compare(List<Integer> ids, Discipline discipline, AnalyticsFilter filter) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new UnprocessableRequestException("'ids' must not repeat a player");
        }
        Map<Integer, PlayerRef> players = refs.players(ids);
        ids.stream().filter(id -> !players.containsKey(id)).findFirst().ifPresent(id -> {
            throw ResourceNotFoundException.of("Player", id);
        });
        Map<Integer, Long> matches = participation.matchesByPlayer(ids, filter);
        if (discipline == Discipline.BATTING) {
            Map<Integer, BattingStats> stats = batting.careers(ids, filter);
            return new ComparisonDto(discipline, ids.stream().map(id -> new ComparedPlayerDto(players.get(id),
                    PlayerStatsMapper.batting(stats.getOrDefault(id, BattingStats.none()), matches.getOrDefault(id, 0L)),
                    null)).toList());
        }
        Map<Integer, BowlingStats> stats = bowling.careers(ids, filter);
        return new ComparisonDto(discipline, ids.stream().map(id -> new ComparedPlayerDto(players.get(id), null,
                PlayerStatsMapper.bowling(stats.getOrDefault(id, BowlingStats.none()), matches.getOrDefault(id, 0L))))
                .toList());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "A player's figures; only the requested discipline is present")
    public record ComparedPlayerDto(PlayerRef player, BattingSummaryDto batting, BowlingSummaryDto bowling) {
    }

    @Schema(description = "Players in the order requested")
    public record ComparisonDto(Discipline discipline, List<ComparedPlayerDto> players) {
    }
}
