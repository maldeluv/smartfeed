from __future__ import annotations

from fastapi.testclient import TestClient


def test_personal_analytics(client: TestClient, user_headers: dict[str, str]) -> None:
    response = client.get("/api/v1/analytics/me", headers=user_headers)
    assert response.status_code == 200
    payload = response.json()
    assert payload["source"] == "postgres.pending_events"
    assert payload["views_count"] >= 1
    assert payload["likes_count"] >= 1
    assert payload["recommendations_opened"] == 1
    assert payload["recommended_articles_opened"] == 1
    assert payload["recommendation_ctr"] == 1.0
    assert payload["favorite_categories"]


def test_global_analytics_requires_admin(client: TestClient, user_headers: dict[str, str]) -> None:
    response = client.get(
        "/api/v1/analytics/global",
        headers=user_headers,
        params={"limit": 5, "days": 30},
    )
    assert response.status_code == 403


def test_global_analytics(client: TestClient, admin_headers: dict[str, str]) -> None:
    response = client.get(
        "/api/v1/analytics/global",
        headers=admin_headers,
        params={"limit": 5, "days": 30},
    )
    assert response.status_code == 200
    payload = response.json()
    assert payload["source"] == "postgres.pending_events"
    assert payload["top_categories"]
    assert payload["top_articles"]
    assert payload["events_by_type"]
    assert payload["activity_by_day"]
