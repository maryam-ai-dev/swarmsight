package com.swarmsight.authority.workbench;

import com.swarmsight.authority.decision.CertificateCheck;
import com.swarmsight.authority.decision.CertificateStatus;
import com.swarmsight.authority.decision.DecisionRequest;
import com.swarmsight.authority.decision.EngineResult;
import com.swarmsight.authority.decision.VerdictEngine;
import com.swarmsight.authority.policy.Level;
import com.swarmsight.authority.policy.PolicyVersion;
import com.swarmsight.authority.workbench.ShadowReplayReport.ReplayCase;
import com.swarmsight.authority.workbench.ShadowReplayReport.ReplayResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Replays cases against a candidate policy and the version in force, comparing
 * the verdicts. Runs purely through the VerdictEngine, in shadow: no ledger
 * write, nothing sent. Produces the "what would change" report.
 */
@Component
public class ShadowReplayer {

    private static final CertificateCheck REPLAY_CERT =
            new CertificateCheck(CertificateStatus.VALID, Level.L2);

    private final VerdictEngine verdictEngine;

    public ShadowReplayer(VerdictEngine verdictEngine) {
        this.verdictEngine = verdictEngine;
    }

    public ShadowReplayReport replay(PolicyVersion base, PolicyVersion candidate, List<ReplayCase> cases) {
        List<ReplayResult> results = new ArrayList<>();
        int changed = 0;
        for (ReplayCase c : cases) {
            EngineResult oldResult = evaluate(base, c);
            EngineResult newResult = evaluate(candidate, c);
            boolean isChanged = !oldResult.verdict().effect().equals(newResult.verdict().effect());
            if (isChanged) {
                changed++;
            }
            results.add(new ReplayResult(
                    c.caseRef(),
                    oldResult.verdict().effect().name(), oldResult.verdict().reasonCode(),
                    newResult.verdict().effect().name(), newResult.verdict().reasonCode(),
                    isChanged));
        }
        return new ShadowReplayReport(cases.size(), changed, results);
    }

    private EngineResult evaluate(PolicyVersion version, ReplayCase c) {
        DecisionRequest req = new DecisionRequest(
                "replay", "replay", c.caseRef(), "replay-agent", version.policyId(), c.action(), c.inputs());
        return verdictEngine.evaluate(req, Optional.of(version), REPLAY_CERT);
    }
}
