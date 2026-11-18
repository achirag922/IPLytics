package com.iplytics.analytics.insights;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iplytics.common.web.Refs.TeamRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** League-wide insight resources (docs/metrics.md section 9). */
public final class InsightDtos {

    private InsightDtos() {
    }

    @Schema(description = """
            Matches with a recorded toss. decided excludes no-results; tossWinnerWinPercentage = toss winner's wins x
            100 / decided (a tie won in a super over is a win). choseToBatFirst counts tosses won and bat first.""")
    public record TossRowDto(long matches, long decided, long tossWinnerWins, BigDecimal tossWinnerWinPercentage,
                             long choseToBatFirst) {
    }

    public record TossDecisionDto(@Schema(allowableValues = {"bat", "field"}) String decision, TossRowDto result) {
    }

    public record TossSeasonDto(int season, TossRowDto result) {
    }

    @Schema(description = "The selected franchise's own tosses (present when team is given)")
    public record TeamTossDto(TeamRef team, long matches, long tossesWon, BigDecimal tossWinPercentage,
                              long choseToBat, long choseToField, long decidedAfterTossWin, long winsAfterTossWin,
                              BigDecimal winPercentageAfterTossWin, long decidedAfterTossLoss,
                              long winsAfterTossLoss, BigDecimal winPercentageAfterTossLoss) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TossInsightsDto(TossRowDto overall, List<TossDecisionDto> byDecision,
                                  List<TossSeasonDto> bySeason, TeamTossDto team) {
    }

    @Schema(description = """
            Decisive matches (won by runs or wickets). averageFirstInningsScore excludes D/L-adjusted matches.""")
    public record BattingFirstRowDto(long decisiveMatches, long battingFirstWins, long chasingWins,
                                     BigDecimal battingFirstWinPercentage, BigDecimal chasingWinPercentage,
                                     BigDecimal averageFirstInningsScore) {
    }

    public record BattingFirstSeasonDto(int season, BattingFirstRowDto result) {
    }

    @Schema(description = """
            Chases of a target (first-innings total + 1) in the band: from and to inclusive, null unbounded.
            successPercentage = successful chases x 100 / matches. D/L-adjusted matches are left out.""")
    public record TargetBandDto(Integer from, Integer to, long matches, long chasedSuccessfully,
                                BigDecimal successPercentage) {
    }

    public record BattingFirstInsightsDto(BattingFirstRowDto overall, List<BattingFirstSeasonDto> bySeason,
                                          List<TargetBandDto> byTarget) {
    }

    @Schema(description = """
            One phase (powerplay overs 1-6, middle 7-15, death 16-20) over the innings in scope. runRate = 6 x runs /
            legal balls; boundary % and dot % are of legal balls; per-innings figures divide by innings with a ball
            bowled in the phase.""")
    public record PhaseRowDto(@Schema(allowableValues = {"powerplay", "middle", "death"}) String phase,
                              long innings, long runs, long legalBalls, String overs, BigDecimal runRate,
                              long wickets, BigDecimal wicketsPerInnings, BigDecimal runsPerInnings, long fours,
                              long sixes, BigDecimal boundaryPercentage, BigDecimal dotBallPercentage) {
    }

    public record PhaseSeasonDto(int season, BigDecimal powerplayRunRate, BigDecimal middleRunRate,
                                 BigDecimal deathRunRate) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = """
            batting: scoring in the innings in scope (with a team, the team's batting); bowling: with a team, what it
            conceded and took. bySeason follows batting.""")
    public record PhaseInsightsDto(List<PhaseRowDto> batting, List<PhaseRowDto> bowling,
                                   List<PhaseSeasonDto> bySeason) {
    }

    @Schema(description = """
            One season. Per-match figures divide by matches; averageScore by regular innings; battingFirstWinPercentage
            over decisive matches; tossWinnerWinPercentage over matches with a toss and a result.""")
    public record SeasonInsightDto(int season, long matches, long runs, BigDecimal averageScore, BigDecimal runRate,
                                   BigDecimal foursPerMatch, BigDecimal sixesPerMatch, BigDecimal wicketsPerMatch,
                                   BigDecimal averageFirstInningsScore, BigDecimal battingFirstWinPercentage,
                                   BigDecimal tossWinnerWinPercentage, BigDecimal powerplayRunRate,
                                   BigDecimal middleRunRate, BigDecimal deathRunRate, Integer highestScore,
                                   TeamRef champion) {
    }

    @Schema(description = "Change from the first to the last season compared (last minus first)")
    public record SeasonChangeDto(int from, int to, BigDecimal averageScore, BigDecimal runRate,
                                  BigDecimal foursPerMatch, BigDecimal sixesPerMatch, BigDecimal wicketsPerMatch,
                                  BigDecimal averageFirstInningsScore, BigDecimal battingFirstWinPercentage,
                                  BigDecimal tossWinnerWinPercentage, BigDecimal powerplayRunRate,
                                  BigDecimal middleRunRate, BigDecimal deathRunRate) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SeasonComparisonDto(List<SeasonInsightDto> seasons, SeasonChangeDto change) {
    }
}
