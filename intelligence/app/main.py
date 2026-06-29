"""SwarmSight Intelligence.

Intelligence proposes; it never decides. Authority is always the first stop and
the only place a verdict is made. In Sprint 0 this service is a stub: it answers
a health check and returns a single hardcoded Proposal from /propose. It stays a
stub until Sprint 6, when the Arena makes it real.
"""

from fastapi import FastAPI
from pydantic import BaseModel, Field

app = FastAPI(
    title="SwarmSight Intelligence",
    description="Proposes. Does not decide. Stub until Sprint 6.",
    version="0.0.1",
)


class Health(BaseModel):
    status: str
    service: str


class Proposal(BaseModel):
    """What Intelligence hands to Authority. Authority decides whether to act on
    it. The shape here is deliberately small for Sprint 0 and will grow with the
    spec; what matters now is that the boundary exists and returns a Proposal.
    """

    proposal_id: str = Field(..., description="Stable id for this proposal")
    workflow: str = Field(..., description="Workflow the proposal targets")
    action: str = Field(..., description="Action Intelligence suggests")
    summary: str = Field(..., description="Plain-English summary of the suggestion")
    confidence: float = Field(..., ge=0.0, le=1.0, description="Self-reported confidence")


@app.get("/health", response_model=Health)
def health() -> Health:
    return Health(status="ok", service="intelligence")


@app.get("/propose", response_model=Proposal)
def propose() -> Proposal:
    """Return a canned Proposal. Hardcoded on purpose for Sprint 0. Authority
    treats this as a suggestion only.
    """
    return Proposal(
        proposal_id="prop-stub-0001",
        workflow="HA-09",
        action="draft_response",
        summary="Stub proposal. Suggests drafting a response for the housing case.",
        confidence=0.5,
    )
