package com.iplytics.transform;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Data access for {@code core.transform_run}. */
@Repository
public class TransformRunRepository {

    private final JdbcTemplate jdbc;

    public TransformRunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long start(Map<String, Long> sourceRuns) {
        return jdbc.queryForObject("""
                insert into core.transform_run (status, matches_run_id, deliveries_run_id, match_players_run_id)
                values ('RUNNING', ?, ?, ?) returning id""", Long.class,
                sourceRuns.get("MATCHES"), sourceRuns.get("DELIVERIES"), sourceRuns.get("MATCH_PLAYERS"));
    }

    public void complete(long runId, String summaryJson) {
        jdbc.update("""
                update core.transform_run set status = 'COMPLETED', completed_at = now(), summary = cast(? as jsonb)
                 where id = ? and status = 'RUNNING'""", summaryJson, runId);
    }

    public void fail(long runId, String errorSummary, String summaryJson) {
        jdbc.update("""
                update core.transform_run set status = 'FAILED', completed_at = now(), error_summary = ?,
                       summary = cast(? as jsonb)
                 where id = ? and status = 'RUNNING'""", errorSummary, summaryJson, runId);
    }
}
