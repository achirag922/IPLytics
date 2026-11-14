package com.iplytics.match.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iplytics.common.web.RefLookup;
import com.iplytics.common.web.SeasonFilterParams;
import com.iplytics.match.repository.MatchCriteria;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Match list filters: the shared season filters plus participants, venue, dates and stage. {@code team} and
 * {@code opponent} both mean "took part"; together they select the meetings of the two.
 */
public class MatchFilterParams extends SeasonFilterParams {

    @Parameter(description = "A franchise that played (team id)")
    @Positive
    private Integer team;

    @Parameter(description = "Another franchise that played (team id)")
    @Positive
    private Integer opponent;

    @Parameter(description = "Venue id")
    @Positive
    private Integer venue;

    @Parameter(description = "First match date (inclusive), yyyy-MM-dd")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @Parameter(description = "Last match date (inclusive), yyyy-MM-dd")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;

    @Parameter(description = "Stage, e.g. League, Qualifier 1, Eliminator, Final (case-insensitive)")
    @Size(max = 40)
    private String stage;

    @JsonIgnore
    @AssertTrue(message = "'dateFrom' must not be after 'dateTo'")
    public boolean isDateRangeOrdered() {
        return dateFrom == null || dateTo == null || !dateFrom.isAfter(dateTo);
    }

    public MatchCriteria toCriteria() {
        return new MatchCriteria(from(), to(), team, opponent, venue, dateFrom, dateTo, stage);
    }

    public void requireKnownReferences(RefLookup refs) {
        refs.requireTeamFilter("team", team);
        refs.requireTeamFilter("opponent", opponent);
        refs.requireVenueFilter("venue", venue);
    }

    /** Applied filters for response metadata (non-null values only). */
    @Override
    public Map<String, Object> describe() {
        Map<String, Object> applied = super.describe();
        putIfSet(applied, "team", team);
        putIfSet(applied, "opponent", opponent);
        putIfSet(applied, "venue", venue);
        putIfSet(applied, "dateFrom", dateFrom);
        putIfSet(applied, "dateTo", dateTo);
        putIfSet(applied, "stage", stage);
        return applied;
    }

    public Integer getTeam() {
        return team;
    }

    public void setTeam(Integer team) {
        this.team = team;
    }

    public Integer getOpponent() {
        return opponent;
    }

    public void setOpponent(Integer opponent) {
        this.opponent = opponent;
    }

    public Integer getVenue() {
        return venue;
    }

    public void setVenue(Integer venue) {
        this.venue = venue;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }
}
