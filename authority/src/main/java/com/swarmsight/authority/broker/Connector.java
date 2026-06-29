package com.swarmsight.authority.broker;

/**
 * A source the broker can fetch from. Package-private on purpose: only the broker
 * package can name this type or call fetch, and fetch requires a CapabilityGrant
 * that only the broker can produce. There is no connector access that does not
 * pass through the broker's capability check.
 */
interface Connector {

    String name();

    ConnectorRecord fetch(CapabilityGrant grant);
}
