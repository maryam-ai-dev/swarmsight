package com.swarmsight.authority.decision;

/**
 * Stable machine-readable reasons a Verdict reached its effect. The plain
 * review brief is for humans; the reason code is for code and audit.
 */
public final class ReasonCode {

    public static final String CLEAR = "CLEAR";
    public static final String UNKNOWN_ACTION = "UNKNOWN_ACTION";
    public static final String POLICY_UNRESOLVABLE = "POLICY_UNRESOLVABLE";
    public static final String CERTIFICATE_INVALID = "CERTIFICATE_INVALID";
    public static final String CERTIFICATE_NOT_ACTIVE = "CERTIFICATE_NOT_ACTIVE";
    public static final String CERTIFICATE_MISSING = "CERTIFICATE_MISSING";
    public static final String CERTIFICATE_UNREADABLE = "CERTIFICATE_UNREADABLE";
    public static final String ACTION_NOT_CERTIFIED = "ACTION_NOT_CERTIFIED";
    public static final String REQUIRED_INPUT_ABSENT = "REQUIRED_INPUT_ABSENT";
    public static final String EVICTION_RISK_DEPENDENTS = "EVICTION_RISK_DEPENDENTS";
    public static final String EVIDENCE_MISSING = "EVIDENCE_MISSING";
    public static final String AUTONOMY_CEILING_EXCEEDED = "AUTONOMY_CEILING_EXCEEDED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ReasonCode() {
    }
}
