package com.swarmsight.authority.incident;

import com.swarmsight.authority.ledger.LedgerRow;
import java.util.List;

/**
 * An incident's own audit pack: the incident record and the ledger rows for its
 * containment, so the whole response is provable.
 */
public record IncidentAuditPack(
        Incident incident,
        List<LedgerRow> ledgerRows) {
}
