package com.iplytics.ingestion.cricsheet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;

/**
 * Maps one Cricsheet JSON match document to rows of matches.csv, deliveries.csv and match_players.csv.
 * Field-by-field rules are documented in docs/data-source.md. Every source field that has no column ends up
 * in a {@code source_unmapped} JSON column, so nothing is silently discarded.
 */
@Component
public class CricsheetMatchMapper {

    static final String IPL_EVENT = "Indian Premier League";
    static final String SEPARATOR = "|";
    private static final List<String> EXTRA_TYPES = List.of("wides", "noballs", "byes", "legbyes", "penalty");

    private final ObjectMapper json;

    public CricsheetMatchMapper(ObjectMapper json) {
        this.json = json;
    }

    public MappedMatch map(String matchId, JsonNode document) {
        if (!document.isObject()) {
            throw new CricsheetFormatException("not a JSON object", List.of());
        }
        JsonNode info = document.path("info");
        String event = text(info.path("event").path("name"));
        if (!IPL_EVENT.equals(event)) {
            throw new CricsheetFormatException("not an Indian Premier League match (event: " + event + ")", List.of());
        }
        LocalDate startDate = validateRequired(document);

        Residual rest = new Residual(document);
        JsonNode people = info.path("registry").path("people");
        JsonNode teams = info.path("teams");
        List<String> missing = new ArrayList<>();

        Map<String, String> match = new HashMap<>();
        match.put("id", matchId);
        match.put("season", text(rest.take("info", "season")));
        match.put("season_year", String.valueOf(startDate.getYear()));
        JsonNode dates = rest.take("info", "dates");
        match.put("date", text(dates.get(0)));
        match.put("end_date", text(dates.get(dates.size() - 1)));
        match.put("city", text(rest.take("info", "city")));
        match.put("event_name", text(rest.take("info", "event", "name")));
        match.put("match_number", text(rest.take("info", "event", "match_number")));
        String stage = text(rest.take("info", "event", "stage"));
        match.put("match_type", stage != null ? stage : "League");
        JsonNode playerOfMatch = rest.take("info", "player_of_match");
        match.put("player_of_match", joined(playerOfMatch));
        match.put("player_of_match_ids", joinedIds(playerOfMatch, people));
        match.put("venue", text(rest.take("info", "venue")));
        rest.take("info", "teams");
        match.put("team1", text(teams.get(0)));
        match.put("team2", text(teams.get(1)));
        match.put("toss_winner", text(rest.take("info", "toss", "winner")));
        match.put("toss_decision", text(rest.take("info", "toss", "decision")));
        mapOutcome(rest, match);
        mapOfficials(rest, match);
        match.put("scheduled_overs", text(rest.take("info", "overs")));
        match.put("source_data_version", text(rest.take("meta", "data_version")));
        match.put("source_created", text(rest.take("meta", "created")));
        match.put("source_revision", text(rest.take("meta", "revision")));

        List<Map<String, String>> players = mapPlayers(matchId, rest, people);

        rest.take("innings");
        List<Map<String, String>> deliveries = new ArrayList<>();
        ArrayNode inningsLeftovers = json.createArrayNode();
        boolean anySuperOver = false;
        JsonNode innings = document.path("innings");
        for (int i = 0; i < innings.size(); i++) {
            JsonNode inningsNode = innings.get(i);
            Residual inningsRest = new Residual(inningsNode);
            boolean superOver = inningsNode.path("super_over").asBoolean(false);
            inningsRest.take("super_over");
            anySuperOver |= superOver;
            String battingTeam = text(inningsRest.take("team"));
            String bowlingTeam = otherTeam(teams, battingTeam);
            if (battingTeam == null || bowlingTeam == null) {
                missing.add("innings[%d].team (one of info.teams)".formatted(i));
            }
            if (!superOver && match.get("target_runs") == null && inningsNode.has("target")) {
                match.put("target_runs", text(inningsRest.take("target", "runs")));
                match.put("target_overs", text(inningsRest.take("target", "overs")));
            }

            Context context = new Context(matchId, i + 1, superOver, battingTeam, bowlingTeam, people, missing);
            ArrayNode overLeftovers = json.createArrayNode();
            JsonNode overs = inningsRest.take("overs");
            for (int o = 0; overs != null && o < overs.size(); o++) {
                JsonNode over = overs.get(o);
                Residual overRest = new Residual(over);
                String overNumber = text(overRest.take("over"));
                JsonNode balls = overRest.take("deliveries");
                for (int b = 0; balls != null && b < balls.size(); b++) {
                    String location = "innings[%d].overs[%d].deliveries[%d]".formatted(i, o, b);
                    deliveries.add(mapDelivery(context, overNumber, b + 1, balls.get(b), location));
                }
                addLeftover(overLeftovers, overRest.remaining(), "over", over.path("over"));
            }
            ObjectNode inningsLeft = inningsRest.remaining();
            if (!overLeftovers.isEmpty()) {
                inningsLeft.set("overs", overLeftovers);
            }
            addLeftover(inningsLeftovers, inningsLeft, "inning", json.getNodeFactory().numberNode(i + 1));
        }
        match.put("super_over", anySuperOver ? "Y" : "N");

        ObjectNode unmapped = rest.remaining();
        if (!inningsLeftovers.isEmpty()) {
            unmapped.set("innings", inningsLeftovers);
        }
        match.put("source_unmapped", toJson(unmapped));

        return new MappedMatch(matchId, match.get("season"), startDate.getYear(), match, deliveries, players,
                List.copyOf(missing));
    }

