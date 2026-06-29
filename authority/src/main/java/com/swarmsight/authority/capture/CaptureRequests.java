package com.swarmsight.authority.capture;

import jakarta.validation.constraints.NotBlank;

/**
 * Request bodies for the three capture intents. The envelope fields identify the
 * run and the work; each intent adds its own payload.
 */
public final class CaptureRequests {

    private CaptureRequests() {
    }

    public record AuthorRequest(
            @NotBlank String requestId,
            @NotBlank String runId,
            @NotBlank String actor,
            @NotBlank String workflow,
            @NotBlank String action,
            @NotBlank String draft) {
    }

    public record EditRequest(
            @NotBlank String requestId,
            @NotBlank String runId,
            @NotBlank String actor,
            @NotBlank String workflow,
            @NotBlank String action,
            @NotBlank String draft) {
    }

    public record ApproveRequest(
            @NotBlank String requestId,
            @NotBlank String runId,
            @NotBlank String actor,
            @NotBlank String workflow,
            @NotBlank String action,
            @NotBlank String reasonCode,
            String note,
            String appealRoute,
            String signposting) {
    }

    /** What a capture returns: the ledger row it created. */
    public record CaptureResult(long seq, String rowHash, String intent) {
    }
}
