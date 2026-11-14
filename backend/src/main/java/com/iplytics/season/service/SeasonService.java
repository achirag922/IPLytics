package com.iplytics.season.service;

import com.iplytics.analytics.leaderboard.api.BattingLeaderboardMetric;
import com.iplytics.analytics.leaderboard.api.BowlingLeaderboardMetric;
import com.iplytics.analytics.leaderboard.api.LeaderboardDtos.BattingLeaderDto;
import com.iplytics.analytics.leaderboard.api.LeaderboardDtos.BowlingLeaderDto;
import com.iplytics.analytics.leaderboard.service.LeaderboardService;
import com.iplytics.analytics.query.AnalyticsFilter;
import com.iplytics.analytics.query.SeasonQueries;
import com.iplytics.common.exception.ResourceNotFoundException;
import com.iplytics.common.metrics.CricketMetrics;
import com.iplytics.common.web.RefLookup;
import com.iplytics.common.web.Refs.PlayerRef;
import com.iplytics.common.web.Refs.TeamRef;
import com.iplytics.season.api.SeasonDtos.IndividualScoreDto;
import com.iplytics.season.api.SeasonDtos.PointsTableDto;
import com.iplytics.season.api.SeasonDtos.PointsTableRowDto;
import com.iplytics.season.api.SeasonDtos.SeasonDto;
import com.iplytics.season.api.SeasonDtos.SeasonLeaderDto;
import com.iplytics.season.api.SeasonDtos.SeasonSummaryDto;
import com.iplytics.season.api.SeasonDtos.TeamScoreDto;
import com.iplytics.season.repository.SeasonRepository;
import com.iplytics.season.repository.SeasonRepository.SeasonRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Season summary and points table from the V1.3 analytics and season queries. */
@Service
public class SeasonService {

    private static final SeasonQueries.SeasonTotals EMPTY = new SeasonQueries.SeasonTotals(0, 0, 0, 0, 0, 0, null, null);

    private final SeasonRepository seasons;
    private final SeasonQueries seasonQueries;
    private final LeaderboardService leaderboards;
    private final RefLookup refs;

    public SeasonService(SeasonRepository seasons, SeasonQueries seasonQueries, LeaderboardService leaderboards,
                         RefLookup refs) {
        this.seasons = seasons;
        this.seasonQueries = seasonQueries;
        this.leaderboards = leaderboards;
        this.refs = refs;
    }

    public List<SeasonDto> list() {
        Map<Integer, TeamRef> teams = refs.teams();
        Map<Integer, SeasonQueries.SeasonTotals> totals = seasonQueries.totalsBySeason();
        return seasons.findAll().stream().map(s -> toDto(s, teams, totals.get(s.year()))).toList();
    }

    public SeasonSummaryDto summary(int year, int minimumBalls, int minimumOvers) {
        SeasonRow season = require(year);
        Map<Integer, TeamRef> teams = refs.teams();
        AnalyticsFilter filter = AnalyticsFilter.all().season(year);
        SeasonQueries.SeasonTotals totals = seasonQueries.totals(year);
        return new SeasonSummaryDto(year, season.name(), totals.matches(), totals.runs(), totals.wickets(),
                totals.fours(), totals.sixes(), totals.averageScore(), totals.runRate(),
                seasonQueries.highestTeamScore(year).map(s -> score(s, teams)).orElse(null),
                seasonQueries.lowestCompletedTeamScore(year).map(s -> score(s, teams)).orElse(null),
                seasonQueries.highestIndividualScore(year).map(s -> individual(s, teams)).orElse(null),
                season.championId() == null ? null : teams.get(season.championId()),
                batting(leaderboards.batting(BattingLeaderboardMetric.RUNS, filter, null, 1)),
                bowling(leaderboards.bowling(BowlingLeaderboardMetric.WICKETS, filter, null, 1)),
                batting(leaderboards.batting(BattingLeaderboardMetric.STRIKE_RATE, filter, minimumBalls, 1)),
                minimumBalls,
                bowling(leaderboards.bowling(BowlingLeaderboardMetric.ECONOMY, filter, minimumOvers, 1)),
                minimumOvers);
    }

    /** Ordered by points, then net run rate, then wins, then name. */
    public PointsTableDto pointsTable(int year) {
        require(year);
        Map<Integer, TeamRef> teams = refs.teams();
        List<PointsTableRowDto> unranked = seasonQueries.pointsTable(year).stream().map(r -> new PointsTableRowDto(0,
                teams.get(r.teamId()), r.matches(), r.wins(), r.losses(), r.ties(), r.noResults(),
                CricketMetrics.points(r.wins(), r.noResults()),
                CricketMetrics.netRunRate(r.runsFor(), r.ballsFaced(), r.runsAgainst(), r.ballsBowled()),
                r.runsFor(), CricketMetrics.overs(r.ballsFaced()), r.runsAgainst(), CricketMetrics.overs(r.ballsBowled())))
                .sorted(Comparator.comparingLong(PointsTableRowDto::points).reversed()
                        .thenComparing(PointsTableRowDto::netRunRate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Comparator.comparingLong(PointsTableRowDto::wins).reversed())
                        .thenComparing(r -> r.team().name()))
                .toList();
        List<PointsTableRowDto> ranked = new ArrayList<>();
        for (int i = 0; i < unranked.size(); i++) {
            PointsTableRowDto r = unranked.get(i);
            ranked.add(new PointsTableRowDto(i + 1, r.team(), r.matches(), r.wins(), r.losses(), r.ties(),
                    r.noResults(), r.points(), r.netRunRate(), r.runsFor(), r.oversFaced(), r.runsAgainst(),
                    r.oversBowled()));
        }
        return new PointsTableDto(year, ranked);
    }

    private SeasonRow require(int year) {
        return seasons.find(year).orElseThrow(() -> ResourceNotFoundException.of("Season", year));
    }

    private static SeasonDto toDto(SeasonRow s, Map<Integer, TeamRef> teams, SeasonQueries.SeasonTotals totals) {
        SeasonQueries.SeasonTotals t = totals == null ? EMPTY : totals;
        return new SeasonDto(s.year(), s.name(), s.sourceSeason(), s.matches(),
                s.championId() == null ? null : teams.get(s.championId()), t.runs(), t.wickets(), t.fours(), t.sixes(),
                t.averageScore(), t.runRate());
    }

    private IndividualScoreDto individual(SeasonQueries.IndividualScore s, Map<Integer, TeamRef> teams) {
        PlayerRef player = refs.players(List.of(s.playerId())).get(s.playerId());
        return new IndividualScoreDto(player, teams.get(s.teamId()), teams.get(s.opponentId()), s.runs(), s.balls(),
                s.notOut(), s.matchId(), s.date());
    }

    private static TeamScoreDto score(SeasonQueries.TeamScore s, Map<Integer, TeamRef> teams) {
        return new TeamScoreDto(teams.get(s.teamId()), teams.get(s.opponentId()), s.runs(), s.wickets(),
                CricketMetrics.overs(s.legalBalls()), s.matchId(), s.date());
    }

    private static SeasonLeaderDto batting(List<BattingLeaderDto> top) {
        return top.isEmpty() ? null : new SeasonLeaderDto(top.get(0).player(), top.get(0).value());
    }

    private static SeasonLeaderDto bowling(List<BowlingLeaderDto> top) {
        return top.isEmpty() ? null : new SeasonLeaderDto(top.get(0).player(), top.get(0).value());
    }

}
