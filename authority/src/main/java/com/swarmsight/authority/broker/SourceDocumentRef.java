package com.swarmsight.authority.broker;

/**
 * A reference to the source document a fetch read: a stable id, a version
 * (eTag/content hash that changes when the document changes), and a human name.
 * Carried from the connector through the masked record and recorded on the
 * source_fetch ledger row, so each case traces to the exact document and version
 * it was built from, and a changed version is detectable.
 */
public record SourceDocumentRef(String id, String version, String name) {
}
