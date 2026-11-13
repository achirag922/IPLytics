package com.iplytics.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CricketPhaseTest {

    @ParameterizedTest(name = "0-indexed over {0} -> {1}")
    @CsvSource({"0, POWERPLAY", "5, POWERPLAY", "6, MIDDLE", "14, MIDDLE", "15, DEATH", "19, DEATH", "20, DEATH"})
    void classifiesZeroIndexedOvers(int overNumber, CricketPhase expected) {
        assertThat(CricketPhase.of(overNumber, false)).isEqualTo(expected);
    }

    @Test
    void superOverBallsNeverBelongToARegularPhase() {
        assertThat(CricketPhase.of(0, true)).isEqualTo(CricketPhase.SUPER_OVER);
        assertThat(CricketPhase.SUPER_OVER.sqlName()).isEqualTo("super_over");
        assertThat(CricketPhase.POWERPLAY.sqlName()).isEqualTo("powerplay");
    }
}
