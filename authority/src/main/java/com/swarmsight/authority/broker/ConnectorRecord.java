package com.swarmsight.authority.broker;

import java.util.List;
import java.util.Map;

/**
 * What the agent receives: the masked fields and the per-field effects that
 * produced them. Allowed fields carry their value, masked fields carry a mask,
 * and denied fields are absent. This is the only public record from a fetch, and
 * the broker is the only thing that can build it, after the mirror has run.
 */
public record ConnectorRecord(
        String connector,
        String resourceScope,
        Map<String, Object> fields,
        List<FieldEffectEntry> fieldEffects) {
}
