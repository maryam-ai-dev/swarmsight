package com.swarmsight.authority.capture;

import com.swarmsight.authority.capture.CaptureRequests.ApproveRequest;
import com.swarmsight.authority.capture.CaptureRequests.AuthorRequest;
import com.swarmsight.authority.capture.CaptureRequests.CaptureResult;
import com.swarmsight.authority.capture.CaptureRequests.EditRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Records the author, edit, and approve work that the proof pack later assembles.
 */
@RestController
public class CaptureController {

    private final CaptureService captureService;

    public CaptureController(CaptureService captureService) {
        this.captureService = captureService;
    }

    @PostMapping("/cases/{caseRef}/author")
    public CaptureResult author(@PathVariable String caseRef, @Valid @RequestBody AuthorRequest req) {
        return captureService.author(caseRef, req);
    }

    @PostMapping("/cases/{caseRef}/edit")
    public CaptureResult edit(@PathVariable String caseRef, @Valid @RequestBody EditRequest req) {
        return captureService.edit(caseRef, req);
    }

    @PostMapping("/cases/{caseRef}/approve")
    public CaptureResult approve(@PathVariable String caseRef, @Valid @RequestBody ApproveRequest req) {
        return captureService.approve(caseRef, req);
    }
}
