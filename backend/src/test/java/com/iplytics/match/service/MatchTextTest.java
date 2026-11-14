package com.iplytics.match.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.iplytics.match.repository.MatchRepository.FielderRow;
import com.iplytics.match.repository.MatchRepository.MatchRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchTextTest {

    private static final FielderRow DHONI = new FielderRow(1, 7, "MS Dhoni", false);
    private static final FielderRow SUB = new FielderRow(1, 8, "KM Jadhav", true);

    @Test
    void dismissalsReadAsOnAScorecard() {
        assertThat(MatchService.dismissalText(null, null, List.of())).isEqualTo("not out");
        assertThat(MatchService.dismissalText("bowled", "JJ Bumrah", List.of())).isEqualTo("b JJ Bumrah");
        assertThat(MatchService.dismissalText("lbw", "JJ Bumrah", List.of())).isEqualTo("lbw b JJ Bumrah");
        assertThat(MatchService.dismissalText("caught", "DL Chahar", List.of(DHONI))).isEqualTo("c MS Dhoni b DL Chahar");
        assertThat(MatchService.dismissalText("caught", "DL Chahar", List.of(SUB)))
                .isEqualTo("c sub (KM Jadhav) b DL Chahar");
        assertThat(MatchService.dismissalText("caught", "MS Dhoni", List.of(DHONI))).isEqualTo("c & b MS Dhoni");
        assertThat(MatchService.dismissalText("caught and bowled", "DL Chahar", List.of()))
                .isEqualTo("c & b DL Chahar");
        assertThat(MatchService.dismissalText("stumped", "Imran Tahir", List.of(DHONI)))
                .isEqualTo("st MS Dhoni b Imran Tahir");
        assertThat(MatchService.dismissalText("hit wicket", "DL Chahar", List.of())).isEqualTo("hit wicket b DL Chahar");
        assertThat(MatchService.dismissalText("run out", "DL Chahar", List.of(DHONI, SUB)))
                .isEqualTo("run out (MS Dhoni/sub (KM Jadhav))");
        assertThat(MatchService.dismissalText("run out", "DL Chahar", List.of())).isEqualTo("run out");
        assertThat(MatchService.dismissalText("retired hurt", "DL Chahar", List.of())).isEqualTo("retired hurt");
        assertThat(MatchService.dismissalText("obstructing the field", "X", List.of()))
                .isEqualTo("obstructing the field");
    }

    @Test
    void resultsReadNaturallyWithHistoricalNames() {
        assertThat(MatchService.resultText(match("runs", 1, 10, null, false))).isEqualTo("Delhi Daredevils won by 10 runs");
        assertThat(MatchService.resultText(match("runs", 2, 1, "D/L", false)))
                .isEqualTo("Mumbai Indians won by 1 run (D/L)");
        assertThat(MatchService.resultText(match("wickets", 2, 7, null, false))).isEqualTo("Mumbai Indians won by 7 wickets");
        assertThat(MatchService.resultText(match("tie", 1, null, null, true)))
                .isEqualTo("Match tied (Delhi Daredevils won the super over)");
        assertThat(MatchService.resultText(match("no result", null, null, null, false))).isEqualTo("No result");
    }

    private static MatchRow match(String result, Integer winnerId, Integer margin, String method, boolean superOver) {
        return new MatchRow(1, 1L, 2010, null, null, "League", 1, 1, "Mumbai", 1, "Delhi Daredevils", 2,
                "Mumbai Indians", null, null, winnerId, superOver ? winnerId : null, result, margin, method, superOver,
                null, null, null, null, 20, List.of(), List.of(), List.of(), List.of());
    }
}
