package com.iplytics.team.service;

import com.iplytics.analytics.leaderboard.api.BattingLeaderboardMetric;
import com.iplytics.analytics.leaderboard.api.BowlingLeaderboardMetric;
import com.iplytics.analytics.leaderboard.service.LeaderboardService;
import com.iplytics.analytics.query.AnalyticsFilter;
import com.iplytics.analytics.query.TeamQueries;
import com.iplytics.common.exception.ResourceNotFoundException;
import com.iplytics.common.web.RefLookup;
import com.iplytics.common.web.StatsFilterParams;
import com.iplytics.common.web.Refs.TeamRef;
import com.iplytics.common.web.Refs.VenueRef;
import com.iplytics.team.api.TeamDtos.TeamDto;
import com.iplytics.team.api.TeamDtos.TeamOpponentStatsDto;
import com.iplytics.team.api.TeamDtos.TeamSeasonRecordDto;
import com.iplytics.team.api.TeamDtos.TeamVenueStatsDto;
import com.iplytics.team.api.TeamDtos.TeamRecordDto;
import com.iplytics.team.api.TeamDtos.TeamSeasonStatsDto;
import com.iplytics.team.api.TeamDtos.TeamStatsDto;
import com.iplytics.team.api.TeamDtos.TopPerformersDto;
import com.iplytics.team.repository.TeamRepository;
import java.util.List;
import java.util.Map;
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

    /** Franchises with their all-time and latest-season records (two grouped queries for the whole list). */
    public List<TeamDto> list() {
        Map<Integer, TeamQueries.TeamStats> allTime = teamQueries.allTeams();
        Map<Integer, TeamQueries.SeasonTeamStats> latest = teamQueries.latestSeasons();
        return teams.findAll().stream().map(t -> withRecords(t, allTime, latest)).toList();
    }

    public TeamDto get(int id) {
        TeamDto team = teams.find(id).orElseThrow(() -> ResourceNotFoundException.of("Team", id));
        return withRecords(team, teamQueries.allTeams(), teamQueries.latestSeasons());
    }

    /** Record per venue, most matches first. */
    public List<TeamVenueStatsDto> venues(int id, StatsFilterParams params) {
        require(id, params);
        Map<Integer, VenueRef> venues = refs.venues();
        return teamQueries.byVenue(id, params.toFilter()).stream()
                .map(g -> new TeamVenueStatsDto(venues.get(g.key()), map(g.stats()))).toList();
    }

    /** Record against each opponent, most matches first. */
    public List<TeamOpponentStatsDto> opponents(int id, StatsFilterParams params) {
        require(id, params);
        Map<Integer, TeamRef> teamRefs = refs.teams();
        return teamQueries.byOpponent(id, params.toFilter()).stream()
                .map(g -> new TeamOpponentStatsDto(teamRefs.get(g.key()), map(g.stats()))).toList();
    }

    private static TeamDto withRecords(TeamDto team, Map<Integer, TeamQueries.TeamStats> allTime,
                                       Map<Integer, TeamQueries.SeasonTeamStats> latest) {
        TeamQueries.TeamStats all = allTime.get(team.id());
        TeamQueries.SeasonTeamStats last = latest.get(team.id());
        return team.withRecords(all == null ? null : record(all.overall()),
                last == null ? null : new TeamSeasonRecordDto(last.seasonYear(), record(last.stats().overall())));
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
                s.averageConceded(), s.runRate(), s.opponentRunRate(), s.highestScore());
    }

    private static TeamRecordDto record(TeamQueries.Record r) {
        return new TeamRecordDto(r.played(), r.won(), r.lost(), r.tied(), r.noResult(), r.winPercentage());
    }
}
