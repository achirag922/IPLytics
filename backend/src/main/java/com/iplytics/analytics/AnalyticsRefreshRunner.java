package com.iplytics.analytics;

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
 * {@code java -jar iplytics.jar --spring.profiles.active=analytics}. Combine with the earlier steps as
 * {@code --spring.profiles.active=ingest,transform,analytics}; it runs last.
 */
@Component
@Profile(AnalyticsRefreshRunner.PROFILE)
@Order(200) // after IngestionRunner and TransformRunner
public class AnalyticsRefreshRunner implements ApplicationRunner, ExitCodeGenerator {

    public static final String PROFILE = "analytics";

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRefreshRunner.class);

    private final AnalyticsRefreshService refreshService;
    private int exitCode;

    public AnalyticsRefreshRunner(AnalyticsRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Refreshing analytics from core");
        try {
            AnalyticsRefreshReport r = refreshService.refresh();
            log.info("Analytics refresh #{} COMPLETED in {} ms (built from transform run #{})", r.refreshRunId(),
                    r.elapsedMillis(), r.transformRunId());
            r.refreshMillis().forEach((view, ms) -> log.info("  refreshed {} in {} ms", view, ms));
            r.rowCounts().forEach((object, rows) -> log.info("  {} {} rows", "%-30s".formatted(object),
                    "%,9d".formatted(rows)));
            log.info("Checks: {}/{} passed", r.checks().stream().filter(AnalyticsRefreshReport.Check::passed).count(),
                    r.checks().size());
            r.checks().forEach(c -> log.info("  [{}] {} - {}", c.passed() ? "PASS" : "FAIL", c.name(), c.detail()));
            r.warnings().forEach(w -> log.warn("Warning: {}", w));
            exitCode = 0;
        } catch (AnalyticsRefreshException e) {
            log.error("Analytics refresh FAILED: {}", e.getMessage());
            e.failedChecks().forEach(c -> log.error("  {}: {}", c.name(), c.detail()));
            exitCode = 1;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
