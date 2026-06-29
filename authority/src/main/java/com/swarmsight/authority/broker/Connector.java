package com.swarmsight.authority.broker;

/**
 * A source the broker can fetch from. Package-private on purpose: only the broker
 * package can name this type or call fetch, and fetch requires a CapabilityGrant
 * that only the broker can produce. There is no connector access that does not
 * pass through the broker's capability check.
 */
interface Connector {

    String name();

    /**
     * Fetch raw values for a granted scope. The broker runs the permission
     * mirror over this before anything reaches the agent; the connector itself
     * never masks.
     */
    RawRecord fetch(CapabilityGrant grant);
}
