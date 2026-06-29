package com.swarmsight.authority.broker;

import java.util.Map;

/**
 * What a connector returns for a fetch: the connector it came from, the resource
 * scope that was granted, and the fields. The permission mirror and masking in
 * Sprint 5 act on these fields before they reach the agent.
 */
public record ConnectorRecord(
        String connector,
        String resourceScope,
        Map<String, Object> fields) {
}
