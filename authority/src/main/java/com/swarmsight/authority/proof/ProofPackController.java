package com.swarmsight.authority.proof;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the assembled proof pack the audit screen renders.
 */
@RestController
public class ProofPackController {

    private final ProofPackService proofPackService;

    public ProofPackController(ProofPackService proofPackService) {
        this.proofPackService = proofPackService;
    }

    @GetMapping("/cases/{caseRef}/proof-pack")
    public ResponseEntity<ProofPack> proofPack(@PathVariable String caseRef) {
        return proofPackService.assemble(caseRef)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
