package com.iplytics.analytics.insights;

import com.iplytics.analytics.insights.InsightDtos.BattingFirstInsightsDto;
import com.iplytics.analytics.insights.InsightDtos.BattingFirstRowDto;
import com.iplytics.analytics.insights.InsightDtos.BattingFirstSeasonDto;
import com.iplytics.analytics.insights.InsightDtos.PhaseInsightsDto;
import com.iplytics.analytics.insights.InsightDtos.PhaseRowDto;
import com.iplytics.analytics.insights.InsightDtos.PhaseSeasonDto;
import com.iplytics.analytics.insights.InsightDtos.SeasonChangeDto;
import com.iplytics.analytics.insights.InsightDtos.SeasonComparisonDto;
import com.iplytics.analytics.insights.InsightDtos.SeasonInsightDto;
import com.iplytics.analytics.insights.InsightDtos.TargetBandDto;
import com.iplytics.analytics.insights.InsightDtos.TeamTossDto;
import com.iplytics.analytics.insights.InsightDtos.TossDecisionDto;
import com.iplytics.analytics.insights.InsightDtos.TossInsightsDto;
import com.iplytics.analytics.insights.InsightDtos.TossRowDto;
import com.iplytics.analytics.insights.InsightDtos.TossSeasonDto;
import com.iplytics.analytics.query.AnalyticsFilter;
import com.iplytics.analytics.query.InsightQueries;
import com.iplytics.analytics.query.InsightQueries.BattingFirstRow;
import com.iplytics.analytics.query.InsightQueries.PhaseTotals;
import com.iplytics.analytics.query.InsightQueries.SeasonInsight;
import com.iplytics.analytics.query.InsightQueries.TossRow;
import com.iplytics.analytics.query.SeasonQueries;
import com.iplytics.common.metrics.CricketMetrics;
import com.iplytics.common.metrics.CricketPhase;
import com.iplytics.common.web.RefLookup;
import com.iplytics.common.web.Refs.TeamRef;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/** Maps the insight queries to API resources; every figure is computed by the query layer or CricketMetrics. */
@Service
public class InsightsService {

    private final InsightQueries insights;
    private final SeasonQueries seasons;
    private final RefLookup refs;

    public InsightsService(InsightQueries insights, SeasonQueries seasons, RefLookup refs) {
        this.insights = insights;
        this.seasons = seasons;
        this.refs = refs;
    }

    public TossInsightsDto toss(InsightFilterParams params) {
        params.requireKnownReferences(refs);
        AnalyticsFilter filter = params.toFilter();
        InsightQueries.TossImpact impact = insights.toss(filter);
        TeamTossDto team = null;
        if (filter.teamId() != null) {
            InsightQueries.TeamToss t = insights.teamToss(filter.teamId(), filter.team(null));
            team = new TeamTossDto(refs.teams().get(filter.teamId()), t.matches(), t.tossesWon(), t.tossWinPercentage(),
                    t.choseToBat(), t.choseToField(), t.decidedAfterTossWin(), t.winsAfterTossWin(),
                    t.winPercentageAfterTossWin(), t.decidedAfterTossLoss(), t.winsAfterTossLoss(),
                    t.winPercentageAfterTossLoss());
        }
        return new TossInsightsDto(toss(impact.overall()),
                impact.byDecision().stream().map(d -> new TossDecisionDto(d.decision(), toss(d.row()))).toList(),
                impact.bySeason().stream().map(s -> new TossSeasonDto(s.seasonYear(), toss(s.row()))).toList(), team);
    }

    public BattingFirstInsightsDto battingFirst(InsightFilterParams params) {
        params.requireKnownReferences(refs);
        InsightQueries.BattingFirst result = insights.battingFirst(params.toFilter());
        return new BattingFirstInsightsDto(battingFirst(result.overall()),
                result.bySeason().stream().map(s -> new BattingFirstSeasonDto(s.seasonYear(), battingFirst(s.row())))
                        .toList(),
                result.byTarget().stream().map(b -> new TargetBandDto(b.from(), b.to(), b.matches(),
                        b.chasedSuccessfully(), b.successPercentage())).toList());
    }

