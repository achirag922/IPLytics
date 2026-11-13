package com.iplytics.transform.reference;

import com.iplytics.transform.reference.ReferenceData.Franchise;
import com.iplytics.transform.reference.ReferenceData.TeamName;
import com.iplytics.transform.reference.ReferenceData.VenueAlias;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Loads and validates the curated reference CSVs. Invalid reference data stops the transform before it starts. */
@Component
public class ReferenceDataLoader {

    static final String FRANCHISES = "reference/franchises.csv";
    static final String TEAM_NAMES = "reference/team_names.csv";
    static final String VENUES = "reference/venues.csv";

    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
            .setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setTrim(true).get();

    public ReferenceData load() {
        try (Reader franchises = classpath(FRANCHISES); Reader teamNames = classpath(TEAM_NAMES);
             Reader venues = classpath(VENUES)) {
            return parse(franchises, teamNames, venues);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read reference data", e);
        }
    }

    public static ReferenceData parse(Reader franchisesCsv, Reader teamNamesCsv, Reader venuesCsv) throws IOException {
        List<String> problems = new ArrayList<>();
        List<Franchise> franchises = records(franchisesCsv).stream()
                .map(r -> new Franchise(r.get("code"), r.get("name"), Boolean.parseBoolean(r.get("active")),
                        r.isMapped("home_city") ? blankToNull(r.get("home_city")) : null))
                .toList();
        List<TeamName> teamNames = new ArrayList<>();
        for (CSVRecord r : records(teamNamesCsv)) {
            try {
                String last = r.get("last_season");
                teamNames.add(new TeamName(r.get("name"), r.get("team_code"), Integer.parseInt(r.get("first_season")),
                        last.isEmpty() ? null : Integer.valueOf(last)));
            } catch (NumberFormatException e) {
                problems.add("team_names.csv line %d: invalid season".formatted(r.getRecordNumber() + 1));
            }
        }
        List<VenueAlias> venues = records(venuesCsv).stream()
                .map(r -> new VenueAlias(r.get("alias"), r.get("venue"), blankToNull(r.get("city"))))
                .toList();

        duplicates(franchises, Franchise::code).forEach(c -> problems.add("duplicate franchise code " + c));
        duplicates(franchises, Franchise::name).forEach(n -> problems.add("duplicate franchise name " + n));
        duplicates(teamNames, TeamName::name).forEach(n -> problems.add("duplicate team name " + n));
        duplicates(venues, VenueAlias::alias).forEach(a -> problems.add("duplicate venue alias " + a));
        Map<String, Franchise> byCode = franchises.stream()
                .collect(Collectors.toMap(Franchise::code, Function.identity(), (a, b) -> a));
        for (Franchise f : franchises) {
            if (f.code().isBlank() || f.name().isBlank()) {
                problems.add("franchise with blank code or name");
            }
            if (teamNames.stream().noneMatch(n -> n.teamCode().equals(f.code()) && n.name().equals(f.name()))) {
                problems.add("franchise %s name '%s' is not listed in team_names.csv".formatted(f.code(), f.name()));
            }
        }
        for (TeamName n : teamNames) {
            if (!byCode.containsKey(n.teamCode())) {
                problems.add("team name '%s' refers to unknown franchise %s".formatted(n.name(), n.teamCode()));
            }
            if (n.lastSeason() != null && n.lastSeason() < n.firstSeason()) {
                problems.add("team name '%s' has last_season before first_season".formatted(n.name()));
            }
        }
        for (VenueAlias v : venues) {
            if (v.alias().isBlank() || v.venue().isBlank()) {
                problems.add("venue row with blank alias or venue");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid reference data: " + String.join("; ", problems));
        }
        return new ReferenceData(franchises, List.copyOf(teamNames), venues);
    }

    private static List<CSVRecord> records(Reader reader) throws IOException {
        try (CSVParser parser = FORMAT.parse(reader)) {
            return parser.getRecords();
        }
    }

    private static <T> Set<String> duplicates(List<T> items, Function<T, String> key) {
        Set<String> seen = new HashSet<>();
        return items.stream().map(key).filter(k -> !seen.add(k)).collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    private static Reader classpath(String path) throws IOException {
        return new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
