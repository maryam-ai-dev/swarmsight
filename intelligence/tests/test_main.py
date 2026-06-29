"""Sprint 0 tests for Intelligence: health is ok and /propose returns a
well-formed Proposal. The suite is intentionally small; it proves the service
boots and the boundary exists.
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
    assert body["workflow"] == "HA-09"
    assert body["action"] == "draft_response"
    assert 0.0 <= body["confidence"] <= 1.0
