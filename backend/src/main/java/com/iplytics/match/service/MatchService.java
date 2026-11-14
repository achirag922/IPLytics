package com.iplytics.match.service;

import static java.util.stream.Collectors.groupingBy;

import com.iplytics.common.exception.ResourceNotFoundException;
import com.iplytics.common.metrics.CricketMetrics;
import com.iplytics.common.web.PageSlice;
import com.iplytics.common.web.RefLookup;
import com.iplytics.common.web.Refs.PlayerRef;
import com.iplytics.common.web.Refs.TeamRef;
import com.iplytics.common.web.Refs.VenueRef;
import com.iplytics.match.api.MatchDtos.BattingLineDto;
import com.iplytics.match.api.MatchDtos.BowlingLineDto;
import com.iplytics.match.api.MatchDtos.ExtrasDto;
import com.iplytics.match.api.MatchDtos.FallOfWicketDto;
import com.iplytics.match.api.MatchDtos.FielderDto;
import com.iplytics.match.api.MatchDtos.InningsProgressionDto;
import com.iplytics.match.api.MatchDtos.InningsScoreDto;
import com.iplytics.match.api.MatchDtos.InningsScorecardDto;
import com.iplytics.match.api.MatchDtos.MatchDetailDto;
import com.iplytics.match.api.MatchDtos.MatchProgressionDto;
import com.iplytics.match.api.MatchDtos.MatchSummaryDto;
import com.iplytics.match.api.MatchDtos.MatchTeamDto;
import com.iplytics.match.api.MatchDtos.OfficialsDto;
import com.iplytics.match.api.MatchDtos.OverProgressionDto;
import com.iplytics.match.api.MatchDtos.ScorecardDto;
import com.iplytics.match.api.MatchDtos.TargetDto;
import com.iplytics.match.api.MatchDtos.TossDto;
import com.iplytics.match.repository.MatchCriteria;
import com.iplytics.match.repository.MatchRepository;
import com.iplytics.match.repository.MatchRepository.BattingRow;
import com.iplytics.match.repository.MatchRepository.BowlingRow;
import com.iplytics.match.repository.MatchRepository.DidNotBatRow;
import com.iplytics.match.repository.MatchRepository.ExtrasRow;
import com.iplytics.match.repository.MatchRepository.FallOfWicketRow;
import com.iplytics.match.repository.MatchRepository.FielderRow;
import com.iplytics.match.repository.MatchRepository.InningsRow;
import com.iplytics.match.repository.MatchRepository.MatchRow;
import com.iplytics.match.repository.MatchRepository.OverRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Assembles match resources from {@link MatchRepository} rows; no SQL and no aggregation beyond formatting. */
@Service
public class MatchService {

    private static final Set<String> BOWLER_CREDITED = Set.of("bowled", "caught", "caught and bowled", "lbw",
            "stumped", "hit wicket");

    private final MatchRepository matches;
    private final RefLookup refs;

    public MatchService(MatchRepository matches, RefLookup refs) {
        this.matches = matches;
        this.refs = refs;
    }

    public PageSlice<MatchSummaryDto> list(MatchCriteria criteria, int page, int size) {
        PageSlice<MatchRow> slice = matches.find(criteria, page, size);
        return new PageSlice<>(summaries(slice.items()), slice.total());
    }

    /** Summaries with innings scores: two queries for the whole page. */
    public List<MatchSummaryDto> summaries(List<MatchRow> rows) {
        Map<Integer, List<InningsRow>> innings = matches.innings(rows.stream().map(MatchRow::id).toList()).stream()
                .collect(groupingBy(InningsRow::matchId));
        Map<Integer, TeamRef> teams = refs.teams();
        Map<Integer, VenueRef> venues = refs.venues();
        return rows.stream().map(m -> new MatchSummaryDto(m.id(), m.sourceMatchId(), m.season(), m.date(), m.endDate(),
                m.stage(), m.matchNumber(), venues.get(m.venueId()), team1(m, teams), team2(m, teams),
                team(m.winnerId(), teams), m.result(), m.resultMargin(), m.method(), m.superOver(), resultText(m),
                scores(innings.getOrDefault(m.id(), List.of()), teams))).toList();
    }

    public MatchDetailDto detail(int id) {
        MatchRow match = require(id);
        return detail(match, matches.innings(List.of(id)), refs.teams());
    }

