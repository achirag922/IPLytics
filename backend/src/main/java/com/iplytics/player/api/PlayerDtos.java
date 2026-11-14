package com.iplytics.player.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iplytics.common.web.Refs.PlayerRef;
import com.iplytics.common.web.Refs.TeamRef;
import com.iplytics.common.web.Refs.VenueRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** Player resources. Statistics exclude super overs; ratios follow docs/metrics.md (null when undefined). */
public final class PlayerDtos {

    private PlayerDtos() {
    }

    @Schema(description = """
            A player with figures inside the requested scope (seasons, franchise; super overs excluded). team is the
            franchise of the player's latest match in scope. battingAverage = runs / dismissals; strikeRate = 100 x
            runs / balls faced; economy = 6 x runs conceded / legal balls; ratios are null without a denominator.""")
    public record PlayerListItemDto(@Schema(example = "412") int id, @Schema(example = "V Kohli") String name,
                                    @Schema(description = "Cricsheet registry id") String sourceId, TeamRef team,
                                    long matches, Integer firstSeason, Integer lastSeason, long battingInnings,
                                    long runs, BigDecimal battingAverage, BigDecimal strikeRate, long bowlingInnings,
                                    long wickets, BigDecimal economy) {
    }

    public record PlayerSummaryDto(@Schema(example = "412") int id, @Schema(example = "V Kohli") String name,
                                   @Schema(description = "Cricsheet registry id", example = "ba607b88") String sourceId,
                                   @Schema(description = "Matches in a playing XI") long matches,
                                   Integer firstSeason, Integer lastSeason) {
    }

    public record PlayerAliasDto(String name, int firstSeason, int lastSeason) {
    }

    public record PlayerTeamDto(TeamRef team, long matches, int firstSeason, int lastSeason) {
    }

    public record PlayerDetailDto(int id, String name, String sourceId, long matches, Integer firstSeason,
                                  Integer lastSeason, List<PlayerAliasDto> aliases, List<PlayerTeamDto> teams) {
    }

    @Schema(description = """
            Batting figures. average = runs / dismissals; strikeRate = 100 x runs / balls faced (wides are not balls
            faced); dot and boundary percentages are per ball faced; run fours are not boundaries.""")
    public record BattingSummaryDto(long matches, long innings, long notOuts, long runs, long balls,
                                    BigDecimal average, BigDecimal strikeRate, long fours, long sixes, long fifties,
                                    long hundreds, long ducks, Integer highestScore, boolean highestScoreNotOut,
                                    long dotBalls, BigDecimal dotBallPercentage, BigDecimal boundaryPercentage,
                                    BigDecimal ballsPerBoundary) {
    }

    @Schema(description = """
            Bowling figures. economy = 6 x runs conceded / legal balls; average = runs conceded / wickets;
            strikeRate = legal balls / wickets. Byes, leg-byes and penalty runs are not charged to the bowler; run
            outs are not bowler wickets; wides/noBalls count deliveries.""")
    public record BowlingSummaryDto(long matches, long innings, long legalBalls, @Schema(example = "48.2") String overs,
                                    long runsConceded, long wickets, BigDecimal economy, BigDecimal average,
                                    BigDecimal strikeRate, long dotBalls, BigDecimal dotBallPercentage, long wides,
                                    long noBalls, long foursConceded, long sixesConceded, long threeWicketHauls,
                                    long fourWicketHauls, long fiveWicketHauls,
                                    @Schema(description = "wickets/runs", example = "4/16") String bestFigures) {
    }

    public record SeasonBattingDto(int season, BattingSummaryDto stats) {
    }

    public record SeasonBowlingDto(int season, BowlingSummaryDto stats) {
    }

    public record PlayerBattingDto(PlayerRef player, BattingSummaryDto summary, List<SeasonBattingDto> bySeason) {
    }

    public record PlayerBowlingDto(PlayerRef player, BowlingSummaryDto summary, List<SeasonBowlingDto> bySeason) {
    }

    public record PhaseBattingDto(@Schema(allowableValues = {"powerplay", "middle", "death"}) String phase,
                                  long runs, long balls, BigDecimal strikeRate,
                                  @Schema(description = "Share of the player's runs in this phase, %") BigDecimal runShare,
                                  long fours, long sixes,
                                  @Schema(description = "Fours and sixes per 100 balls faced in the phase") BigDecimal boundaryPercentage) {
    }

    public record PhaseBowlingDto(@Schema(allowableValues = {"powerplay", "middle", "death"}) String phase,
                                  long legalBalls, String overs, long runsConceded, long wickets, BigDecimal economy) {
    }

    @Schema(description = "Phases by 0-indexed over: powerplay 0-5, middle 6-14, death 15+. Super overs excluded.")
    public record PlayerPhasesDto(PlayerRef player, List<PhaseBattingDto> batting, List<PhaseBowlingDto> bowling) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Figures against one opponent; batting or bowling is absent if the player did not bat or bowl")
    public record OpponentSplitDto(TeamRef opponent, long matches, BattingSummaryDto batting,
                                   BowlingSummaryDto bowling) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Figures at one venue; batting or bowling is absent if the player did not bat or bowl")
    public record VenueSplitDto(VenueRef venue, long matches, BattingSummaryDto batting, BowlingSummaryDto bowling) {
    }

    public record PlayerVsTeamsDto(PlayerRef player, List<OpponentSplitDto> opponents) {
    }

    public record PlayerVenuesDto(PlayerRef player, List<VenueSplitDto> venues) {
    }
}
