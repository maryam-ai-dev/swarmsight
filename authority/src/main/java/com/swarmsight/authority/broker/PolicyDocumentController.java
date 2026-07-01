package com.swarmsight.authority.broker;

import com.swarmsight.authority.workbench.PolicyIngestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Infer a department policy from a policy document the department already holds
 * in its SharePoint library. The service owner picks a document and the policy
 * it should govern; Authority fetches the file, has Claude read the guards it
 * implies, and stages a <em>proposed</em> change on the Workbench. It enacts
 * nothing: the same diff / replay / activate gate still applies.
 *
 * <p>This closes the loop opened by the caseload: cases come from SharePoint
 * documents, and now the policy those cases are governed against can too.
 */
@RestController
public class PolicyDocumentController {

    private final SharePointConnector connector;
    private final PolicyIngestionService ingestion;

    public PolicyDocumentController(SharePointConnector connector, PolicyIngestionService ingestion) {
        this.connector = connector;
        this.ingestion = ingestion;
    }

    /** A policy document offered as a source, without its bytes. */
    public record PolicyDocSummary(String name, String version) {
    }

    /** The policy documents in the library a service owner can infer a policy from. */
    @GetMapping("/policy-documents")
    public List<PolicyDocSummary> documents() {
        return connector.listPolicyDocuments().stream()
                .map(d -> new PolicyDocSummary(d.name(), d.version()))
                .toList();
    }

    public record InferRequest(@NotBlank String document, String policyId) {

        public String safePolicyId() {
            return policyId == null || policyId.isBlank() ? "HL-01" : policyId;
        }
    }

    /**
     * Fetch the named policy document from SharePoint and stage a proposed change
     * for the given policy, inferred from it.
     */
    @PostMapping("/policy-documents/infer")
    public ResponseEntity<?> infer(@Valid @RequestBody InferRequest req) {
        try {
            SharePointConnector.PolicyDoc doc = connector.fetchPolicyDocument(req.document());
            String locator = "sharepoint:" + doc.name();
            return ResponseEntity.ok(ingestion.ingestDocument(
                    req.safePolicyId(), doc.bytes(), doc.mediaType(), doc.name(), locator));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of(
                    "error", "Could not infer a policy from the document: " + e.getMessage()));
        }
    }
}