    public ScorecardDto scorecard(int id) {
        MatchRow match = require(id);
        Map<Integer, TeamRef> teams = refs.teams();
        List<InningsRow> innings = matches.innings(List.of(id));
        Map<Integer, ExtrasRow> extras = matches.extras(id).stream()
                .collect(Collectors.toMap(ExtrasRow::inningsId, Function.identity()));
        Map<Integer, List<BattingRow>> batting = matches.batting(id).stream().collect(groupingBy(BattingRow::inningsId));
        Map<Long, List<FielderRow>> fielders = matches.fielders(id).stream().collect(groupingBy(FielderRow::wicketId));
        Map<Integer, List<BowlingRow>> bowling = matches.bowling(id).stream().collect(groupingBy(BowlingRow::inningsId));
        Map<Integer, List<FallOfWicketRow>> falls = matches.fallOfWickets(id).stream()
                .collect(groupingBy(FallOfWicketRow::inningsId));
        Map<Integer, List<DidNotBatRow>> didNotBat = matches.didNotBat(id).stream()
                .collect(groupingBy(DidNotBatRow::teamId));

        List<InningsScorecardDto> cards = new ArrayList<>();
        for (InningsRow i : innings) {
            ExtrasRow e = extras.getOrDefault(i.inningsId(), new ExtrasRow(i.inningsId(), 0, 0, 0, 0, 0));
            cards.add(new InningsScorecardDto(i.inningsNumber(), i.superOver(), teams.get(i.battingTeamId()),
                    teams.get(i.bowlingTeamId()), i.runs(), i.wickets(), CricketMetrics.overs(i.legalBalls()),
                    i.legalBalls(), i.runRate(), target(match, innings, i.inningsNumber()),
                    new ExtrasDto(i.extras(), e.wides(), e.noBalls(), e.byes(), e.legByes(), e.penalty()),
                    batting.getOrDefault(i.inningsId(), List.of()).stream().map(b -> battingLine(b, fielders)).toList(),
                    i.superOver() ? List.of() : didNotBat.getOrDefault(i.battingTeamId(), List.of()).stream()
                            .map(d -> new PlayerRef(d.playerId(), d.playerName())).toList(),
                    bowling.getOrDefault(i.inningsId(), List.of()).stream().map(MatchService::bowlingLine).toList(),
                    falls.getOrDefault(i.inningsId(), List.of()).stream().map(f -> new FallOfWicketDto(f.wicket(),
                            f.score(), f.ball(), new PlayerRef(f.playerId(), f.playerName()))).toList()));
        }
        return new ScorecardDto(detail(match, innings, teams), cards);
    }

    public MatchProgressionDto progression(int id) {
        MatchRow match = require(id);
        Map<Integer, TeamRef> teams = refs.teams();
        List<InningsRow> innings = matches.innings(List.of(id));
        Map<Integer, List<OverRow>> overs = matches.progression(id).stream()
                .collect(groupingBy(OverRow::inningsNumber));
        return new MatchProgressionDto(id, innings.stream().map(i -> new InningsProgressionDto(i.inningsNumber(),
                i.superOver(), teams.get(i.battingTeamId()), target(match, innings, i.inningsNumber()),
                overs.getOrDefault(i.inningsNumber(), List.of()).stream().map(o -> new OverProgressionDto(
                        o.overNumber() + 1, o.runs(), o.wickets(), o.extras(), o.legalBalls(), o.cumulativeRuns(),
                        o.cumulativeWickets(), CricketMetrics.runRate(o.cumulativeRuns(), o.cumulativeLegalBalls())))
                        .toList())).toList());
    }

    /** Human-readable result, e.g. "Mumbai Indians won by 10 runs" (names as used at the time). */
    static String resultText(MatchRow m) {
        String winner = m.winnerId() == null ? null
                : m.winnerId() == m.team1Id() ? m.team1Name() : m.team2Name();
        String method = m.method() == null ? "" : " (" + m.method() + ")";
        return switch (m.result()) {
            case "runs" -> winner + " won by " + plural(m.resultMargin(), "run") + method;
            case "wickets" -> winner + " won by " + plural(m.resultMargin(), "wicket") + method;
            case "tie" -> winner == null ? "Match tied"
                    : "Match tied (" + winner + (m.superOver() ? " won the super over)" : " won)");
            default -> "No result";
        };
    }

    /** Dismissal as printed on a scorecard: "c Fielder b Bowler", "run out (A/B)", "not out", ... */
    static String dismissalText(String kind, String bowler, List<FielderRow> fielders) {
        if (kind == null) {
            return "not out";
        }
        String fielder = fielders.isEmpty() ? null : fielderName(fielders.get(0));
        return switch (kind) {
            case "bowled" -> "b " + bowler;
            case "lbw" -> "lbw b " + bowler;
            case "caught and bowled" -> "c & b " + bowler;
            case "caught" -> fielder == null ? "c ? b " + bowler
                    : fielders.get(0).playerName().equals(bowler) && !fielders.get(0).substitute() ? "c & b " + bowler
                    : "c " + fielder + " b " + bowler;
            case "stumped" -> "st " + (fielder == null ? "?" : fielder) + " b " + bowler;
            case "hit wicket" -> "hit wicket b " + bowler;
            case "run out" -> fielders.isEmpty() ? "run out"
                    : "run out (" + fielders.stream().map(MatchService::fielderName).collect(Collectors.joining("/")) + ")";
            default -> kind;
        };
    }

