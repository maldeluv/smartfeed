from __future__ import annotations

from fastapi.testclient import TestClient


def test_admin_article_crud(
    client: TestClient,
    admin_headers: dict[str, str],
    user_headers: dict[str, str],
) -> None:
    payload = {
        "title": "Admin Pytest Article",
        "summary": "Article created by admin pytest.",
        "content": "Long enough article content for admin pytest.",
        "source_url": "https://example.com/tests/admin-pytest",
        "category_id": 1,
        "author": "Pytest",
        "popularity_score": 1,
    }

    forbidden_response = client.post(
        "/api/v1/admin/articles",
        headers=user_headers,
        json=payload,
    )
    assert forbidden_response.status_code == 403

    create_response = client.post(
        "/api/v1/admin/articles",
        headers=admin_headers,
        json=payload,
    )
    assert create_response.status_code == 201
    article_id = create_response.json()["id"]

    put_response = client.put(
        f"/api/v1/admin/articles/{article_id}",
        headers=admin_headers,
        json={"title": "Admin Pytest Article Updated", "popularity_score": 2},
    )
    assert put_response.status_code == 200
    assert put_response.json()["title"] == "Admin Pytest Article Updated"

    delete_response = client.delete(
        f"/api/v1/admin/articles/{article_id}",
        headers=admin_headers,
    )
    assert delete_response.status_code == 204

    detail_response = client.get(f"/api/v1/articles/{article_id}")
    assert detail_response.status_code == 404
