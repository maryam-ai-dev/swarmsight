package com.swarmsight.authority.broker;

/**
 * Proof that the broker validated a capability for this exact fetch. It is
 * package-private and its constructor can only be called inside the broker
 * package, by the broker, after validation. No code outside this package can
 * name a grant or fabricate one, so a connector cannot be reached without a
 * validated capability. This is what makes the broker non-bypassable.
 */
record CapabilityGrant(
        String capabilityId,
        String connector,
        String resourceScope,
        String caseRef,
        String action) {
}