    /** Match-level fields without which no row can be produced. */
    private static LocalDate validateRequired(JsonNode document) {
        JsonNode info = document.path("info");
        List<String> missing = new ArrayList<>();
        if (text(info.path("season")) == null) {
            missing.add("info.season");
        }
        if (text(info.path("venue")) == null) {
            missing.add("info.venue");
        }
        JsonNode teams = info.path("teams");
        if (!teams.isArray() || teams.size() != 2) {
            missing.add("info.teams (exactly 2)");
        }
        if (!document.path("innings").isArray()) {
            missing.add("innings");
        }
        LocalDate startDate = null;
        JsonNode dates = info.path("dates");
        if (!dates.isArray() || dates.isEmpty()) {
            missing.add("info.dates");
        } else {
            try {
                startDate = LocalDate.parse(dates.get(0).asText());
            } catch (DateTimeParseException e) {
                missing.add("info.dates[0] (yyyy-MM-dd)");
            }
        }
        if (!missing.isEmpty()) {
            throw new CricsheetFormatException("missing required fields", missing);
        }
        return startDate;
    }

    private static void mapOutcome(Residual rest, Map<String, String> match) {
        String winner = text(rest.take("info", "outcome", "winner"));
        String superOverWinner = text(rest.take("info", "outcome", "eliminator"));
        match.put("winner", winner != null ? winner : superOverWinner); // official winner, incl. super-over ties
        match.put("super_over_winner", superOverWinner);
        if (rest.peek("info", "outcome", "by", "runs") != null) {
            match.put("result", "runs");
            match.put("result_margin", text(rest.take("info", "outcome", "by", "runs")));
        } else if (rest.peek("info", "outcome", "by", "wickets") != null) {
            match.put("result", "wickets");
            match.put("result_margin", text(rest.take("info", "outcome", "by", "wickets")));
        } else {
            match.put("result", text(rest.take("info", "outcome", "result"))); // tie, no result, draw
        }
        match.put("method", text(rest.take("info", "outcome", "method")));
    }

    private static void mapOfficials(Residual rest, Map<String, String> match) {
        JsonNode umpires = rest.peek("info", "officials", "umpires");
        if (umpires != null && umpires.isArray()) {
            match.put("umpire1", text(umpires.get(0)));
            match.put("umpire2", text(umpires.get(1)));
            if (umpires.size() <= 2) {
                rest.take("info", "officials", "umpires"); // more than two stays in source_unmapped
            }
        }
        match.put("tv_umpires", joined(rest.take("info", "officials", "tv_umpires")));
        match.put("match_referees", joined(rest.take("info", "officials", "match_referees")));
        match.put("reserve_umpires", joined(rest.take("info", "officials", "reserve_umpires")));
    }

    /** Playing lists in source order. Registry ids of listed players are consumed; others (officials) remain. */
    private static List<Map<String, String>> mapPlayers(String matchId, Residual rest, JsonNode people) {
        List<Map<String, String>> rows = new ArrayList<>();
        JsonNode players = rest.take("info", "players");
        if (players == null) {
            return rows;
        }
        Iterator<Map.Entry<String, JsonNode>> teams = players.fields();
        while (teams.hasNext()) {
            Map.Entry<String, JsonNode> team = teams.next();
            int position = 0;
            for (JsonNode playerNode : team.getValue()) {
                String player = text(playerNode);
                Map<String, String> row = new HashMap<>();
                row.put("match_id", matchId);
                row.put("team", team.getKey());
                row.put("player", player);
                row.put("player_id", id(people, player));
                row.put("list_position", String.valueOf(++position));
                rows.add(row);
                rest.take("info", "registry", "people", player);
            }
        }
        return rows;
    }

