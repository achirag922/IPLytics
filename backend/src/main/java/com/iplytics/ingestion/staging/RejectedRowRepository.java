package com.iplytics.ingestion.staging;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RejectedRowRepository {

    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbc;

    public RejectedRowRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertAll(long runId, String sourceFile, List<RejectedRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
                insert into staging.rejected_row
                    (ingestion_run_id, source_file, source_row_number, reason_code, reason, raw_data)
                values (?, ?, ?, ?, ?, cast(? as jsonb))""", rows, BATCH_SIZE, (ps, row) -> {
            ps.setLong(1, runId);
            ps.setString(2, sourceFile);
            ps.setInt(3, row.sourceRowNumber());
            ps.setString(4, row.reasonCode().name());
            ps.setString(5, row.reason());
            ps.setString(6, row.rawDataJson());
        });
    }
}
