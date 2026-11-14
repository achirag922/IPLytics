package com.iplytics.team.api;

import com.iplytics.analytics.leaderboard.api.LeaderboardDtos.BattingLeaderDto;
import com.iplytics.analytics.leaderboard.api.LeaderboardDtos.BowlingLeaderDto;
import com.iplytics.common.web.Refs.TeamRef;
import com.iplytics.common.web.Refs.VenueRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** Franchise resources. Records exclude super overs from scores; a super-over win counts as a win. */
public final class TeamDtos {

    private TeamDtos() {
    }

    public record TeamNameDto(@Schema(example = "Delhi Daredevils") String name, int firstSeason,
                              @Schema(description = "null while the name is still in use") Integer lastSeason) {
    }

    public record TeamDto(@Schema(example = "3") int id, @Schema(example = "DC") String code,
                          @Schema(example = "Delhi Capitals") String name, boolean active,
                          @Schema(example = "Delhi") String homeCity, Integer firstSeason, Integer lastSeason,
                          long matches, List<TeamNameDto> names,
                          @Schema(description = "All-time record") TeamRecordDto record,
                          @Schema(description = "Record in the last season the franchise played") TeamSeasonRecordDto latestSeason) {

        public TeamDto withRecords(TeamRecordDto allTime, TeamSeasonRecordDto latest) {
            return new TeamDto(id, code, name, active, homeCity, firstSeason, lastSeason, matches, names, allTime, latest);
        }
    }

    public record TeamSeasonRecordDto(int season, TeamRecordDto record) {
    }

    @Schema(description = "winPercentage = 100 x wins / (played - no results); ties won in a super over count as wins")
    public record TeamRecordDto(long played, long won, long lost, long tied, long noResult, BigDecimal winPercentage) {
    }

    @Schema(description = "averageScore = runs per innings batted; runRate = 6 x runs / legal balls; super overs excluded")
    public record TeamStatsDto(TeamRecordDto overall, TeamRecordDto battingFirst, TeamRecordDto chasing,
                               TeamRecordDto home, TeamRecordDto away, long runsFor, long inningsBatted,
                               BigDecimal averageScore, long runsAgainst, long inningsBowled,
                               BigDecimal averageConceded, BigDecimal runRate, BigDecimal opponentRunRate,
                               @Schema(description = "Highest regular-innings total") Integer highestScore) {
    }

    public record TeamVenueStatsDto(VenueRef venue, TeamStatsDto stats) {
    }

    public record TeamOpponentStatsDto(TeamRef opponent, TeamStatsDto stats) {
    }

    public record TeamSeasonStatsDto(int season, TeamStatsDto stats) {
    }

    public record TopPerformersDto(List<BattingLeaderDto> batting, List<BowlingLeaderDto> bowling) {
    }
}
