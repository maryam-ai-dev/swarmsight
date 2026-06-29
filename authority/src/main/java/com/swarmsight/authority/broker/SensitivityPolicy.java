package com.swarmsight.authority.broker;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The department field sensitivity policy at the agent's clearance, for the
 * housing example. Hardcoded for Sprint 5, like the verdict policy was in Sprint
 * 1; it can become versioned data later. Any field not named here fails closed
 * to DENY, so the agent only ever sees a field that is explicitly allowed.
 */
@Component
public class SensitivityPolicy {

    private static final Map<String, FieldEffect> MAP = Map.of(
            "applicant_name", FieldEffect.ALLOW,
            "income", FieldEffect.ALLOW,
            "tenancy_status", FieldEffect.ALLOW,
            "national_insurance", FieldEffect.MASK,
            "medical_notes", FieldEffect.DENY);

    public FieldEffect effectFor(String field) {
        return MAP.getOrDefault(field, FieldEffect.DENY);
    }
}
