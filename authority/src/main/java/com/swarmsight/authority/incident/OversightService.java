package com.swarmsight.authority.incident;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Live oversight metrics for the head-of-department screen, read straight from
 * the ledger and the working stores.
 */
@Service
public class OversightService {

    private final JdbcTemplate jdbc;

    public OversightService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> metrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ledger_rows", count("SELECT count(*) FROM ledger_rows"));
        m.put("decisions", count("SELECT count(*) FROM ledger_rows WHERE intent = 'decision'"));
        m.put("holds", count("SELECT count(*) FROM ledger_rows WHERE intent = 'decision' "
                + "AND (payload::jsonb)->>'effect' = 'HOLD'"));
        m.put("blocks", count("SELECT count(*) FROM ledger_rows WHERE intent = 'decision' "
                + "AND (payload::jsonb)->>'effect' = 'BLOCK'"));
        m.put("allows", count("SELECT count(*) FROM ledger_rows WHERE intent = 'decision' "
                + "AND (payload::jsonb)->>'effect' = 'ALLOW'"));
        m.put("certificates", count("SELECT count(*) FROM certificates"));
        m.put("active_certificates", count("SELECT count(*) FROM certificates WHERE status = 'ACTIVE'"));
        m.put("suspended_certificates", count("SELECT count(*) FROM certificates WHERE status = 'SUSPENDED'"));
        m.put("deployments", count("SELECT count(*) FROM deployment_approvals"));
        m.put("incidents", count("SELECT count(*) FROM incidents"));
        m.put("revoked_capabilities", count("SELECT count(*) FROM capabilities WHERE revoked_at IS NOT NULL"));
        return m;
    }

    private long count(String sql) {
        Long c = jdbc.queryForObject(sql, Long.class);
        return c == null ? 0 : c;
    }
}
