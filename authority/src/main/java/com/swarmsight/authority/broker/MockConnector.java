package com.swarmsight.authority.broker;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * A stand-in case system that exercises the broker before a real connector
 * exists. It only accepts a CapabilityGrant the broker produced, and it returns
 * raw values plus the source's own permission per field. It never masks: the
 * broker's permission mirror does that. Swapping in SharePoint via Microsoft
 * Graph later is an adapter change here, nothing else.
 */
@Component
class MockConnector implements Connector {

    static final String NAME = "mock-case-system";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RawRecord fetch(CapabilityGrant grant) {
        Map<String, Object> fields = new LinkedHashMap<>();
        Map<String, FieldEffect> sourcePermissions = new LinkedHashMap<>();

        if ("tenancy_record".equals(grant.resourceScope())) {
            put(fields, sourcePermissions, "applicant_name", "Ms A. Adeyemi", FieldEffect.ALLOW);
            put(fields, sourcePermissions, "income", 18400, FieldEffect.ALLOW);
            put(fields, sourcePermissions, "tenancy_status", "confirmed", FieldEffect.ALLOW);
            put(fields, sourcePermissions, "national_insurance", "QQ123456C", FieldEffect.ALLOW);
            put(fields, sourcePermissions, "medical_notes", "Disability, mobility needs", FieldEffect.ALLOW);
        } else {
            put(fields, sourcePermissions, "note", "no record for scope " + grant.resourceScope(), FieldEffect.ALLOW);
        }
        return new RawRecord(grant.connector(), grant.resourceScope(), fields, sourcePermissions);
    }

    private void put(Map<String, Object> fields, Map<String, FieldEffect> perms,
            String field, Object value, FieldEffect sourcePermission) {
        fields.put(field, value);
        perms.put(field, sourcePermission);
    }
}