    public PhaseInsightsDto phases(InsightFilterParams params) {
        params.requireKnownReferences(refs);
        AnalyticsFilter filter = params.toFilter();
        List<PhaseRowDto> bowling = filter.teamId() == null ? null
                : phases(insights.phasesConceded(filter.teamId(), filter));
        List<PhaseSeasonDto> bySeason = insights.phasesBySeason(filter).entrySet().stream().map(e -> {
            Map<CricketPhase, BigDecimal> rates = new EnumMap<>(CricketPhase.class);
            e.getValue().forEach(p -> rates.put(p.phase(), p.runRate()));
            return new PhaseSeasonDto(e.getKey(), rates.get(CricketPhase.POWERPLAY), rates.get(CricketPhase.MIDDLE),
                    rates.get(CricketPhase.DEATH));
        }).toList();
        return new PhaseInsightsDto(phases(insights.phases(filter)), bowling, bySeason);
    }

    /** Seasons in the range, or exactly the listed ones; with two or more, the change from first to last. */
    public SeasonComparisonDto seasons(Integer from, Integer to, Collection<Integer> only) {
        Integer low = only.isEmpty() ? from : only.stream().min(Integer::compare).orElse(null);
        Integer high = only.isEmpty() ? to : only.stream().max(Integer::compare).orElse(null);
        Map<Integer, TeamRef> teams = refs.teams();
        Map<Integer, Integer> champions = seasons.champions();
        List<SeasonInsightDto> rows = insights.seasons(low, high).stream()
                .filter(s -> only.isEmpty() || only.contains(s.seasonYear()))
                .map(s -> season(s, champions.get(s.seasonYear()) == null ? null
                        : teams.get(champions.get(s.seasonYear())))).toList();
        SeasonChangeDto change = rows.size() < 2 ? null : change(rows.get(0), rows.get(rows.size() - 1));
        return new SeasonComparisonDto(rows, change);
    }

    private static SeasonInsightDto season(SeasonInsight s, TeamRef champion) {
        return new SeasonInsightDto(s.seasonYear(), s.matches(), s.runs(), s.averageScore(), s.runRate(),
                s.foursPerMatch(), s.sixesPerMatch(), s.wicketsPerMatch(), s.averageFirstInningsScore(),
                s.battingFirstWinPercentage(), s.tossWinnerWinPercentage(), s.powerplayRunRate(), s.middleRunRate(),
                s.deathRunRate(), s.highestScore(), champion);
    }

    private static SeasonChangeDto change(SeasonInsightDto a, SeasonInsightDto b) {
        Function<Function<SeasonInsightDto, BigDecimal>, BigDecimal> delta =
                metric -> CricketMetrics.change(metric.apply(a), metric.apply(b));
        return new SeasonChangeDto(a.season(), b.season(), delta.apply(SeasonInsightDto::averageScore),
                delta.apply(SeasonInsightDto::runRate), delta.apply(SeasonInsightDto::foursPerMatch),
                delta.apply(SeasonInsightDto::sixesPerMatch), delta.apply(SeasonInsightDto::wicketsPerMatch),
                delta.apply(SeasonInsightDto::averageFirstInningsScore),
                delta.apply(SeasonInsightDto::battingFirstWinPercentage),
                delta.apply(SeasonInsightDto::tossWinnerWinPercentage), delta.apply(SeasonInsightDto::powerplayRunRate),
                delta.apply(SeasonInsightDto::middleRunRate), delta.apply(SeasonInsightDto::deathRunRate));
    }

    private static TossRowDto toss(TossRow r) {
        return new TossRowDto(r.matches(), r.decided(), r.tossWinnerWins(), r.tossWinnerWinPercentage(),
                r.choseToBatFirst());
    }

    private static BattingFirstRowDto battingFirst(BattingFirstRow r) {
        return new BattingFirstRowDto(r.decisiveMatches(), r.battingFirstWins(), r.chasingWins(),
                r.battingFirstWinPercentage(), r.chasingWinPercentage(), r.averageFirstInningsScore());
    }

    /** The three regular phases in innings order; a phase without a ball in scope is a zero row. */
    private static List<PhaseRowDto> phases(List<PhaseTotals> totals) {
        Map<CricketPhase, PhaseTotals> byPhase = new EnumMap<>(CricketPhase.class);
        totals.forEach(p -> byPhase.put(p.phase(), p));
        return List.of(CricketPhase.POWERPLAY, CricketPhase.MIDDLE, CricketPhase.DEATH).stream()
                .map(phase -> byPhase.getOrDefault(phase, new PhaseTotals(phase, 0, 0, 0, "0.0", null, 0, null, null,
                        0, 0, null, null)))
                .map(p -> new PhaseRowDto(
                p.phase().sqlName(), p.innings(), p.runs(), p.legalBalls(), p.overs(), p.runRate(), p.wickets(),
                p.wicketsPerInnings(), p.runsPerInnings(), p.fours(), p.sixes(), p.boundaryPercentage(),
                p.dotBallPercentage())).toList();
    }
}
