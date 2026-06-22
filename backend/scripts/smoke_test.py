from __future__ import annotations

import argparse
import json
import os
from datetime import datetime, timezone
from typing import Any
from urllib import error, request
from uuid import uuid4


def api_json(
    method: str,
    url: str,
    payload: dict[str, Any] | None = None,
    token: str | None = None,
) -> tuple[int, dict[str, Any]]:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token is not None:
        headers["Authorization"] = f"Bearer {token}"
    req = request.Request(url, data=body, headers=headers, method=method)
    try:
        with request.urlopen(req, timeout=20) as response:
            response_body = response.read().decode("utf-8")
            return response.status, json.loads(response_body) if response_body else {}
    except error.HTTPError as exc:
        response_body = exc.read().decode("utf-8")
        detail = json.loads(response_body) if response_body else {}
        raise RuntimeError(f"{method} {url} failed: {exc.code} {detail}") from exc


def check(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)
    print(f"[ok] {message}")


def run_smoke(base_url: str) -> None:
    base_url = base_url.rstrip("/")

    status, health = api_json("GET", f"{base_url}/health")
    check(status == 200 and health.get("status") == "ok", "healthcheck")

    email = f"smoke-{uuid4().hex[:10]}@smartfeed.local"
    password = "password123"
    _, register_payload = api_json(
        "POST",
        f"{base_url}/api/v1/auth/register",
        {
            "email": email,
            "full_name": "Smoke Test User",
            "password": password,
        },
    )
    check(register_payload["user"]["email"] == email, "registration")

    _, login_payload = api_json(
        "POST",
        f"{base_url}/api/v1/auth/login",
        {"email": email, "password": password},
    )
    token = login_payload["access_token"]
    check(bool(token), "login")

    _, articles_page = api_json("GET", f"{base_url}/api/v1/articles?limit=1&offset=0")
    check(articles_page["total"] > 0, "get articles")
    article = articles_page["items"][0]
    article_id = article["id"]
    category_id = article["category_id"]

    _, like_payload = api_json(
        "POST",
        f"{base_url}/api/v1/articles/{article_id}/like",
        token=token,
    )
    check(like_payload["status"] == "liked", "like article")

    _, save_payload = api_json(
        "POST",
        f"{base_url}/api/v1/articles/{article_id}/save",
        token=token,
    )
    check(save_payload["status"] == "saved", "save article")

    _, event_payload = api_json(
        "POST",
        f"{base_url}/api/v1/events",
        {
            "eventType": "view_article",
            "articleId": article_id,
            "categoryId": category_id,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "metadata": {"source": "smoke_test"},
        },
        token=token,
    )
    check(event_payload["status"] == "accepted", "send event")

    _, recommendations_payload = api_json(
        "GET",
        f"{base_url}/api/v1/recommendations/me?limit=10&offset=0",
        token=token,
    )
    check("items" in recommendations_payload, "get recommendations")

    print(
        "Smoke test completed: "
        f"article_id={article_id}, recommendations={recommendations_payload['total']}."
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="SmartFeed HTTP smoke test.")
    parser.add_argument(
        "--base-url",
        default=os.getenv("SMARTFEED_BASE_URL", "http://localhost:8000"),
    )
    return parser.parse_args()


if __name__ == "__main__":
    run_smoke(parse_args().base_url)
