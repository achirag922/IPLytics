package com.iplytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.iplytics.support.AbstractIntegrationTest;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void allMigrationsApplyCleanly() {
        assertThat(flyway.info().applied())
                .isNotEmpty()
                .allSatisfy(m -> assertThat(m.getState().isFailed()).isFalse());
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void layeredSchemasExist() {
        List<String> schemas = jdbc.queryForList(
                "select schema_name from information_schema.schemata"
                        + " where schema_name in ('staging', 'core', 'analytics')",
                String.class);
        assertThat(schemas).containsExactlyInAnyOrder("staging", "core", "analytics");
    }

    @Test
    void stagingAndIngestionAuditTablesExist() {
        List<String> tables = jdbc.queryForList(
                "select table_schema || '.' || table_name from information_schema.tables"
                        + " where table_schema in ('staging', 'core') and table_type = 'BASE TABLE'",
                String.class);
        assertThat(tables).contains(
                "core.ingestion_run", "staging.matches", "staging.deliveries", "staging.match_players",
                "staging.rejected_row");
    }

    @Test
    void runsAgainstPostgres16() {
        assertThat(jdbc.queryForObject("show server_version", String.class)).startsWith("16");
    }
}
