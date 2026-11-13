package com.iplytics.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CricketMetricsTest {

    @Test
    void computesRatiosFromRawTotalsRoundedHalfUpToTwoDecimals() {
        assertThat(CricketMetrics.strikeRate(13, 6)).isEqualByComparingTo("216.67");
        assertThat(CricketMetrics.strikeRate(1, 3)).isEqualByComparingTo("33.33");
        assertThat(CricketMetrics.economy(16, 8)).isEqualByComparingTo("12.00");
        assertThat(CricketMetrics.runRate(30, 14)).isEqualByComparingTo("12.86");
        assertThat(CricketMetrics.battingAverage(1, 8)).isEqualByComparingTo("0.13"); // 0.125 rounds half-up
        assertThat(CricketMetrics.bowlingAverage(16, 1)).isEqualByComparingTo("16.00");
        assertThat(CricketMetrics.bowlingStrikeRate(8, 1)).isEqualByComparingTo("8.00");
        assertThat(CricketMetrics.dotBallPercentage(2, 6)).isEqualByComparingTo("33.33");
        assertThat(CricketMetrics.boundaryPercentage(2, 6)).isEqualByComparingTo("33.33");
        assertThat(CricketMetrics.ballsPerBoundary(6, 2)).isEqualByComparingTo("3.00");
        assertThat(CricketMetrics.winPercentage(2, 3, 1)).isEqualByComparingTo("100.00");
        assertThat(CricketMetrics.strikeRate(13, 6).scale()).isEqualTo(2);
    }

    @ParameterizedTest(name = "undefined ratio: {0}")
    @CsvSource({"average without a dismissal", "strike rate without a ball", "economy without a legal ball",
            "win percentage when every match had no result"})
    void undefinedRatiosAreNull(String description) {
        BigDecimal value = switch (description) {
            case "average without a dismissal" -> CricketMetrics.battingAverage(50, 0);
            case "strike rate without a ball" -> CricketMetrics.strikeRate(0, 0);
            case "economy without a legal ball" -> CricketMetrics.economy(5, 0);
            default -> CricketMetrics.winPercentage(0, 2, 2);
        };
        assertThat(value).isNull();
    }

    @Test
    void oversNotationCountsLegalBalls() {
        assertThat(CricketMetrics.overs(22)).isEqualTo("3.4");
        assertThat(CricketMetrics.overs(120)).isEqualTo("20.0");
        assertThat(CricketMetrics.overs(0)).isEqualTo("0.0");
    }
}
