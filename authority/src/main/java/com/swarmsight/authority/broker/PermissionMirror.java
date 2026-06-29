package com.swarmsight.authority.broker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * Applies the permission mirror to a raw record: per field, the outcome is the
 * intersection of the source's own permission and the sensitivity policy, and
 * masking is applied before the result leaves the boundary. Fields are processed
 * in name order so the field_effects are deterministic on the ledger.
 */
@Component
public class PermissionMirror {

    /** What replaces a masked value: the agent learns the field exists, not its value. */
    static final String MASK_MARKER = "XXXXXXXXX";

    private final SensitivityPolicy sensitivityPolicy;

    public PermissionMirror(SensitivityPolicy sensitivityPolicy) {
        this.sensitivityPolicy = sensitivityPolicy;
    }

    /** The masked fields the agent receives, and the per-field effects to ledger. */
    public record Mirrored(Map<String, Object> maskedFields, List<FieldEffectEntry> fieldEffects) {
    }

    public Mirrored apply(RawRecord raw) {
        Map<String, Object> masked = new LinkedHashMap<>();
        List<FieldEffectEntry> effects = new ArrayList<>();

        for (String field : new TreeSet<>(raw.fields().keySet())) {
            FieldEffect source = raw.sourcePermissions().getOrDefault(field, FieldEffect.DENY);
            FieldEffect policy = sensitivityPolicy.effectFor(field);
            FieldEffect outcome = FieldEffect.intersect(source, policy);
            effects.add(new FieldEffectEntry(field, source, policy, outcome));

            switch (outcome) {
                case ALLOW -> masked.put(field, raw.fields().get(field));
                case MASK -> masked.put(field, MASK_MARKER);
                case DENY -> {
                    // Removed entirely: the agent never knows the field was there.
                }
            }
        }
        return new Mirrored(masked, effects);
    }
}
