package com.iplytics.transform.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iplytics.transform.reference.ReferenceData.TeamName;
import com.iplytics.transform.reference.ReferenceData.VenueAlias;
import java.io.StringReader;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ReferenceDataLoaderTest {

    private final ReferenceData reference = new ReferenceDataLoader().load();
    private final Map<String, String> franchiseOf = reference.teamNames().stream()
            .collect(Collectors.toMap(TeamName::name, TeamName::teamCode));
    private final Map<String, String> venueOf = reference.venueAliases().stream()
            .collect(Collectors.toMap(VenueAlias::alias, VenueAlias::venue));

    @Test
    void renamedFranchisesShareOneIdentity() {
        assertThat(franchiseOf).containsEntry("Delhi Daredevils", "DC").containsEntry("Delhi Capitals", "DC")
                .containsEntry("Kings XI Punjab", "PBKS").containsEntry("Punjab Kings", "PBKS")
                .containsEntry("Royal Challengers Bangalore", "RCB").containsEntry("Royal Challengers Bengaluru", "RCB")
                .containsEntry("Rising Pune Supergiants", "RPS").containsEntry("Rising Pune Supergiant", "RPS");
    }

    @Test
    void distinctFranchisesStaySeparate() {
        assertThat(java.util.List.of("Deccan Chargers", "Sunrisers Hyderabad", "Pune Warriors", "Kochi Tuskers Kerala",
                "Gujarat Lions", "Gujarat Titans", "Lucknow Super Giants").stream().map(franchiseOf::get))
                .doesNotHaveDuplicates().doesNotContainNull();
        assertThat(reference.franchises()).hasSize(15);
    }

    @Test
    void renamedGroundsShareOneVenue() {
        assertThat(venueOf.get("Feroz Shah Kotla, Delhi")).isEqualTo(venueOf.get("Arun Jaitley Stadium"))
                .isEqualTo("Arun Jaitley Stadium");
        assertThat(venueOf.get("M Chinnaswamy Stadium")).isEqualTo(venueOf.get("M.Chinnaswamy Stadium"))
                .isEqualTo(venueOf.get("M Chinnaswamy Stadium, Bangalore"))
                .isEqualTo(venueOf.get("M Chinnaswamy Stadium, Bengaluru"));
        assertThat(venueOf.get("Sardar Patel Stadium, Motera, Ahmedabad")).isEqualTo("Narendra Modi Stadium");
    }

    @Test
    void rejectsInconsistentReferenceData() {
        String franchises = "code,name,active\nDC,Delhi Capitals,true\nDC,Delhi Capitals,true\n";
        String names = "name,team_code,first_season,last_season\nDelhi Capitals,DC,2019,\nMystery XI,ZZ,2010,2009\n";
        String venues = "venue,city,alias\nEden Gardens,Kolkata,Eden Gardens\nEden Gardens,Kolkata,Eden Gardens\n";

        assertThatThrownBy(() -> ReferenceDataLoader.parse(new StringReader(franchises), new StringReader(names),
                new StringReader(venues)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate franchise code DC")
                .hasMessageContaining("unknown franchise ZZ")
                .hasMessageContaining("last_season before first_season")
                .hasMessageContaining("duplicate venue alias Eden Gardens");
    }
}
