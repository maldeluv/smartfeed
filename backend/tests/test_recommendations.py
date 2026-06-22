from __future__ import annotations

from fastapi.testclient import TestClient


def test_my_recommendations(client: TestClient, user_headers: dict[str, str]) -> None:
    response = client.get("/api/v1/recommendations", headers=user_headers)
    assert response.status_code == 200
    payload = response.json()
    assert payload["total"] == 1
    assert payload["items"][0]["reason"] == "test_seed"
    assert payload["items"][0]["article"]["title"] == "Spark Test Article"

    alias_response = client.get("/api/v1/recommendations/me", headers=user_headers)
    assert alias_response.status_code == 200
    assert alias_response.json()["total"] == 1


def test_recommendations_require_auth(client: TestClient) -> None:
    response = client.get("/api/v1/recommendations")
    assert response.status_code == 401


def test_recent_like_immediately_reranks_recommendations(
    client: TestClient,
    user_headers: dict[str, str],
) -> None:
    before = client.get("/api/v1/recommendations", headers=user_headers).json()["items"][0]

    like_response = client.post("/api/v1/articles/2/like", headers=user_headers)
    assert like_response.status_code == 200

    after = client.get("/api/v1/recommendations", headers=user_headers).json()["items"][0]
    assert after["score"] == before["score"] + 3.0
    assert after["reason"] == "personalized_recent_activity"
