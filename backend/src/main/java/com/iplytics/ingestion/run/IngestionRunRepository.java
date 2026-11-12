package com.iplytics.ingestion.run;

import com.iplytics.ingestion.schema.Dataset;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Data access for {@code core.ingestion_run}. Each call joins the caller's transaction if there is one. */
@Repository
public class IngestionRunRepository {

    private final JdbcTemplate jdbc;

    public IngestionRunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return the run that already loaded a file with this hash, if any */
    public Optional<Long> findCompletedRunId(String fileHash) {
        List<Long> ids = jdbc.queryForList(
                "select id from core.ingestion_run where file_hash = ? and status = 'COMPLETED'",
                Long.class, fileHash);
        return ids.stream().findFirst();
    }

    /**
     * Records a new RUNNING run.
     *
     * @return the run id, or empty when the same file is already RUNNING or COMPLETED (enforced by a unique index)
     */
    public Optional<Long> start(Dataset dataset, String fileName, String fileHash, long fileSizeBytes) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    insert into core.ingestion_run (dataset, file_name, file_hash, file_size_bytes, status)
                    values (?, ?, ?, ?, 'RUNNING')
                    returning id""", Long.class, dataset.name(), fileName, fileHash, fileSizeBytes));
        } catch (DuplicateKeyException e) {
            return Optional.empty();
        }
    }

    public void complete(long runId, int rowsRead, int rowsLoaded, int rowsRejected) {
        int updated = jdbc.update("""
                update core.ingestion_run
                   set status = 'COMPLETED', rows_read = ?, rows_loaded = ?, rows_rejected = ?, completed_at = now()
                 where id = ? and status = 'RUNNING'""", rowsRead, rowsLoaded, rowsRejected, runId);
        requireRunning(runId, updated);
    }

    /** Marks a run FAILED. Nothing is loaded by a failed run, so rows_loaded is always 0. */
    public void fail(long runId, String errorSummary, int rowsRead, int rowsRejected) {
        int updated = jdbc.update("""
                update core.ingestion_run
                   set status = 'FAILED', rows_read = ?, rows_loaded = 0, rows_rejected = ?,
                       error_summary = ?, completed_at = now()
                 where id = ? and status = 'RUNNING'""", rowsRead, rowsRejected, errorSummary, runId);
        requireRunning(runId, updated);
    }

    /** Fails RUNNING runs older than {@code timeout}, e.g. left behind by a killed process. */
    public int failStaleRuns(Duration timeout) {
        return jdbc.update("""
                update core.ingestion_run
                   set status = 'FAILED', rows_loaded = 0, completed_at = now(),
                       error_summary = 'Abandoned: still RUNNING after ' || ? || ' seconds'
                 where status = 'RUNNING' and started_at < now() - make_interval(secs => ?)""",
                timeout.toSeconds(), (double) timeout.toSeconds());
    }

    public Optional<IngestionRun> findById(long runId) {
        return jdbc.query("select * from core.ingestion_run where id = ?", IngestionRunRepository::map, runId)
                .stream().findFirst();
    }

    private static void requireRunning(long runId, int updated) {
        if (updated != 1) {
            throw new IllegalStateException("Ingestion run " + runId + " is not RUNNING");
        }
    }

    private static IngestionRun map(ResultSet rs, int rowNum) throws SQLException {
        return new IngestionRun(
                rs.getLong("id"),
                Dataset.valueOf(rs.getString("dataset")),
                rs.getString("file_name"),
                rs.getString("file_hash"),
                rs.getLong("file_size_bytes"),
                RunStatus.valueOf(rs.getString("status")),
                rs.getInt("rows_read"),
                rs.getInt("rows_loaded"),
                rs.getInt("rows_rejected"),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class),
                rs.getString("error_summary"));
    }
}
