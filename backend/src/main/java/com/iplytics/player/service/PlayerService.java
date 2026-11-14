package com.iplytics.player.service;

import com.iplytics.analytics.comparison.Discipline;
import com.iplytics.analytics.query.AnalyticsFilter;
import com.iplytics.analytics.query.BattingQueries;
import com.iplytics.analytics.query.BowlingQueries;
import com.iplytics.analytics.query.Grouping;
import com.iplytics.analytics.query.ParticipationQueries;
import com.iplytics.analytics.query.PlayerTotalsQueries;
import com.iplytics.common.exception.ResourceNotFoundException;
import com.iplytics.common.metrics.CricketMetrics;
import com.iplytics.common.web.PageSlice;
import com.iplytics.common.web.RefLookup;
import com.iplytics.common.web.Refs.PlayerRef;
import com.iplytics.common.web.Refs.TeamRef;
import com.iplytics.common.web.Refs.VenueRef;
import com.iplytics.common.web.StatsFilterParams;
import com.iplytics.player.api.PlayerDtos.BattingSummaryDto;
import com.iplytics.player.api.PlayerDtos.BowlingSummaryDto;
import com.iplytics.player.api.PlayerDtos.OpponentSplitDto;
import com.iplytics.player.api.PlayerDtos.PhaseBattingDto;
import com.iplytics.player.api.PlayerDtos.PhaseBowlingDto;
import com.iplytics.player.api.PlayerDtos.PlayerBattingDto;
import com.iplytics.player.api.PlayerDtos.PlayerBowlingDto;
import com.iplytics.player.api.PlayerDtos.PlayerDetailDto;
import com.iplytics.player.api.PlayerDtos.PlayerListItemDto;
import com.iplytics.player.api.PlayerDtos.PlayerPhasesDto;
import com.iplytics.player.api.PlayerDtos.PlayerSummaryDto;
import com.iplytics.player.api.PlayerDtos.PlayerTeamDto;
import com.iplytics.player.api.PlayerDtos.PlayerVenuesDto;
import com.iplytics.player.api.PlayerDtos.PlayerVsTeamsDto;
import com.iplytics.player.api.PlayerDtos.SeasonBattingDto;
import com.iplytics.player.api.PlayerDtos.SeasonBowlingDto;
import com.iplytics.player.api.PlayerDtos.VenueSplitDto;
import com.iplytics.player.api.PlayerListParams;
import com.iplytics.player.api.PlayerSort;
import com.iplytics.player.api.PlayerStatsMapper;
import com.iplytics.player.repository.PlayerRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Player use cases. Statistics come from the V1.3 analytics query layer; this class validates, composes and maps.
 * Every method issues a fixed number of queries regardless of the number of rows returned.
 */
@Service
public class PlayerService {

    private final PlayerRepository players;
    private final BattingQueries batting;
    private final BowlingQueries bowling;
    private final ParticipationQueries participation;
    private final PlayerTotalsQueries playerTotals;
    private final RefLookup refs;

    public PlayerService(PlayerRepository players, BattingQueries batting, BowlingQueries bowling,
                         ParticipationQueries participation, PlayerTotalsQueries playerTotals, RefLookup refs) {
        this.players = players;
        this.playerTotals = playerTotals;
        this.batting = batting;
        this.bowling = bowling;
        this.participation = participation;
        this.refs = refs;
    }

    /** Paged players in scope with their totals; ratios via CricketMetrics, team refs from one lookup. */
    public PageSlice<PlayerListItemDto> list(String term, PlayerListParams scope, Discipline discipline,
                                             PlayerSort sort, int page, int size) {
        scope.requireKnownReferences(refs);
        PlayerTotalsQueries.Requires requires = discipline == null ? PlayerTotalsQueries.Requires.ANY
                : discipline == Discipline.BATTING ? PlayerTotalsQueries.Requires.BATTED : PlayerTotalsQueries.Requires.BOWLED;
        PageSlice<PlayerTotalsQueries.PlayerTotalsRow> slice =
                playerTotals.list(term, scope.toFilter(), requires, sort.order(), page, size);
        Map<Integer, TeamRef> teams = refs.teams();
        return new PageSlice<>(slice.items().stream().map(r -> new PlayerListItemDto(r.id(), r.name(), r.sourceId(),
                r.teamId() == null ? null : teams.get(r.teamId()), r.matches(), r.firstSeason(), r.lastSeason(),
                r.battingInnings(), r.runs(), CricketMetrics.battingAverage(r.runs(), r.dismissals()),
                CricketMetrics.strikeRate(r.runs(), r.balls()), r.bowlingInnings(), r.wickets(),
                CricketMetrics.economy(r.runsConceded(), r.legalBalls()))).toList(), slice.total());
    }

    public PlayerDetailDto detail(int id) {
        PlayerSummaryDto player = players.find(id).orElseThrow(() -> ResourceNotFoundException.of("Player", id));
        Map<Integer, TeamRef> teams = refs.teams();
        List<PlayerTeamDto> spells = players.teams(id).stream()
                .map(s -> new PlayerTeamDto(teams.get(s.teamId()), s.matches(), s.firstSeason(), s.lastSeason()))
                .toList();
        return new PlayerDetailDto(player.id(), player.name(), player.sourceId(), player.matches(),
                player.firstSeason(), player.lastSeason(), players.aliases(id), spells);
    }

