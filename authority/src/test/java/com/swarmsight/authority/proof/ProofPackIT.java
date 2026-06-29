package com.swarmsight.authority.proof;

import static org.assertj.core.api.Assertions.assertThat;

import com.swarmsight.authority.capture.CaptureRequests.ApproveRequest;
import com.swarmsight.authority.capture.CaptureRequests.AuthorRequest;
import com.swarmsight.authority.capture.CaptureRequests.EditRequest;
import com.swarmsight.authority.capture.CaptureService;
import com.swarmsight.authority.decision.DecisionRequest;
import com.swarmsight.authority.decision.DecisionService;
import com.swarmsight.authority.decision.ReasonCode;
import com.swarmsight.authority.proof.TextDiff.Segment.Op;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The proof pack, assembled from a real author, edit, and approve flow against a
 * live Postgres: all seven sections, a derived diff, an intact chain, and a
 * stable export hash.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "swarmsight.demo-seed=false")
@Testcontainers
class ProofPackIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired private DecisionService decisionService;
    @Autowired private CaptureService captureService;
    @Autowired private ProofPackService proofPackService;
    @Autowired private TestRestTemplate rest;

    private static final String CASE = "PP-1";

    @BeforeEach
    void seedCase() {
        decisionService.decide(new DecisionRequest("pp-dec-1", "run-pp-1", CASE, "agent-housing-1",
                "HA-09", "draft_response",
                Map.of("tenancy_status", "confirmed", "eviction_risk", true, "dependent_children", true)));
        captureService.author(CASE, new AuthorRequest("pp-auth-1", "run-pp-1", "housing-appeals-agent",
                "HA-09", "draft_response",
                "Dear Ms Adeyemi, I am writing to inform you that your application has been refused."));
        captureService.edit(CASE, new EditRequest("pp-edit-1", "run-pp-1", "officer-okafor",
                "HA-09", "draft_response",
                "Dear Ms Adeyemi, I am writing to confirm the next steps in your case, and how to appeal."));
        captureService.approve(CASE, new ApproveRequest("pp-appr-1", "run-pp-1", "officer-okafor",
                "HA-09", "draft_response", "OFFICER_APPROVED",
                "Softened the refusal wording and added appeals signposting.",
                "First-tier Tribunal (Property Chamber)", "Appeals paragraph added to the letter."));
    }

    @Test
    void assemblesAllSevenSectionsFromRealRows() {
        ProofPack pack = proofPackService.assemble(CASE).orElseThrow();

        assertThat(pack.chainVerification().ok()).isTrue();
        assertThat(pack.whatHappened().effect()).isEqualTo("HOLD");
        assertThat(pack.whyReview().reasonCode()).isEqualTo(ReasonCode.EVICTION_RISK_DEPENDENTS);
        assertThat(pack.whyReview().guardsFired())
                .anyMatch(g -> g.name().equals("eviction-risk-with-dependents") && !g.source().isBlank());
        assertThat(pack.responsibility().approvedBy()).isEqualTo("officer-okafor");
        assertThat(pack.decisionTrace()).extracting(ProofPack.TraceEvent::intent)
                .containsExactly("author", "edit", "approve");
        assertThat(pack.humanJudgement().appealRoute()).contains("First-tier Tribunal");
        assertThat(pack.sources().missing()).contains("latest eviction notice");
        assertThat(pack.governingRules().technical().exportHash()).isEqualTo(pack.exportHash());
    }

    @Test
    void diffIsDerivedFromAuthorAndFinalDrafts() {
        ProofPack pack = proofPackService.assemble(CASE).orElseThrow();

        assertThat(pack.humanJudgement().authorDraft()).contains("refused");
        assertThat(pack.humanJudgement().finalDraft()).contains("next steps");
        assertThat(pack.humanJudgement().diff()).anyMatch(s -> s.op() == Op.DELETE && s.text().contains("refused"));
        assertThat(pack.humanJudgement().diff()).anyMatch(s -> s.op() == Op.INSERT && s.text().contains("appeal"));
    }

    @Test
    void exportHashIsStableForTheSameInput() {
        String first = proofPackService.assemble(CASE).orElseThrow().exportHash();
        String second = proofPackService.assemble(CASE).orElseThrow().exportHash();
        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void unknownCaseHasNoProofPack() {
        assertThat(proofPackService.assemble("does-not-exist")).isEmpty();
        assertThat(rest.getForEntity("/cases/does-not-exist/proof-pack", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void proofPackEndpointRendersTheCase() {
        ProofPack pack = rest.getForObject("/cases/" + CASE + "/proof-pack", ProofPack.class);
        assertThat(pack).isNotNull();
        assertThat(pack.caseRef()).isEqualTo(CASE);
        assertThat(pack.chainVerification().ok()).isTrue();
        assertThat(pack.ledgerEntries()).isGreaterThanOrEqualTo(4);
    }
}
