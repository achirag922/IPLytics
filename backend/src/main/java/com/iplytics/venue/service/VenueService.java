package com.iplytics.venue.service;

import com.iplytics.analytics.query.TeamQueries;
import com.iplytics.analytics.query.VenueQueries;
import com.iplytics.common.exception.ResourceNotFoundException;
import com.iplytics.common.web.RefLookup;
import com.iplytics.common.web.Refs.TeamRef;
import com.iplytics.common.web.SeasonFilterParams;
import com.iplytics.venue.api.VenueDtos.ScoreBandDto;
import com.iplytics.venue.api.VenueDtos.VenueDto;
import com.iplytics.venue.api.VenueDtos.VenueSeasonStatsDto;
import com.iplytics.venue.api.VenueDtos.VenueStatsDto;
import com.iplytics.venue.api.VenueDtos.VenueTeamDto;
import com.iplytics.venue.repository.VenueRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Venue use cases on top of the V1.3 venue query. */
@Service
public class VenueService {

    private final VenueRepository venues;
    private final VenueQueries venueQueries;
    private final TeamQueries teamQueries;
    private final RefLookup refs;

    public VenueService(VenueRepository venues, VenueQueries venueQueries, TeamQueries teamQueries, RefLookup refs) {
        this.venues = venues;
        this.venueQueries = venueQueries;
        this.teamQueries = teamQueries;
        this.refs = refs;
    }

    /** Each franchise's record at the venue, most wins first. */
    public List<VenueTeamDto> teams(int id, SeasonFilterParams params) {
        get(id);
        Map<Integer, TeamRef> teams = refs.teams();
        return teamQueries.atVenue(id, params.toFilter()).stream().map(g -> {
            TeamQueries.Record r = g.stats().overall();
            return new VenueTeamDto(teams.get(g.key()), r.played(), r.won(), r.lost(), r.noResult(),
                    r.winPercentage(), g.stats().averageScore(), g.stats().highestScore());
        }).toList();
    }

    public List<VenueDto> list() {
        return venues.findAll();
    }

    public VenueDto get(int id) {
        return venues.find(id).orElseThrow(() -> ResourceNotFoundException.of("Venue", id));
    }

    public VenueStatsDto stats(int id, SeasonFilterParams params) {
        get(id);
        return map(venueQueries.summary(id, params.toFilter()));
    }

    public List<VenueSeasonStatsDto> seasons(int id, SeasonFilterParams params) {
        get(id);
        return venueQueries.bySeason(id, params.toFilter()).stream()
                .map(s -> new VenueSeasonStatsDto(s.seasonYear(), map(s.stats()))).toList();
    }

    public List<ScoreBandDto> scoreDistribution(int id, SeasonFilterParams params) {
        get(id);
        return venueQueries.scoreDistribution(id, params.toFilter()).stream()
                .map(b -> new ScoreBandDto(b.from(), b.to(), b.innings())).toList();
    }

    private static VenueStatsDto map(VenueQueries.VenueStats s) {
        return new VenueStatsDto(s.matches(), s.decisiveMatches(), s.battingFirstWins(), s.chasingWins(),
                s.battingFirstWinPercentage(), s.chaseSuccessPercentage(), s.averageFirstInningsScore(),
                s.highestTotal(), s.lowestCompletedTotal(), s.averageRunRate(), s.innings(), s.wicketsPerInnings(),
                s.boundariesPerInnings());
    }
}
