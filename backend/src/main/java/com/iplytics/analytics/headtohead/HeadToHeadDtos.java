package com.iplytics.analytics.headtohead;

import com.iplytics.common.web.Refs.TeamRef;
import com.iplytics.match.api.MatchDtos.MatchSummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public final class HeadToHeadDtos {

    private HeadToHeadDtos() {
    }

    @Schema(description = """
            One side of a rivalry. wins include ties decided by a super over; winsBattingFirst + winsChasing = wins
            (a super-over win is credited to the side's role in the regular innings). averageScore = runs per regular
            innings in these meetings.""")
    public record RivalSideDto(TeamRef team, long wins, long winsBattingFirst, long winsChasing,
                               BigDecimal averageScore) {
    }

    public record SeasonMeetingsDto(int season, long matches, long team1Wins, long team2Wins, long ties,
                                    long noResults) {
    }

    @Schema(description = "Meetings of two franchises; 'ties' counts tied matches whatever decided them")
    public record HeadToHeadDto(long matches, long ties, long noResults, RivalSideDto team1, RivalSideDto team2,
                                List<SeasonMeetingsDto> bySeason,
                                @Schema(description = "Up to five most recent meetings") List<MatchSummaryDto> recentMatches) {
    }
}
