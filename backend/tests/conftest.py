from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.core.database import Base, get_db
from app.core.kafka import KafkaDeliveryResult, kafka_event_producer
from app.core.mongodb import get_mongo_database
from app.core.security import get_password_hash
from app.main import app
from app.models import Article, Category, PendingEvent, Recommendation, User


@pytest.fixture()
def db_session() -> Session:
    engine = create_engine(
        "sqlite+pysqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    TestingSessionLocal = sessionmaker(
        autocommit=False,
        autoflush=False,
        bind=engine,
        expire_on_commit=False,
    )
    Base.metadata.create_all(bind=engine)

    with TestingSessionLocal() as session:
        seed_test_data(session)
        yield session

    Base.metadata.drop_all(bind=engine)
    engine.dispose()


@pytest.fixture()
def client(db_session: Session, monkeypatch: pytest.MonkeyPatch) -> TestClient:
    def override_get_db():
        yield db_session

    def override_get_mongo_database():
        return None

    def fake_send(_: dict) -> KafkaDeliveryResult:
        return KafkaDeliveryResult(delivered=False, detail="Kafka disabled in tests")

    monkeypatch.setattr(kafka_event_producer, "send", fake_send)
    app.dependency_overrides[get_db] = override_get_db
    app.dependency_overrides[get_mongo_database] = override_get_mongo_database

    with TestClient(app) as test_client:
        yield test_client

    app.dependency_overrides.clear()


@pytest.fixture()
def user_headers(client: TestClient) -> dict[str, str]:
    return auth_headers(client, "demo@smartfeed.local", "demo12345")


@pytest.fixture()
def admin_headers(client: TestClient) -> dict[str, str]:
    return auth_headers(client, "admin@smartfeed.local", "admin12345")


def auth_headers(client: TestClient, email: str, password: str) -> dict[str, str]:
    response = client.post(
        "/api/v1/auth/login",
        json={"email": email, "password": password},
    )
    assert response.status_code == 200
    token = response.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


def seed_test_data(session: Session) -> None:
    now = datetime.now(timezone.utc)
    users = [
        User(
            email="admin@smartfeed.local",
            full_name="Admin User",
            role="admin",
            password_hash=get_password_hash("admin12345"),
            is_active=True,
        ),
        User(
            email="demo@smartfeed.local",
            full_name="Demo User",
            role="user",
            password_hash=get_password_hash("demo12345"),
            is_active=True,
        ),
    ]
    categories = [
        Category(name="Backend", slug="backend", description="Backend engineering."),
        Category(name="Big Data", slug="big-data", description="Data platforms."),
    ]
    session.add_all(users + categories)
    session.flush()

    articles = [
        Article(
            title="FastAPI Test Article",
            summary="A test article about FastAPI and APIs.",
            content="Long enough FastAPI article content for tests.",
            source_url="https://example.com/tests/fastapi",
            category_id=categories[0].id,
            author="SmartFeed Tests",
            published_at=now - timedelta(days=1),
            popularity_score=10.0,
        ),
        Article(
            title="Spark Test Article",
            summary="A test article about Spark data pipelines.",
            content="Long enough Spark article content for tests.",
            source_url="https://example.com/tests/spark",
            category_id=categories[1].id,
            author="SmartFeed Tests",
            published_at=now - timedelta(days=2),
            popularity_score=8.0,
        ),
    ]
    session.add_all(articles)
    session.flush()

    session.add(
        Recommendation(
            user_id=users[1].id,
            article_id=articles[1].id,
            score=0.95,
            reason="test_seed",
            model_version="pytest",
        )
    )
    session.add_all(
        [
            PendingEvent(
                event_id="pytest-view-1",
                user_id=users[1].id,
                session_id="pytest-session",
                event_type="view_article",
                article_id=articles[0].id,
                category_id=categories[0].id,
                timestamp=now,
                device={"platform": "test"},
                metadata_json={"source": "pytest"},
                error="pytest_seed",
            ),
            PendingEvent(
                event_id="pytest-like-1",
                user_id=users[1].id,
                session_id="pytest-session",
                event_type="like_article",
                article_id=articles[0].id,
                category_id=categories[0].id,
                timestamp=now,
                device={"platform": "test"},
                metadata_json={"source": "pytest"},
                error="pytest_seed",
            ),
            PendingEvent(
                event_id="pytest-open-recommendations-1",
                user_id=users[1].id,
                session_id="pytest-session",
                event_type="open_recommendations",
                article_id=None,
                category_id=None,
                timestamp=now,
                device={"platform": "test"},
                metadata_json={"source": "pytest"},
                error="pytest_seed",
            ),
            PendingEvent(
                event_id="pytest-open-recommended-1",
                user_id=users[1].id,
                session_id="pytest-session",
                event_type="open_recommended_article",
                article_id=articles[1].id,
                category_id=categories[1].id,
                timestamp=now,
                device={"platform": "test"},
                metadata_json={"source": "pytest"},
                error="pytest_seed",
            ),
        ]
    )
    session.commit()
