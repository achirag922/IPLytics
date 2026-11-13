package com.iplytics.transform;

import static com.iplytics.transform.TransformSql.counted;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iplytics.transform.TransformReport.CheckResult;
import com.iplytics.transform.TransformReport.TableChange;
import com.iplytics.transform.reference.ReferenceData;
import com.iplytics.transform.reference.ReferenceDataLoader;
import java.util.ArrayList;
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
 * Transforms the latest completed staging batches into the core schema in ONE transaction:
 * stage typed copies -> validate -> upsert reference data, players, matches, innings, deliveries, wickets and
 * playing lists -> prune rows no longer in the source -> reconcile. Any failed check rolls everything back, so
 * core is never partially updated. Reruns are no-ops (zero rows changed). See docs/adr/0010.
 */
@Service
public class CoreTransformService {

    private static final Logger log = LoggerFactory.getLogger(CoreTransformService.class);
    private static final int MAX_ERROR_LENGTH = 2000;

    private final JdbcTemplate jdbc;
    private final ReferenceDataLoader referenceLoader;
    private final Reconciler reconciler;
    private final TransformRunRepository runs;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;

    public CoreTransformService(JdbcTemplate jdbc, DataSource dataSource, ReferenceDataLoader referenceLoader,
                                Reconciler reconciler, TransformRunRepository runs, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.referenceLoader = referenceLoader;
        this.reconciler = reconciler;
        this.runs = runs;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public TransformReport transform() {
        long started = System.nanoTime();
        ReferenceData reference = referenceLoader.load();
        Map<String, Long> sourceRuns = latestStagingRuns();
        long runId = runs.start(sourceRuns);
        try {
            return Objects.requireNonNull(transaction.execute(status -> {
                TransformReport report = transformInTransaction(runId, sourceRuns, reference, started);
                runs.complete(runId, json(report));
                return report;
            }));
        } catch (TransformException e) {
            runs.fail(runId, truncate(e.getMessage()), json(Map.of("checks", e.checks())));
            throw e;
        } catch (RuntimeException e) {
            log.error("Transformation failed unexpectedly", e);
            runs.fail(runId, truncate("Unexpected error: " + e), null);
            throw new TransformException("Transformation failed: " + e.getMessage(), e);
        }
    }

    private TransformReport transformInTransaction(long runId, Map<String, Long> sourceRuns, ReferenceData reference,
                                                   long started) {
        jdbc.queryForList(TransformSql.LOCK); // one transformation at a time
        stage(sourceRuns, reference);

        List<CheckResult> checks = new ArrayList<>(reconciler.validate(jdbc));
        if (checks.stream().anyMatch(c -> !c.passed())) {
            throw new TransformException("Staging data failed validation; core was not changed", checks);
        }

        Map<String, TableChange> changes = new LinkedHashMap<>();
        TransformSql.CORE_TABLES.forEach(t -> changes.put(t, new TableChange(0, 0, 0)));

        deleted(changes, "match", TransformSql.DELETE_STALE_MATCHES);
        upserted(changes, "season", TransformSql.UPSERT_SEASONS);
        upserted(changes, "team", TransformSql.UPSERT_TEAMS);
        upserted(changes, "team_name_history", TransformSql.UPSERT_TEAM_NAMES);
        jdbc.execute(TransformSql.RESOLVE_VENUES);
        upserted(changes, "venue", TransformSql.UPSERT_VENUES);
        upserted(changes, "venue_alias", TransformSql.UPSERT_VENUE_ALIASES);
        upserted(changes, "player", TransformSql.UPSERT_PLAYERS);
        upserted(changes, "player", TransformSql.UPSERT_PLAYERS_WITHOUT_ID);
        jdbc.execute(TransformSql.PLAYER_MAP);
        TransformSql.PLAYER_MAP_INDEX.forEach(jdbc::execute);
        jdbc.execute(TransformSql.ALIASES);
        upserted(changes, "player_alias", TransformSql.UPSERT_PLAYER_ALIASES);

        upserted(changes, "match", TransformSql.UPSERT_MATCHES);
        upserted(changes, "innings", TransformSql.UPSERT_INNINGS);
        deleted(changes, "innings", TransformSql.DELETE_STALE_INNINGS);
        upserted(changes, "delivery", TransformSql.UPSERT_DELIVERIES);
        deleted(changes, "delivery", TransformSql.DELETE_STALE_DELIVERIES);
        TransformSql.ANALYZE_CORE.forEach(jdbc::execute);
        TransformSql.DELIVERY_KEYS.forEach(jdbc::execute);
        upserted(changes, "wicket", TransformSql.UPSERT_WICKETS);
        deleted(changes, "wicket", TransformSql.DELETE_STALE_WICKETS);
        upserted(changes, "wicket_fielder", TransformSql.UPSERT_WICKET_FIELDERS);
        deleted(changes, "wicket_fielder", TransformSql.DELETE_STALE_WICKET_FIELDERS);
        upserted(changes, "match_player", TransformSql.UPSERT_MATCH_PLAYERS);
        deleted(changes, "match_player", TransformSql.DELETE_STALE_MATCH_PLAYERS);

        deleted(changes, "player_alias", TransformSql.DELETE_STALE_PLAYER_ALIASES);
        deleted(changes, "player", TransformSql.DELETE_UNUSED_PLAYERS);
        deleted(changes, "team_name_history", TransformSql.DELETE_UNUSED_TEAM_NAMES);
        deleted(changes, "team", TransformSql.DELETE_UNUSED_TEAMS);
        deleted(changes, "venue_alias", TransformSql.DELETE_UNUSED_VENUE_ALIASES);
        deleted(changes, "venue", TransformSql.DELETE_UNUSED_VENUES);
        deleted(changes, "season", TransformSql.DELETE_UNUSED_SEASONS);

        checks.addAll(reconciler.reconcile(jdbc));
        if (checks.stream().anyMatch(c -> !c.passed())) {
            throw new TransformException("Reconciliation failed; all core changes were rolled back", checks);
        }
        List<String> warnings = reconciler.warnings(jdbc);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : TransformSql.CORE_TABLES) {
            counts.put(table, jdbc.queryForObject("select count(*) from core." + table, Long.class));
        }
        return new TransformReport(runId, sourceRuns, counts, changes, checks, warnings,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    private Map<String, Long> latestStagingRuns() {
        Map<String, Long> latest = new LinkedHashMap<>();
        jdbc.query(TransformSql.LATEST_RUNS, rs -> {
            latest.put(rs.getString("dataset"), rs.getLong("run_id"));
        });
        List<String> missing = List.of("MATCHES", "DELIVERIES", "MATCH_PLAYERS").stream()
                .filter(d -> !latest.containsKey(d)).toList();
        if (!missing.isEmpty()) {
            throw new TransformException("No completed ingestion run for " + missing
                    + ": run the ingest step (--spring.profiles.active=ingest) first", List.of());
        }
        Map<String, Long> ordered = new LinkedHashMap<>();
        for (String dataset : List.of("MATCHES", "DELIVERIES", "MATCH_PLAYERS")) {
            ordered.put(dataset, latest.get(dataset));
        }
        return ordered;
    }

    private void stage(Map<String, Long> sourceRuns, ReferenceData reference) {
        jdbc.execute(TransformSql.stageMatches(sourceRuns.get("MATCHES")));
        jdbc.execute(TransformSql.stageDeliveries(sourceRuns.get("DELIVERIES")));
        jdbc.execute(TransformSql.stageMatchPlayers(sourceRuns.get("MATCH_PLAYERS")));
        TransformSql.STAGE_DERIVED.forEach(jdbc::execute);

        TransformSql.CREATE_REFERENCE.forEach(jdbc::execute);
        jdbc.batchUpdate("insert into t_ref_team values (?, ?, ?, ?)", reference.franchises(), 100, (ps, f) -> {
            ps.setString(1, f.code());
            ps.setString(2, f.name());
            ps.setBoolean(3, f.active());
            ps.setString(4, f.homeCity());
        });
        jdbc.batchUpdate("insert into t_ref_team_name values (?, ?, ?, ?)", reference.teamNames(), 100, (ps, n) -> {
            ps.setString(1, n.name());
            ps.setString(2, n.teamCode());
            ps.setInt(3, n.firstSeason());
            ps.setObject(4, n.lastSeason(), java.sql.Types.SMALLINT);
        });
        jdbc.batchUpdate("insert into t_ref_venue values (?, ?, ?)", reference.venueAliases(), 100, (ps, v) -> {
            ps.setString(1, v.alias());
            ps.setString(2, v.venue());
            ps.setString(3, v.city());
        });
    }

    private void upserted(Map<String, TableChange> changes, String table, String sql) {
        long started = System.nanoTime();
        Map<String, Object> row = jdbc.queryForMap(sql);
        log.debug("upsert {} took {} ms", table, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        changes.merge(table, new TableChange(((Number) row.get("inserted")).longValue(),
                ((Number) row.get("updated")).longValue(), 0), TableChange::plus);
    }

    private void deleted(Map<String, TableChange> changes, String table, String deleteSql) {
        Long count = jdbc.queryForObject(counted(deleteSql), Long.class);
        changes.merge(table, new TableChange(0, 0, count == null ? 0 : count), TableChange::plus);
    }

    private String json(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String truncate(String message) {
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH - 3) + "...";
    }
}
