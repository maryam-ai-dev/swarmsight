package com.swarmsight.authority.broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/**
 * Proves the broker cannot be bypassed by construction. A connector can only be
 * reached with a CapabilityGrant, and a grant can only be made inside this
 * package by the broker after it validates. No code outside the broker package
 * can name a grant or a connector, so there is no path that skips the check.
 */
class BrokerNonBypassTest {

    @Test
    void capabilityGrantIsNotPublicAndHasNoPublicConstructor() {
        assertThat(Modifier.isPublic(CapabilityGrant.class.getModifiers()))
                .as("CapabilityGrant must not be public").isFalse();
        for (Constructor<?> ctor : CapabilityGrant.class.getDeclaredConstructors()) {
            assertThat(Modifier.isPublic(ctor.getModifiers()))
                    .as("CapabilityGrant must have no public constructor").isFalse();
        }
    }

    @Test
    void connectorTypeIsNotPublic() {
        assertThat(Modifier.isPublic(Connector.class.getModifiers()))
                .as("Connector must not be public").isFalse();
    }

    @Test
    void rawRecordIsNotPublicSoUnmaskedValuesCannotLeaveTheBroker() {
        // Raw source values are carried only by RawRecord, which is package-
        // private, so no fetch can return unmasked data without the mirror.
        assertThat(Modifier.isPublic(RawRecord.class.getModifiers()))
                .as("RawRecord must not be public").isFalse();
    }

    @Test
    void theOnlyWayToCallAConnectorIsWithAGrant() throws Exception {
        Method fetch = Connector.class.getMethod("fetch", CapabilityGrant.class);
        assertThat(fetch.getParameterTypes()).containsExactly(CapabilityGrant.class);
        // The mock connector exposes no other public fetch entry point.
        long publicFetchMethods = java.util.Arrays.stream(MockConnector.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("fetch"))
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .count();
        assertThat(publicFetchMethods).isEqualTo(1);
    }
}