    private static String fielderName(FielderRow f) {
        return f.substitute() ? "sub (" + f.playerName() + ")" : f.playerName();
    }

    private static BattingLineDto battingLine(BattingRow b, Map<Long, List<FielderRow>> fielders) {
        List<FielderRow> who = b.wicketId() == null ? List.of() : fielders.getOrDefault(b.wicketId(), List.of());
        boolean credited = b.dismissalKind() != null && BOWLER_CREDITED.contains(b.dismissalKind());
        return new BattingLineDto(b.position(), new PlayerRef(b.playerId(), b.playerName()),
                dismissalText(b.dismissalKind(), b.bowlerName(), who), b.dismissalKind(),
                credited && b.bowlerId() != null ? new PlayerRef(b.bowlerId(), b.bowlerName()) : null,
                who.isEmpty() ? null : who.stream()
                        .map(f -> new FielderDto(new PlayerRef(f.playerId(), f.playerName()), f.substitute())).toList(),
                b.runs(), b.balls(), b.fours(), b.sixes(), b.strikeRate(), b.out());
    }

    private static BowlingLineDto bowlingLine(BowlingRow b) {
        return new BowlingLineDto(new PlayerRef(b.playerId(), b.playerName()), CricketMetrics.overs(b.legalBalls()),
                b.legalBalls(), b.runsConceded(), b.wickets(), b.economy(), b.dotBalls(), b.wides(), b.noBalls(),
                b.foursConceded(), b.sixesConceded());
    }

    /**
     * Runs needed by the side batting second in a pairing: the source target for innings 2 (which carries D/L
     * revisions), otherwise the previous innings plus one. Innings 1, 3, 5 ... set targets and have none.
     */
    private static Integer target(MatchRow match, List<InningsRow> innings, int inningsNumber) {
        if (inningsNumber % 2 == 1) {
            return null;
        }
        if (inningsNumber == 2 && match.targetRuns() != null) {
            return match.targetRuns();
        }
        return innings.stream().filter(i -> i.inningsNumber() == inningsNumber - 1).findFirst()
                .map(i -> i.runs() + 1).orElse(null);
    }

    private MatchRow require(int id) {
        return matches.find(id).orElseThrow(() -> ResourceNotFoundException.of("Match", id));
    }

    private MatchDetailDto detail(MatchRow m, List<InningsRow> innings, Map<Integer, TeamRef> teams) {
        Map<Integer, VenueRef> venues = refs.venues();
        return new MatchDetailDto(m.id(), m.sourceMatchId(), m.season(), m.date(), m.endDate(), m.stage(),
                m.matchNumber(), venues.get(m.venueId()), m.city(), team1(m, teams), team2(m, teams),
                m.tossWinnerId() == null ? null : new TossDto(teams.get(m.tossWinnerId()), m.tossDecision()),
                team(m.winnerId(), teams), team(m.superOverWinnerId(), teams), m.result(), m.resultMargin(),
                m.method(), m.superOver(), resultText(m),
                m.playerOfMatchId() == null ? null : new PlayerRef(m.playerOfMatchId(), m.playerOfMatchName()),
                m.targetRuns() == null && m.targetOvers() == null ? null : new TargetDto(m.targetRuns(), m.targetOvers()),
                m.scheduledOvers(),
                new OfficialsDto(m.umpires(), m.tvUmpires(), m.matchReferees(), m.reserveUmpires()),
                scores(innings, teams));
    }

    private static List<InningsScoreDto> scores(List<InningsRow> innings, Map<Integer, TeamRef> teams) {
        return innings.stream().map(i -> new InningsScoreDto(i.inningsNumber(), teams.get(i.battingTeamId()), i.runs(),
                i.wickets(), CricketMetrics.overs(i.legalBalls()), i.superOver())).toList();
    }

    private static MatchTeamDto team1(MatchRow m, Map<Integer, TeamRef> teams) {
        return new MatchTeamDto(teams.get(m.team1Id()), m.team1Name());
    }

    private static MatchTeamDto team2(MatchRow m, Map<Integer, TeamRef> teams) {
        return new MatchTeamDto(teams.get(m.team2Id()), m.team2Name());
    }

    private static TeamRef team(Integer id, Map<Integer, TeamRef> teams) {
        return id == null ? null : teams.get(id);
    }

    private static String plural(Integer n, String unit) {
        return n + " " + unit + (n != null && n == 1 ? "" : "s");
    }
}
