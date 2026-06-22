from __future__ import annotations

from fastapi.testclient import TestClient


def test_categories_and_articles(client: TestClient) -> None:
    categories_response = client.get("/api/v1/categories")
    assert categories_response.status_code == 200
    categories = categories_response.json()
    assert len(categories) == 2

    articles_response = client.get("/api/v1/articles", params={"limit": 10, "offset": 0})
    assert articles_response.status_code == 200
    articles_page = articles_response.json()
    assert articles_page["total"] == 2
    assert len(articles_page["items"]) == 2

    article_id = articles_page["items"][0]["id"]
    detail_response = client.get(f"/api/v1/articles/{article_id}")
    assert detail_response.status_code == 200
    assert detail_response.json()["content"]


def test_article_filters(client: TestClient) -> None:
    category_response = client.get("/api/v1/articles", params={"category_id": 1})
    assert category_response.status_code == 200
    assert category_response.json()["total"] == 1

    search_response = client.get("/api/v1/articles", params={"search": "Spark"})
    assert search_response.status_code == 200
    assert search_response.json()["total"] == 1
    assert search_response.json()["items"][0]["title"] == "Spark Test Article"


def test_article_not_found(client: TestClient) -> None:
    response = client.get("/api/v1/articles/9999")
    assert response.status_code == 404
