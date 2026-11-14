package com.iplytics.venue.service;

import com.iplytics.analytics.query.VenueQueries;
import com.iplytics.common.exception.ResourceNotFoundException;
import com.iplytics.common.web.SeasonFilterParams;
import com.iplytics.venue.api.VenueDtos.VenueDto;
import com.iplytics.venue.api.VenueDtos.VenueStatsDto;
import com.iplytics.venue.repository.VenueRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** Venue use cases on top of the V1.3 venue query. */
@Service
public class VenueService {

    private final VenueRepository venues;
    private final VenueQueries venueQueries;

    public VenueService(VenueRepository venues, VenueQueries venueQueries) {
        this.venues = venues;
        this.venueQueries = venueQueries;
    }

    public List<VenueDto> list() {
        return venues.findAll();
    }

    public VenueDto get(int id) {
        return venues.find(id).orElseThrow(() -> ResourceNotFoundException.of("Venue", id));
    }

    public VenueStatsDto stats(int id, SeasonFilterParams params) {
        get(id);
        VenueQueries.VenueStats s = venueQueries.summary(id, params.toFilter());
        return new VenueStatsDto(s.matches(), s.decisiveMatches(), s.battingFirstWins(), s.chasingWins(),
                s.battingFirstWinPercentage(), s.chaseSuccessPercentage(), s.averageFirstInningsScore(),
                s.highestTotal(), s.lowestCompletedTotal(), s.averageRunRate(), s.innings(), s.wicketsPerInnings(),
                s.boundariesPerInnings());
    }
}
