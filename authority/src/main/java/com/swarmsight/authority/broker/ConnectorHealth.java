package com.swarmsight.authority.broker;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Reports the health of the registered connectors. The go-live gate reads this.
 * The mock connector is always healthy; real adapters will report their own
 * status here. Public, so the gate can read it, while the Connector type itself
 * stays package-private.
 */
@Component
public class ConnectorHealth {

    private final List<Connector> connectors;

    public ConnectorHealth(List<Connector> connectors) {
        this.connectors = connectors;
    }

    public Map<String, Boolean> statuses() {
        Map<String, Boolean> statuses = new TreeMap<>();
        for (Connector connector : connectors) {
            statuses.put(connector.name(), true);
        }
        return statuses;
    }

    public boolean allHealthy() {
        return statuses().values().stream().allMatch(Boolean::booleanValue);
    }
}
