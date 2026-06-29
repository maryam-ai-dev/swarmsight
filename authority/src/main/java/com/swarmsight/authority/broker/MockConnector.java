package com.swarmsight.authority.broker;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * A stand-in source that exercises the broker before any real connector exists.
 * It only accepts a CapabilityGrant, which only the broker can produce, so it
 * cannot be fetched from without a validated capability. It returns canned
 * records keyed by resource scope.
 */
@Component
class MockConnector implements Connector {

    static final String NAME = "mock-case-system";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ConnectorRecord fetch(CapabilityGrant grant) {
        Map<String, Object> fields = switch (grant.resourceScope()) {
            case "tenancy_record" -> Map.of(
                    "tenancy_status", "confirmed",
                    "national_insurance", "QQ123456C",
                    "address", "12 Example Road");
            case "income_assessment" -> Map.of(
                    "income", 18400,
                    "assessment_ref", "assessment.pdf p.3");
            default -> Map.of("note", "no record for scope " + grant.resourceScope());
        };
        return new ConnectorRecord(grant.connector(), grant.resourceScope(), fields);
    }
}
