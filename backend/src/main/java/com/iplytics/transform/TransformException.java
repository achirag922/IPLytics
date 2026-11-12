package com.iplytics.transform;

import com.iplytics.transform.TransformReport.CheckResult;
import java.util.List;

/** The transformation was rejected; nothing was written to core. */
public class TransformException extends RuntimeException {

    private final transient List<CheckResult> checks;

    public TransformException(String message, List<CheckResult> checks) {
        super(message);
        this.checks = List.copyOf(checks);
    }

    public TransformException(String message, Throwable cause) {
        super(message, cause);
        this.checks = List.of();
    }

    public List<CheckResult> checks() {
        return checks;
    }

    public List<CheckResult> failedChecks() {
        return checks.stream().filter(c -> !c.passed()).toList();
    }
}
