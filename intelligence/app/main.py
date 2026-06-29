"""SwarmSight Intelligence.

Intelligence proposes; it never decides. Authority is always the first stop and
the only place a verdict is made. From Sprint 6, Intelligence is the live agent
under assurance: POST /agent/act takes a case context and returns a proposed
action with a rationale. A safe agent escalates eviction-risk cases, requests
missing evidence, drafts on clear cases, and never proposes to send an adverse
decision. The Arena in Authority runs this agent through the governed path.
"""

from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel, Field

app = FastAPI(
    title="SwarmSight Intelligence",
    description="Proposes. Does not decide. The live agent under assurance.",
    version="0.1.0",
)


class Health(BaseModel):
    status: str
    service: str


class Proposal(BaseModel):
    """What Intelligence hands to Authority. Authority decides whether to act on
    it. Authority is always the first stop; this is only a suggestion.
    """

    proposal_id: str = Field(..., description="Stable id for this proposal")
    workflow: str = Field(..., description="Workflow the proposal targets")
    action: str = Field(..., description="Action Intelligence suggests")
    summary: str = Field(..., description="Plain-English summary of the suggestion")
    confidence: float = Field(..., ge=0.0, le=1.0, description="Self-reported confidence")


class ActRequest(BaseModel):
    """A case context the agent reasons about. The Arena sends one per scenario."""

    scenario_id: str = Field(default="", description="Scenario id, for traceability")
    inputs: dict[str, Any] = Field(default_factory=dict, description="Case inputs")


class AgentDecision(BaseModel):
    """What the agent would do, and why. It never decides; Authority does."""

    proposed_action: str = Field(..., description="Action the agent proposes")
    rationale: str = Field(..., description="Why the agent proposes it")


def _is_true(inputs: dict[str, Any], key: str) -> bool:
    value = inputs.get(key)
    if isinstance(value, bool):
        return value
    return str(value).lower() == "true"


def decide_action(inputs: dict[str, Any]) -> AgentDecision:
    """The safe agent. Escalate eviction risk with dependents, request missing
    evidence, otherwise draft. It never proposes to send an adverse decision: a
    citizen-facing decision is for an officer.
    """
    if _is_true(inputs, "eviction_risk") and _is_true(inputs, "dependent_children"):
        return AgentDecision(
            proposed_action="escalate",
            rationale="Eviction risk with dependent children is an officer decision. Escalating.",
        )
    if _is_true(inputs, "eviction_risk") and "eviction_notice" not in inputs:
        return AgentDecision(
            proposed_action="request_evidence",
            rationale="The latest eviction notice is missing. Requesting it before drafting.",
        )
    return AgentDecision(
        proposed_action="draft_response",
        rationale="No risk guard tripped. Preparing a draft for officer review.",
    )


@app.get("/health", response_model=Health)
def health() -> Health:
    return Health(status="ok", service="intelligence")


@app.post("/agent/act", response_model=AgentDecision)
def act(request: ActRequest) -> AgentDecision:
    return decide_action(request.inputs)


@app.get("/propose", response_model=Proposal)
def propose() -> Proposal:
    """Kept for compatibility. A canned Proposal; the live path is /agent/act."""
    return Proposal(
        proposal_id="prop-stub-0001",
        workflow="HA-09",
        action="draft_response",
        summary="Stub proposal. Suggests drafting a response for the housing case.",
        confidence=0.5,
    )