    private Map<String, String> mapDelivery(Context context, String over, int ball, JsonNode delivery,
                                            String location) {
        Residual rest = new Residual(delivery);
        Map<String, String> row = new HashMap<>();
        row.put("match_id", context.matchId());
        row.put("inning", String.valueOf(context.inning()));
        row.put("is_super_over", context.superOver() ? "1" : "0");
        row.put("batting_team", context.battingTeam());
        row.put("bowling_team", context.bowlingTeam());
        row.put("over", over);
        row.put("ball", String.valueOf(ball));
        row.put("actual_delivery", text(rest.take("actual_delivery"))); // scorer's label, legal balls only
        for (String role : List.of("batter", "bowler", "non_striker")) {
            String name = required(rest.take(role), location + "." + role, context.missing());
            row.put(role, name);
            row.put(role + "_id", id(context.people(), name));
        }
        row.put("batsman_runs", required(rest.take("runs", "batter"), location + ".runs.batter", context.missing()));
        row.put("extra_runs", required(rest.take("runs", "extras"), location + ".runs.extras", context.missing()));
        row.put("total_runs", required(rest.take("runs", "total"), location + ".runs.total", context.missing()));
        JsonNode nonBoundary = rest.take("runs", "non_boundary");
        row.put("non_boundary", nonBoundary != null && nonBoundary.asBoolean(false) ? "1" : "0");

        List<String> extrasPresent = new ArrayList<>();
        for (String type : EXTRA_TYPES) {
            JsonNode value = rest.take("extras", type);
            row.put(type, value == null ? "0" : value.asText());
            if (value != null) {
                extrasPresent.add(type);
            }
        }
        row.put("extras_type", extrasPresent.isEmpty() ? null : String.join(SEPARATOR, extrasPresent));

        JsonNode wickets = rest.take("wickets");
        boolean hasWicket = wickets != null && wickets.isArray() && !wickets.isEmpty();
        row.put("is_wicket", hasWicket ? "1" : "0");
        if (hasWicket) {
            putWicket(row, "", wickets.get(0), context.people());
            if (wickets.size() > 1) {
                putWicket(row, "other_", wickets.get(1), context.people());
            }
            row.put("wickets_json", toJson(wickets)); // exact source, incl. 3rd+ wickets and substitute flags
        } else if (wickets != null) {
            rest.remaining().set("wickets", wickets); // unexpected shape: keep it rather than drop it
        }
        row.put("source_unmapped", toJson(rest.remaining()));
        return row;
    }

    private static void putWicket(Map<String, String> row, String prefix, JsonNode wicket, JsonNode people) {
        String playerOut = text(wicket.path("player_out"));
        row.put(prefix + "player_dismissed", playerOut);
        row.put(prefix + "player_dismissed_id", id(people, playerOut));
        row.put(prefix + "dismissal_kind", text(wicket.path("kind")));
        JsonNode fielders = wicket.path("fielders");
        List<String> names = StreamSupport.stream(fielders.spliterator(), false)
                .map(f -> text(f.path("name"))).toList();
        row.put(prefix + "fielder", names.isEmpty() ? null : String.join(SEPARATOR, names.stream()
                .map(n -> n == null ? "" : n).toList()));
        row.put(prefix + "fielder_ids", joinedIds(names, people));
    }

    private void addLeftover(ArrayNode target, ObjectNode leftover, String key, JsonNode keyValue) {
        if (!leftover.isEmpty()) {
            ObjectNode entry = json.createObjectNode();
            entry.set(key, keyValue);
            entry.setAll(leftover);
            target.add(entry);
        }
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise JSON", e);
        }
    }

    private static String otherTeam(JsonNode teams, String team) {
        if (team == null) {
            return null;
        }
        if (team.equals(text(teams.get(0)))) {
            return text(teams.get(1));
        }
        return team.equals(text(teams.get(1))) ? text(teams.get(0)) : null;
    }

    private static String required(JsonNode node, String location, List<String> missing) {
        String value = text(node);
        if (value == null) {
            missing.add(location);
        }
        return value;
    }

    private static String id(JsonNode people, String name) {
        return name == null ? null : text(people.get(name));
    }

    private static String joined(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isArray()) {
            return text(node);
        }
        List<String> values = StreamSupport.stream(node.spliterator(), false).map(JsonNode::asText).toList();
        return values.isEmpty() ? null : String.join(SEPARATOR, values);
    }

    private static String joinedIds(JsonNode names, JsonNode people) {
        if (names == null || !names.isArray()) {
            return null;
        }
        return joinedIds(StreamSupport.stream(names.spliterator(), false).map(JsonNode::asText).toList(), people);
    }

    /** Ids aligned with names; an unknown id is an empty segment. Null when no name has an id. */
    private static String joinedIds(List<String> names, JsonNode people) {
        List<String> ids = names.stream().map(n -> id(people, n)).toList();
        if (ids.stream().allMatch(java.util.Objects::isNull)) {
            return null;
        }
        return String.join(SEPARATOR, ids.stream().map(i -> i == null ? "" : i).toList());
    }

    static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || node.isContainerNode()) {
            return null;
        }
        String value = node.asText();
        return value.isBlank() ? null : value;
    }

    private record Context(String matchId, int inning, boolean superOver, String battingTeam, String bowlingTeam,
                           JsonNode people, List<String> missing) {
    }
}
