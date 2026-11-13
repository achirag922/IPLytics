package com.iplytics.analytics;

import com.iplytics.analytics.AnalyticsRefreshReport.Check;
import java.util.List;

/** The refresh was rejected and rolled back; the previous analytics content is still in place. */
public class AnalyticsRefreshException extends RuntimeException {

    private final transient List<Check> checks;

    public AnalyticsRefreshException(String message, List<Check> checks) {
        super(message);
        this.checks = List.copyOf(checks);
    }

    public AnalyticsRefreshException(String message, Throwable cause) {
        super(message, cause);
        this.checks = List.of();
    }

    public List<Check> failedChecks() {
        return checks.stream().filter(c -> !c.passed()).toList();
    }

    public List<Check> checks() {
        return checks;
    }
}
