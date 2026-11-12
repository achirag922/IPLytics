package com.iplytics.transform;

import com.iplytics.transform.TransformReport.CheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * CLI entry point, active with the {@value #PROFILE} profile:
 * {@code java -jar iplytics.jar --spring.profiles.active=transform}. Can be combined with {@code ingest}
 * ({@code --spring.profiles.active=ingest,transform}); ingestion then runs first. Exit code 0 on success.
 */
@Component
@Profile(TransformRunner.PROFILE)
@Order(100) // after IngestionRunner when both profiles are active
public class TransformRunner implements ApplicationRunner, ExitCodeGenerator {

    public static final String PROFILE = "transform";

    private static final Logger log = LoggerFactory.getLogger(TransformRunner.class);

    private final CoreTransformService transformService;
    private int exitCode;

    public TransformRunner(CoreTransformService transformService) {
        this.transformService = transformService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Transforming staging -> core");
        try {
            TransformReport report = transformService.transform();
            print(report);
            exitCode = 0;
        } catch (TransformException e) {
            log.error("Transform FAILED: {}", e.getMessage());
            e.failedChecks().forEach(c -> log.error("  [{}] {}: {}", c.phase(), c.name(), c.detail()));
            exitCode = 1;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    private static void print(TransformReport r) {
        log.info("Transform run #{} COMPLETED in {} ms using staging runs {}", r.transformRunId(), r.elapsedMillis(),
                r.sourceRuns());
        log.info("  {}  {}  {}  {}  {}", "%-18s".formatted("core table"), "%9s".formatted("rows"),
                "%9s".formatted("inserted"), "%8s".formatted("updated"), "%8s".formatted("deleted"));
        r.coreRowCounts().forEach((table, rows) -> {
            TransformReport.TableChange c = r.changes().get(table);
            log.info("  {}  {}  {}  {}  {}", "%-18s".formatted(table), "%,9d".formatted(rows),
                    "%,9d".formatted(c.inserted()), "%,8d".formatted(c.updated()), "%,8d".formatted(c.deleted()));
        });
        long passed = r.checks().stream().filter(CheckResult::passed).count();
        log.info("Checks: {}/{} passed", passed, r.checks().size());
        r.checks().forEach(c -> log.info("  [{}] {} - {}", c.passed() ? "PASS" : "FAIL", c.name(), c.detail()));
        r.warnings().forEach(w -> log.warn("Warning: {}", w));
        log.info(r.totalChanges() == 0 ? "Core was already up to date (no rows changed)."
                : "Core updated: %,d row changes.".formatted(r.totalChanges()));
    }
}
