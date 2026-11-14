package com.iplytics.analytics.headtohead;

import com.iplytics.analytics.headtohead.HeadToHeadDtos.HeadToHeadDto;
import com.iplytics.analytics.headtohead.HeadToHeadDtos.RivalSideDto;
import com.iplytics.analytics.headtohead.HeadToHeadDtos.SeasonMeetingsDto;
import com.iplytics.analytics.query.AnalyticsFilter;
import com.iplytics.analytics.query.HeadToHeadQueries;
import com.iplytics.analytics.query.HeadToHeadQueries.HeadToHead;
import com.iplytics.common.exception.ResourceNotFoundException;
import com.iplytics.common.exception.UnprocessableRequestException;
import com.iplytics.common.web.RefLookup;
import com.iplytics.common.web.Refs.TeamRef;
import com.iplytics.match.repository.MatchCriteria;
import com.iplytics.match.service.MatchService;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Rivalry figures from analytics.team_match_result (V1.3) plus the latest meetings from the match list. */
@Service
public class HeadToHeadService {

    static final int RECENT_MATCHES = 5;

    private final HeadToHeadQueries queries;
    private final MatchService matches;
    private final RefLookup refs;

    public HeadToHeadService(HeadToHeadQueries queries, MatchService matches, RefLookup refs) {
        this.queries = queries;
        this.matches = matches;
        this.refs = refs;
    }

    public HeadToHeadDto headToHead(int team1, int team2, Integer seasonFrom, Integer seasonTo, Integer venue) {
        if (team1 == team2) {
            throw new UnprocessableRequestException("'team1' and 'team2' must be different franchises");
        }
        Map<Integer, TeamRef> teams = refs.teams();
        require(teams, team1);
        require(teams, team2);
        refs.requireVenueFilter("venue", venue);

        AnalyticsFilter filter = AnalyticsFilter.all().seasons(seasonFrom, seasonTo).venue(venue);
        HeadToHead all = queries.overall(team1, team2, filter);
        return new HeadToHeadDto(all.matches(), all.ties(), all.noResults(),
                new RivalSideDto(teams.get(team1), all.teamAWins(), all.teamAWinsBattingFirst(), all.teamAWinsChasing(),
                        all.teamAAverageScore()),
                new RivalSideDto(teams.get(team2), all.teamBWins(), all.teamBWinsBattingFirst(), all.teamBWinsChasing(),
                        all.teamBAverageScore()),
                queries.bySeason(team1, team2, filter).stream().map(g -> new SeasonMeetingsDto(g.key(),
                        g.headToHead().matches(), g.headToHead().teamAWins(), g.headToHead().teamBWins(),
                        g.headToHead().ties(), g.headToHead().noResults())).toList(),
                matches.list(MatchCriteria.between(team1, team2, seasonFrom, seasonTo, venue), 0, RECENT_MATCHES).items());
    }

    private static void require(Map<Integer, TeamRef> teams, int id) {
        if (!teams.containsKey(id)) {
            throw ResourceNotFoundException.of("Team", id);
        }
    }
}
