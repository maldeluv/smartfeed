from __future__ import annotations

from fastapi.testclient import TestClient


def test_register_login_and_me(client: TestClient) -> None:
    register_response = client.post(
        "/api/v1/auth/register",
        json={
            "email": "new-user@smartfeed.local",
            "full_name": "New User",
            "password": "password123",
        },
    )
    assert register_response.status_code == 201
    register_payload = register_response.json()
    assert register_payload["token_type"] == "bearer"
    assert register_payload["user"]["email"] == "new-user@smartfeed.local"

    duplicate_response = client.post(
        "/api/v1/auth/register",
        json={
            "email": "new-user@smartfeed.local",
            "full_name": "New User",
            "password": "password123",
        },
    )
    assert duplicate_response.status_code == 409

    login_response = client.post(
        "/api/v1/auth/login",
        json={"email": "new-user@smartfeed.local", "password": "password123"},
    )
    assert login_response.status_code == 200
    token = login_response.json()["access_token"]

    me_response = client.get("/api/v1/users/me", headers={"Authorization": f"Bearer {token}"})
    assert me_response.status_code == 200
    assert me_response.json()["role"] == "user"


def test_login_rejects_invalid_password(client: TestClient) -> None:
    response = client.post(
        "/api/v1/auth/login",
        json={"email": "demo@smartfeed.local", "password": "wrong-password"},
    )
    assert response.status_code == 401
