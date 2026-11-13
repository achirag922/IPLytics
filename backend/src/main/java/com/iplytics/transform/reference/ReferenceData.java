package com.iplytics.transform.reference;

import java.util.List;

/**
 * Curated reference data shipped with the application (src/main/resources/reference/*.csv).
 *
 * @param franchises   franchise identities (code, current or final name)
 * @param teamNames    every source team name, mapped to a franchise with the seasons it was used
 * @param venueAliases every known source venue name, mapped to a canonical venue and city
 */
public record ReferenceData(List<Franchise> franchises, List<TeamName> teamNames, List<VenueAlias> venueAliases) {

    /** @param homeCity primary home city (curated; matched against venue city for home/away splits) */
    public record Franchise(String code, String name, boolean active, String homeCity) {
    }

    /** @param lastSeason null while the name is still in use */
    public record TeamName(String name, String teamCode, int firstSeason, Integer lastSeason) {
    }

    public record VenueAlias(String alias, String venue, String city) {
    }
}
