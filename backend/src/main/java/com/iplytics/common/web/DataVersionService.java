package com.iplytics.common.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * The version of the data served by the API: the latest COMPLETED staging->core transform and analytics refresh.
 * Data only changes when one of them completes, so this drives ETags (docs/api.md, "Caching"). Looked up at most
 * once per HTTP request.
 */
@Service
public class DataVersionService {

    private static final String REQUEST_ATTRIBUTE = DataVersionService.class.getName() + ".version";

    private final JdbcTemplate jdbc;

    public DataVersionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DataVersion current() {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null && request.getAttribute(REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
                instanceof DataVersion cached) {
            return cached;
        }
        DataVersion version = jdbc.queryForObject("""
                select (select max(id) from core.transform_run where status = 'COMPLETED') as transform_run,
                       (select max(id) from analytics.refresh_run where status = 'COMPLETED') as refresh_run""",
                (rs, n) -> new DataVersion((Long) rs.getObject("transform_run"), (Long) rs.getObject("refresh_run")));
        if (request != null) {
            request.setAttribute(REQUEST_ATTRIBUTE, version, RequestAttributes.SCOPE_REQUEST);
        }
        return version;
    }

    /** @param transformRunId latest completed core transform; @param refreshRunId latest completed analytics refresh */
    public record DataVersion(Long transformRunId, Long refreshRunId) {

        public String tag() {
            return "t" + (transformRunId == null ? 0 : transformRunId) + "-a" + (refreshRunId == null ? 0 : refreshRunId);
        }

        /** Weak: equal versions mean semantically equal content, not byte-identical bodies. */
        public String etag() {
            return "W/\"" + tag() + "\"";
        }
    }
}
