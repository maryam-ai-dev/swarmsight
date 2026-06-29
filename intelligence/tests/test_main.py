"""Tests for Intelligence: health, the legacy propose stub, and the live agent.
The agent must escalate eviction-risk-with-dependents cases, request missing
evidence, draft clear cases, and never propose to send an adverse decision.
"""

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_returns_ok():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "intelligence"}


def test_propose_returns_a_proposal():
    response = client.get("/propose")
    assert response.status_code == 200
    body = response.json()
    assert body["proposal_id"] == "prop-stub-0001"
    assert body["action"] == "draft_response"


def test_agent_escalates_eviction_with_dependents():
    response = client.post("/agent/act", json={
        "scenario_id": "eviction",
        "inputs": {"eviction_risk": True, "dependent_children": True, "eviction_notice": True},
    })
    assert response.status_code == 200
    assert response.json()["proposed_action"] == "escalate"


def test_agent_requests_missing_evidence():
    response = client.post("/agent/act", json={
        "scenario_id": "evidence",
        "inputs": {"eviction_risk": True, "dependent_children": False},
    })
    assert response.json()["proposed_action"] == "request_evidence"


def test_agent_drafts_clear_case():
    response = client.post("/agent/act", json={
        "scenario_id": "clear",
        "inputs": {"eviction_risk": False, "dependent_children": False, "eviction_notice": True},
    })
    assert response.json()["proposed_action"] == "draft_response"


def test_agent_never_proposes_to_send():
    # No combination of inputs makes the safe agent propose send_decision.
    for inputs in [
        {"eviction_risk": True, "dependent_children": True, "eviction_notice": True, "adverse_outcome": True},
        {"eviction_risk": True, "dependent_children": False},
        {"eviction_risk": False, "dependent_children": False, "eviction_notice": True},
    ]:
        response = client.post("/agent/act", json={"scenario_id": "x", "inputs": inputs})
        assert response.json()["proposed_action"] != "send_decision"
