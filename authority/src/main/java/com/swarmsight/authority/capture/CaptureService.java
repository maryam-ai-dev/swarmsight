package com.swarmsight.authority.capture;

import com.swarmsight.authority.capture.CaptureRequests.ApproveRequest;
import com.swarmsight.authority.capture.CaptureRequests.AuthorRequest;
import com.swarmsight.authority.capture.CaptureRequests.CaptureResult;
import com.swarmsight.authority.capture.CaptureRequests.EditRequest;
import com.swarmsight.authority.ledger.LedgerRow;
import com.swarmsight.authority.ledger.LedgerService;
import com.swarmsight.authority.policy.PolicyRepository;
import com.swarmsight.authority.policy.PolicyVersion;
import com.swarmsight.authority.run.RunContextRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Records the author, edit, and approve work for a case as ledger rows. Each
 * goes through the same single serialised, hash-chained appender as decisions,
 * so the capture trail is part of the same provable chain. Idempotent on
 * request_id.
 */
@Service
public class CaptureService {

    private final LedgerService ledgerService;
    private final PolicyRepository policyRepository;
    private final RunContextRepository runContextRepository;

    public CaptureService(
            LedgerService ledgerService,
            PolicyRepository policyRepository,
            RunContextRepository runContextRepository) {
        this.ledgerService = ledgerService;
        this.policyRepository = policyRepository;
        this.runContextRepository = runContextRepository;
    }

    public CaptureResult author(String caseRef, AuthorRequest req) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", "author");
        payload.put("draft", req.draft());
        return append("author", caseRef, req.runId(), req.actor(), req.workflow(), req.action(),
                payload, req.requestId());
    }

    public CaptureResult edit(String caseRef, EditRequest req) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", "edit");
        payload.put("draft", req.draft());
        return append("edit", caseRef, req.runId(), req.actor(), req.workflow(), req.action(),
                payload, req.requestId());
    }

    public CaptureResult approve(String caseRef, ApproveRequest req) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", "approve");
        payload.put("reason_code", req.reasonCode());
        payload.put("note", req.note());
        payload.put("appeal_route", req.appealRoute());
        payload.put("signposting", req.signposting());
        return append("approve", caseRef, req.runId(), req.actor(), req.workflow(), req.action(),
                payload, req.requestId());
    }

    private CaptureResult append(
            String intent, String caseRef, String runId, String actor, String workflow, String action,
            Map<String, Object> payload, String requestId) {
        Instant now = Instant.now();
        runContextRepository.ensureExists(runId, caseRef, workflow, now);
        String version = policyRepository.resolve(workflow, now)
                .map(PolicyVersion::version).orElse("n/a");
        LedgerRow row = ledgerService.append(
                intent, actor, runId, caseRef, action, version, payload, requestId, now);
        return new CaptureResult(row.seq(), row.rowHash(), intent);
    }
}
