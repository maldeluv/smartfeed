from __future__ import annotations

from fastapi.testclient import TestClient


def test_user_actions_create_state_and_events(
    client: TestClient,
    user_headers: dict[str, str],
) -> None:
    subscribe_response = client.post("/api/v1/categories/1/subscribe", headers=user_headers)
    assert subscribe_response.status_code == 200
    assert subscribe_response.json()["status"] == "subscribed"
    assert subscribe_response.json()["event"]["delivery"] == "pending_events"

    like_response = client.post("/api/v1/articles/1/like", headers=user_headers)
    assert like_response.status_code == 200
    assert like_response.json()["status"] == "liked"

    save_response = client.post("/api/v1/articles/1/save", headers=user_headers)
    assert save_response.status_code == 200
    assert save_response.json()["status"] == "saved"

    categories_response = client.get("/api/v1/categories", headers=user_headers)
    assert categories_response.json()[0]["is_subscribed"] is True

    article_response = client.get("/api/v1/articles/1", headers=user_headers)
    assert article_response.json()["is_liked"] is True
    assert article_response.json()["is_saved"] is True

    saved_response = client.get("/api/v1/saved", headers=user_headers)
    assert saved_response.status_code == 200
    assert saved_response.json()["total"] == 1
    assert saved_response.json()["items"][0]["is_saved"] is True

    unlike_response = client.delete("/api/v1/articles/1/like", headers=user_headers)
    assert unlike_response.status_code == 200
    assert unlike_response.json()["status"] == "unliked"

    unsave_response = client.delete("/api/v1/articles/1/save", headers=user_headers)
    assert unsave_response.status_code == 200
    assert unsave_response.json()["status"] == "unsaved"

    article_response = client.get("/api/v1/articles/1", headers=user_headers)
    assert article_response.json()["is_liked"] is False
    assert article_response.json()["is_saved"] is False


def test_events_single_and_batch(client: TestClient, user_headers: dict[str, str]) -> None:
    event_response = client.post(
        "/api/v1/events",
        headers=user_headers,
        json={
            "eventType": "view_article",
            "articleId": 1,
            "categoryId": 1,
            "metadata": {"source": "pytest"},
        },
    )
    assert event_response.status_code == 200
    assert event_response.json()["status"] == "accepted"
    assert event_response.json()["delivery"] == "pending_events"

    batch_response = client.post(
        "/api/v1/events/batch",
        headers=user_headers,
        json={
            "events": [
                {"eventType": "search", "metadata": {"query": "spark"}},
                {"eventType": "open_recommendations", "metadata": {}},
            ]
        },
    )
    assert batch_response.status_code == 200
    assert batch_response.json()["accepted_count"] == 2
    assert batch_response.json()["failed_count"] == 0


def test_event_validation_requires_article_for_article_events(
    client: TestClient,
    user_headers: dict[str, str],
) -> None:
    response = client.post(
        "/api/v1/events",
        headers=user_headers,
        json={"eventType": "view_article", "metadata": {}},
    )
    assert response.status_code == 422