    public PlayerBattingDto batting(int id, StatsFilterParams params) {
        PlayerRef player = require(id, params);
        AnalyticsFilter filter = params.toFilter();
        Map<Integer, Long> matchesBySeason = participation.matchesBy(id, filter, Grouping.SEASON);
        List<SeasonBattingDto> seasons = batting.grouped(id, filter, Grouping.SEASON).stream()
                .map(g -> new SeasonBattingDto(g.key(), PlayerStatsMapper.batting(g.stats(),
                        matchesBySeason.getOrDefault(g.key(), 0L))))
                .toList();
        return new PlayerBattingDto(player,
                PlayerStatsMapper.batting(batting.career(id, filter), participation.matches(id, filter)), seasons);
    }

    public PlayerBowlingDto bowling(int id, StatsFilterParams params) {
        PlayerRef player = require(id, params);
        AnalyticsFilter filter = params.toFilter();
        Map<Integer, Long> matchesBySeason = participation.matchesBy(id, filter, Grouping.SEASON);
        List<SeasonBowlingDto> seasons = bowling.grouped(id, filter, Grouping.SEASON).stream()
                .map(g -> new SeasonBowlingDto(g.key(), PlayerStatsMapper.bowling(g.stats(),
                        matchesBySeason.getOrDefault(g.key(), 0L))))
                .toList();
        return new PlayerBowlingDto(player,
                PlayerStatsMapper.bowling(bowling.career(id, filter), participation.matches(id, filter)), seasons);
    }

    public PlayerPhasesDto phases(int id, StatsFilterParams params) {
        PlayerRef player = require(id, params);
        AnalyticsFilter filter = params.toFilter();
        List<BattingQueries.PhaseBatting> battingPhases = batting.phases(id, filter);
        long totalRuns = battingPhases.stream().mapToLong(BattingQueries.PhaseBatting::runs).sum();
        return new PlayerPhasesDto(player,
                battingPhases.stream().map(p -> new PhaseBattingDto(p.phase().sqlName(), p.runs(), p.balls(),
                        p.strikeRate(), CricketMetrics.percentage(p.runs(), totalRuns), p.fours(), p.sixes(),
                        p.boundaryPercentage())).toList(),
                bowling.phases(id, filter).stream().map(p -> new PhaseBowlingDto(p.phase().sqlName(), p.legalBalls(),
                        CricketMetrics.overs(p.legalBalls()), p.runsConceded(), p.wickets(), p.economy())).toList());
    }

    public PlayerVsTeamsDto vsTeams(int id, StatsFilterParams params) {
        PlayerRef player = require(id, params);
        Map<Integer, TeamRef> teams = refs.teams();
        List<OpponentSplitDto> splits = splits(id, params.toFilter(), Grouping.OPPONENT).stream()
                .map(s -> new OpponentSplitDto(teams.get(s.key()), s.matches(), s.batting(), s.bowling()))
                .sorted(Comparator.comparing((OpponentSplitDto s) -> s.opponent().name()))
                .toList();
        return new PlayerVsTeamsDto(player, splits);
    }

    public PlayerVenuesDto venues(int id, StatsFilterParams params) {
        PlayerRef player = require(id, params);
        Map<Integer, VenueRef> venues = refs.venues();
        List<VenueSplitDto> splits = splits(id, params.toFilter(), Grouping.VENUE).stream()
                .map(s -> new VenueSplitDto(venues.get(s.key()), s.matches(), s.batting(), s.bowling()))
                .sorted(Comparator.comparingLong(VenueSplitDto::matches).reversed()
                        .thenComparing(s -> s.venue().name()))
                .toList();
        return new PlayerVenuesDto(player, splits);
    }

    /** Three grouped queries (matches, batting, bowling) merged by key. */
    private List<Split> splits(int id, AnalyticsFilter filter, Grouping grouping) {
        Map<Integer, Long> matches = participation.matchesBy(id, filter, grouping);
        Map<Integer, BattingQueries.Grouped> bat = batting.grouped(id, filter, grouping).stream()
                .collect(Collectors.toMap(BattingQueries.Grouped::key, Function.identity()));
        Map<Integer, BowlingQueries.Grouped> bowl = bowling.grouped(id, filter, grouping).stream()
                .collect(Collectors.toMap(BowlingQueries.Grouped::key, Function.identity()));
        TreeSet<Integer> keys = new TreeSet<>(matches.keySet());
        keys.addAll(bat.keySet());
        keys.addAll(bowl.keySet());
        return keys.stream().map(key -> {
            long played = matches.getOrDefault(key, 0L);
            BattingSummaryDto b = bat.containsKey(key) ? PlayerStatsMapper.batting(bat.get(key).stats(), played) : null;
            BowlingSummaryDto w = bowl.containsKey(key) ? PlayerStatsMapper.bowling(bowl.get(key).stats(), played) : null;
            return new Split(key, played, b, w);
        }).toList();
    }

    private PlayerRef require(int id, StatsFilterParams params) {
        PlayerSummaryDto player = players.find(id).orElseThrow(() -> ResourceNotFoundException.of("Player", id));
        params.requireKnownReferences(refs);
        return new PlayerRef(player.id(), player.name());
    }

    private record Split(int key, long matches, BattingSummaryDto batting, BowlingSummaryDto bowling) {
    }
}
