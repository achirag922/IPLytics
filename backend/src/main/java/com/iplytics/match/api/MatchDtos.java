package com.iplytics.match.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iplytics.common.web.Refs.PlayerRef;
import com.iplytics.common.web.Refs.TeamRef;
import com.iplytics.common.web.Refs.VenueRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Match resources. Overs are display notation ("19.4" = 19 overs and 4 legal balls). */
public final class MatchDtos {

    private MatchDtos() {
    }

    public record MatchTeamDto(TeamRef team, @Schema(description = "Name used at the time", example = "Delhi Daredevils")
                               String nameAtTheTime) {
    }

    public record InningsScoreDto(int inningsNumber, TeamRef team, int runs, int wickets,
                                  @Schema(example = "20.0") String overs, boolean superOver) {
    }

    public record MatchSummaryDto(@Schema(example = "1021") int id,
                                  @Schema(description = "Cricsheet match id", example = "1426312") long sourceMatchId,
                                  int season, LocalDate date, LocalDate endDate,
                                  @Schema(example = "League") String stage, Integer matchNumber, VenueRef venue,
                                  MatchTeamDto team1, MatchTeamDto team2, TeamRef winner,
                                  @Schema(allowableValues = {"runs", "wickets", "tie", "no result"}) String result,
                                  Integer resultMargin, @Schema(example = "D/L") String method, boolean superOver,
                                  @Schema(example = "Mumbai Indians won by 10 runs") String resultText,
                                  List<InningsScoreDto> innings) {
    }

    public record TossDto(TeamRef winner, @Schema(allowableValues = {"bat", "field"}) String decision) {
    }

    public record TargetDto(Integer runs, BigDecimal overs) {
    }

    public record OfficialsDto(List<String> umpires, List<String> tvUmpires, List<String> matchReferees,
                               List<String> reserveUmpires) {
    }

    public record MatchDetailDto(int id, long sourceMatchId, int season, LocalDate date, LocalDate endDate,
                                 String stage, Integer matchNumber, VenueRef venue,
                                 @Schema(description = "City as recorded by the source") String city,
                                 MatchTeamDto team1, MatchTeamDto team2, TossDto toss, TeamRef winner,
                                 TeamRef superOverWinner, String result, Integer resultMargin, String method,
                                 boolean superOver, String resultText, PlayerRef playerOfMatch, TargetDto target,
                                 Integer scheduledOvers, OfficialsDto officials, List<InningsScoreDto> innings) {
    }

    public record ExtrasDto(int total, int wides, int noBalls, int byes, int legByes, int penalty) {
    }

    public record FielderDto(PlayerRef player, boolean substitute) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BattingLineDto(int position, PlayerRef player,
                                 @Schema(example = "c MS Dhoni b DL Chahar") String dismissal,
                                 String dismissalKind, PlayerRef bowler, List<FielderDto> fielders, int runs,
                                 int balls, int fours, int sixes, BigDecimal strikeRate, boolean out) {
    }

    @Schema(description = "maidens = complete overs bowled by one bowler conceding no runs to the bowler (byes and leg byes allowed)")
    public record BowlingLineDto(PlayerRef player, String overs, int maidens, int legalBalls, int runsConceded,
                                 int wickets, BigDecimal economy, int dotBalls, int wides, int noBalls,
                                 int foursConceded, int sixesConceded) {
    }

    public record FallOfWicketDto(int wicket, int score, @Schema(example = "7.3") String over, PlayerRef player) {
    }

    public record InningsScorecardDto(int inningsNumber, boolean superOver, TeamRef battingTeam, TeamRef bowlingTeam,
                                      int runs, int wickets, String overs, int legalBalls, BigDecimal runRate,
                                      Integer target, ExtrasDto extras, List<BattingLineDto> batting,
                                      List<PlayerRef> didNotBat, List<BowlingLineDto> bowling,
                                      List<FallOfWicketDto> fallOfWickets) {
    }

    public record ScorecardDto(MatchDetailDto match, List<InningsScorecardDto> innings) {
    }

    public record OverProgressionDto(@Schema(description = "1-based over") int over, int runs, int wickets, int extras,
                                     int legalBalls, int cumulativeRuns, int cumulativeWickets,
                                     @Schema(description = "Cumulative runs per over") BigDecimal runRate) {
    }

    public record InningsProgressionDto(int inningsNumber, boolean superOver, TeamRef team, Integer target,
                                        List<OverProgressionDto> overs) {
    }

    @Schema(description = "Per-over runs and wickets: the data behind worm (cumulative) and Manhattan (per over) charts")
    public record MatchProgressionDto(int matchId, List<InningsProgressionDto> innings) {
    }
}
