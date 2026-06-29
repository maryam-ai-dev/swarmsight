package com.swarmsight.authority.workbench;

/**
 * A legal or policy source a change is drawn from, with enough to prove
 * provenance: where it came from, which version, and a hash of its content. The
 * worked example is the section 21 abolition.
 */
public record SourceDocument(
        String uri,
        String version,
        String contentHash,
        String title) {
}
