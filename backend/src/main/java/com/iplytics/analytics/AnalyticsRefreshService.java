package com.iplytics.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iplytics.analytics.AnalyticsRefreshReport.Check;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rebuilds the analytics layer from core: refreshes the materialized views in a fixed order inside one
 * transaction, reconciles them against core, and commits only if every check passes. Refreshes use
 * {@code CONCURRENTLY}, so readers keep seeing the previous content until commit. Deterministic: the same core
 * data always produces the same analytics. Call it after every staging -> core transformation.
 */
@Service
public class AnalyticsRefreshService {

    /** Refresh order; the plain views (innings_context, team_match_result) need no refresh. */
    static final List<String> MATERIALIZED_VIEWS = List.of(
            "analytics.innings_summary", "analytics.batting_innings", "analytics.bowling_innings");

    static final List<String> ANALYTICS_OBJECTS = List.of(
            "analytics.innings_summary", "analytics.batting_innings", "analytics.bowling_innings",
            "analytics.team_match_result");

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRefreshService.class);
    private static final int MAX_ERROR_LENGTH = 2000;

    private final JdbcTemplate jdbc;
    private final AnalyticsReconciler reconciler;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;

    public AnalyticsRefreshService(JdbcTemplate jdbc, DataSource dataSource, AnalyticsReconciler reconciler,
                                   ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.reconciler = reconciler;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public AnalyticsRefreshReport refresh() {
        long started = System.nanoTime();
        Long transformRunId = jdbc.queryForObject(
                "select max(id) from core.transform_run where status = 'COMPLETED'", Long.class);
        Long runId = jdbc.queryForObject(
                "insert into analytics.refresh_run (status, transform_run_id) values ('RUNNING', ?) returning id",
                Long.class, transformRunId);
        long refreshRunId = Objects.requireNonNull(runId);
        try {
            return Objects.requireNonNull(transaction.execute(status -> {
                AnalyticsRefreshReport report = refreshInTransaction(refreshRunId, transformRunId, started);
                jdbc.update("""
                        update analytics.refresh_run set status = 'COMPLETED', completed_at = now(),
                               summary = cast(? as jsonb) where id = ?""", json(report), refreshRunId);
                return report;
            }));
        } catch (AnalyticsRefreshException e) {
            fail(refreshRunId, e.getMessage(), json(Map.of("checks", e.checks())));
            throw e;
        } catch (RuntimeException e) {
            log.error("Analytics refresh failed unexpectedly", e);
            fail(refreshRunId, "Unexpected error: " + e, null);
            throw new AnalyticsRefreshException("Analytics refresh failed: " + e.getMessage(), e);
        }
    }

    private AnalyticsRefreshReport refreshInTransaction(long refreshRunId, Long transformRunId, long started) {
        jdbc.queryForList("select pg_advisory_xact_lock(7101202)"); // one refresh at a time
        Map<String, Long> refreshMillis = new LinkedHashMap<>();
        for (String view : MATERIALIZED_VIEWS) {
            long viewStarted = System.nanoTime();
            jdbc.execute("refresh materialized view concurrently " + view);
            refreshMillis.put(view, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - viewStarted));
        }
        List<Check> checks = reconciler.check(jdbc);
        if (checks.stream().anyMatch(c -> !c.passed())) {
            throw new AnalyticsRefreshException("Analytics reconciliation failed; the refresh was rolled back", checks);
        }
        Map<String, Long> rowCounts = new LinkedHashMap<>();
        for (String object : ANALYTICS_OBJECTS) {
            rowCounts.put(object, jdbc.queryForObject("select count(*) from " + object, Long.class));
        }
        return new AnalyticsRefreshReport(refreshRunId, transformRunId, rowCounts, refreshMillis, checks,
                reconciler.warnings(jdbc), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    private void fail(long refreshRunId, String error, String summaryJson) {
        String message = error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH - 3) + "...";
        jdbc.update("""
                update analytics.refresh_run set status = 'FAILED', completed_at = now(), error_summary = ?,
                       summary = cast(? as jsonb) where id = ? and status = 'RUNNING'""", message, summaryJson,
                refreshRunId);
    }

    private String json(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
