package com.iplytics.team.service;

import com.iplytics.analytics.leaderboard.api.BattingLeaderboardMetric;
import com.iplytics.analytics.leaderboard.api.BowlingLeaderboardMetric;
import com.iplytics.analytics.leaderboard.service.LeaderboardService;
import com.iplytics.analytics.query.AnalyticsFilter;
import com.iplytics.analytics.query.TeamQueries;
import com.iplytics.common.exception.ResourceNotFoundException;
import com.iplytics.common.web.RefLookup;
import com.iplytics.common.web.StatsFilterParams;
import com.iplytics.team.api.TeamDtos.TeamDto;
import com.iplytics.team.api.TeamDtos.TeamRecordDto;
import com.iplytics.team.api.TeamDtos.TeamSeasonStatsDto;
import com.iplytics.team.api.TeamDtos.TeamStatsDto;
import com.iplytics.team.api.TeamDtos.TopPerformersDto;
import com.iplytics.team.repository.TeamRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** Franchise use cases on top of the V1.3 team and leaderboard queries. */
@Service
public class TeamService {

    private final TeamRepository teams;
    private final TeamQueries teamQueries;
    private final LeaderboardService leaderboards;
    private final RefLookup refs;

    public TeamService(TeamRepository teams, TeamQueries teamQueries, LeaderboardService leaderboards, RefLookup refs) {
        this.teams = teams;
        this.teamQueries = teamQueries;
        this.leaderboards = leaderboards;
        this.refs = refs;
    }

    public List<TeamDto> list() {
        return teams.findAll();
    }

    public TeamDto get(int id) {
        return teams.find(id).orElseThrow(() -> ResourceNotFoundException.of("Team", id));
    }

    public TeamStatsDto stats(int id, StatsFilterParams params) {
        require(id, params);
        return map(teamQueries.summary(id, params.toFilter()));
    }

    public List<TeamSeasonStatsDto> seasons(int id, StatsFilterParams params) {
        require(id, params);
        return teamQueries.bySeason(id, params.toFilter()).stream()
                .map(s -> new TeamSeasonStatsDto(s.seasonYear(), map(s.stats()))).toList();
    }

    /** Leading run scorers and wicket takers while playing for the franchise. */
    public TopPerformersDto topPerformers(int id, StatsFilterParams params, int limit) {
        require(id, params);
        AnalyticsFilter filter = params.toFilter().team(id);
        return new TopPerformersDto(leaderboards.batting(BattingLeaderboardMetric.RUNS, filter, null, limit),
                leaderboards.bowling(BowlingLeaderboardMetric.WICKETS, filter, null, limit));
    }

    private void require(int id, StatsFilterParams params) {
        if (!refs.teamExists(id)) {
            throw ResourceNotFoundException.of("Team", id);
        }
        params.requireKnownReferences(refs);
    }

    private static TeamStatsDto map(TeamQueries.TeamStats s) {
        return new TeamStatsDto(record(s.overall()), record(s.battingFirst()), record(s.chasing()), record(s.home()),
                record(s.away()), s.runsFor(), s.inningsBatted(), s.averageScore(), s.runsAgainst(), s.inningsBowled(),
                s.averageConceded(), s.runRate(), s.opponentRunRate());
    }

    private static TeamRecordDto record(TeamQueries.Record r) {
        return new TeamRecordDto(r.played(), r.won(), r.lost(), r.tied(), r.noResult(), r.winPercentage());
    }
}
