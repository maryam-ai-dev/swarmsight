package com.swarmsight.authority.broker;

/**
 * Thrown when the broker rejects a fetch. The reason code names why, so callers
 * and tests can assert the exact failure mode. Every reason is a refusal: the
 * broker fails closed.
 */
public class BrokerException extends RuntimeException {

    public enum Reason {
        NO_CAPABILITY,
        EXPIRED,
        REVOKED,
        AGENT_SUSPENDED,
        EXCEEDS_VERDICT,
        UNKNOWN_CONNECTOR
    }

    private final Reason reason;

    public BrokerException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
