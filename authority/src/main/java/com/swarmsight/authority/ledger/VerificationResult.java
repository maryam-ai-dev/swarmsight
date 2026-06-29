package com.swarmsight.authority.ledger;

/**
 * The outcome of verifying a hash chain. On a break, brokenAtSeq names the row
 * where verification first failed and message says how.
 */
public record VerificationResult(
        boolean ok,
        int checkedRows,
        Long brokenAtSeq,
        String message) {

    public static VerificationResult intact(int checkedRows) {
        return new VerificationResult(true, checkedRows, null,
                "Chain intact: " + checkedRows + " rows verified, no breaks.");
    }

    public static VerificationResult broken(int checkedRows, long seq, String message) {
        return new VerificationResult(false, checkedRows, seq, message);
    }
}
