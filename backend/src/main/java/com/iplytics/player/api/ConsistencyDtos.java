package com.iplytics.player.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iplytics.common.web.Refs.PlayerRef;
import com.iplytics.common.web.Refs.TeamRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Player consistency and form (docs/metrics.md section 10). */
public final class ConsistencyDtos {

    private ConsistencyDtos() {
    }

    public record InningsBandDto(@Schema(example = "30-49") String label, long innings) {
    }

    public record BattingFormDto(int matchId, LocalDate date, TeamRef opponent, int runs, int balls, boolean notOut,
                                 BigDecimal strikeRate) {
    }

    @Schema(description = """
            Spread of innings scores. standardDeviation is the sample standard deviation (null with fewer than two
            innings); coefficientOfVariation = 100 x standard deviation / mean score (lower is steadier). Percentages
            are of innings batted. recentInnings: the last 10, newest first.""")
    public record BattingConsistencyDto(long innings, BigDecimal average, BigDecimal medianScore,
                                        BigDecimal standardDeviation, BigDecimal coefficientOfVariation,
                                        BigDecimal thirtyPlusPercentage, BigDecimal fiftyPlusPercentage,
                                        BigDecimal underTenPercentage, List<InningsBandDto> scoreBands,
                                        List<BattingFormDto> recentInnings) {
    }

    public record BowlingFormDto(int matchId, LocalDate date, TeamRef opponent, String overs, int runsConceded,
                                 int wickets, BigDecimal economy) {
    }

    @Schema(description = """
            wicketInningsPercentage / multiWicketInningsPercentage: innings with 1+ / 2+ wickets, % of innings bowled.
            Economy spread and economicalInningsPercentage (economy under 8) use innings of at least one over.
            recentInnings: the last 10, newest first.""")
    public record BowlingConsistencyDto(long innings, BigDecimal wicketInningsPercentage,
                                        BigDecimal multiWicketInningsPercentage, BigDecimal medianWickets,
                                        BigDecimal economyStandardDeviation, BigDecimal economyCoefficientOfVariation,
                                        BigDecimal economicalInningsPercentage, List<BowlingFormDto> recentInnings) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlayerConsistencyDto(PlayerRef player, BattingConsistencyDto batting,
                                       BowlingConsistencyDto bowling) {
    }
}
