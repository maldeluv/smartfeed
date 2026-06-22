from __future__ import annotations

import argparse
import json
import random
from collections.abc import Iterable
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from uuid import uuid4

from common import bootstrap_backend_path, default_data_dir, positive_int

bootstrap_backend_path()

from app.core.config import settings
from app.core.database import SessionLocal
from app.models import Article, PendingEvent, User
from app.schemas.event import SmartFeedEvent
from generate_seed_data import ensure_seed_data
from kafka import KafkaProducer
from sqlalchemy import select
from sqlalchemy.orm import Session


ARTICLE_EVENT_TYPES = [
    "view_article",
    "like_article",
    "unlike_article",
    "save_article",
    "unsave_article",
    "open_recommended_article",
]
CATEGORY_EVENT_TYPES = ["subscribe_category", "unsubscribe_category"]
GLOBAL_EVENT_TYPES = ["search", "open_recommendations"]
EVENT_WEIGHTS = [
    ("view_article", 46),
    ("like_article", 10),
    ("save_article", 8),
    ("open_recommended_article", 7),
    ("search", 10),
    ("open_recommendations", 7),
    ("subscribe_category", 5),
    ("unsubscribe_category", 2),
    ("unlike_article", 2),
    ("unsave_article", 3),
]
SEARCH_TERMS = [
    "spark",
    "kafka",
    "fastapi",
    "android offline",
    "postgres index",
    "recommendations",
    "mongodb events",
    "security jwt",
    "big data",
    "mobile analytics",
]
APP_VERSIONS = ["1.0.0", "1.1.0", "1.2.0"]
ANDROID_VERSIONS = ["10", "11", "12", "13", "14", "15"]


def choose_event_type() -> str:
    event_types = [item[0] for item in EVENT_WEIGHTS]
    weights = [item[1] for item in EVENT_WEIGHTS]
    return random.choices(event_types, weights=weights, k=1)[0]


def load_user_ids(db: Session, limit: int) -> list[int]:
    statement = (
        select(User.id)
        .where(User.role == "user", User.is_active.is_(True))
        .order_by(User.id.asc())
        .limit(limit)
    )
    return list(db.scalars(statement).all())


def load_articles(db: Session, limit: int) -> list[tuple[int, int]]:
    statement = select(Article.id, Article.category_id).order_by(Article.id.asc()).limit(limit)
    return [(row[0], row[1]) for row in db.execute(statement).all()]


def synthetic_articles(count: int) -> list[tuple[int, int]]:
    return [(index, ((index - 1) % 12) + 1) for index in range(1, count + 1)]


def synthetic_user_ids(count: int) -> list[int]:
    return list(range(1, count + 1))


def build_metadata(event_type: str) -> dict[str, Any]:
    if event_type == "search":
        return {"query": random.choice(SEARCH_TERMS), "source": "feed_search"}
    if event_type == "view_article":
        return {
            "duration_seconds": random.randint(8, 240),
            "source": random.choice(["feed", "category", "saved", "push"]),
        }
    if event_type == "open_recommended_article":
        return {
            "recommendation_rank": random.randint(1, 20),
            "reason": random.choice(["category_interest", "popular_now", "similar_users"]),
        }
    return {"source": random.choice(["feed", "detail", "category", "recommendations"])}


def build_device() -> dict[str, str]:
    return {
        "platform": "android",
        "app_version": random.choice(APP_VERSIONS),
        "os_version": random.choice(ANDROID_VERSIONS),
    }


def build_event(
    user_id: int,
    event_type: str,
    article: tuple[int, int] | None,
    category_id: int | None,
    timestamp: datetime,
    session_id: str,
) -> dict[str, Any]:
    article_id = article[0] if article is not None else None
    article_category_id = article[1] if article is not None else None
    final_category_id = category_id or article_category_id

    event = SmartFeedEvent(
        eventType=event_type,
        articleId=article_id,
        categoryId=final_category_id,
        timestamp=timestamp,
        sessionId=session_id,
        device=build_device(),
        metadata=build_metadata(event_type),
    )
    payload = event.model_dump(mode="json", by_alias=False)
    payload["event_id"] = str(event.event_id)
    payload["session_id"] = str(event.session_id) if event.session_id is not None else None
    payload["user_id"] = user_id
    return payload


def generate_events(
    user_ids: list[int],
    articles: list[tuple[int, int]],
    total_events: int,
) -> Iterable[dict[str, Any]]:
    sessions = {
        user_id: [str(uuid4()) for _ in range(random.randint(2, 8))]
        for user_id in user_ids
    }
    category_ids = sorted({category_id for _, category_id in articles})
    now = datetime.now(timezone.utc)

    for _ in range(total_events):
        user_id = random.choice(user_ids)
        event_type = choose_event_type()
        timestamp = now - timedelta(
            days=random.randint(0, 29),
            seconds=random.randint(0, 86_399),
        )
        session_id = random.choice(sessions[user_id])
        article = random.choice(articles) if event_type in ARTICLE_EVENT_TYPES else None
        category_id = random.choice(category_ids) if event_type in CATEGORY_EVENT_TYPES else None
        yield build_event(
            user_id=user_id,
            event_type=event_type,
            article=article,
            category_id=category_id,
            timestamp=timestamp,
            session_id=session_id,
        )


def send_to_kafka(events: Iterable[dict[str, Any]]) -> int:
    producer = KafkaProducer(
        bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS,
        value_serializer=lambda value: json.dumps(value).encode("utf-8"),
        linger_ms=25,
        batch_size=32_768,
        request_timeout_ms=10_000,
        api_version_auto_timeout_ms=10_000,
    )
    sent = 0
    for event in events:
        producer.send(settings.KAFKA_USER_EVENTS_TOPIC, event)
        sent += 1
        if sent % 5000 == 0:
            producer.flush(timeout=30)
            print(f"Sent {sent} events to Kafka...")
    producer.flush(timeout=60)
    producer.close(timeout=10)
    return sent


def parse_event_timestamp(value: str | datetime) -> datetime:
    if isinstance(value, datetime):
        return value
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def pending_event_mapping(event: dict[str, Any]) -> dict[str, Any]:
    return {
        "event_id": event["event_id"],
        "user_id": event["user_id"],
        "session_id": event["session_id"],
        "event_type": event["event_type"],
        "article_id": event["article_id"],
        "category_id": event["category_id"],
        "timestamp": parse_event_timestamp(event["timestamp"]),
        "device": event["device"],
        "metadata_json": event["metadata"],
        "error": "generated_to_db",
    }


def write_to_db(events: Iterable[dict[str, Any]], chunk_size: int = 2000) -> int:
    inserted = 0
    chunk: list[dict[str, Any]] = []
    with SessionLocal() as db:
        for event in events:
            chunk.append(pending_event_mapping(event))
            if len(chunk) >= chunk_size:
                db.bulk_insert_mappings(PendingEvent, chunk)
                db.commit()
                inserted += len(chunk)
                chunk.clear()
                print(f"Inserted {inserted} events into pending_events...")

        if chunk:
            db.bulk_insert_mappings(PendingEvent, chunk)
            db.commit()
            inserted += len(chunk)
    return inserted


def send_to_kafka_and_db(events: Iterable[dict[str, Any]], chunk_size: int = 2000) -> tuple[int, int]:
    producer = KafkaProducer(
        bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS,
        value_serializer=lambda value: json.dumps(value).encode("utf-8"),
        linger_ms=25,
        batch_size=32_768,
        request_timeout_ms=10_000,
        api_version_auto_timeout_ms=10_000,
    )
    sent = 0
    inserted = 0
    chunk: list[dict[str, Any]] = []

    with SessionLocal() as db:
        for event in events:
            producer.send(settings.KAFKA_USER_EVENTS_TOPIC, event)
            sent += 1
            chunk.append(pending_event_mapping(event))

            if len(chunk) >= chunk_size:
                db.bulk_insert_mappings(PendingEvent, chunk)
                db.commit()
                inserted += len(chunk)
                chunk.clear()

            if sent % 5000 == 0:
                producer.flush(timeout=30)
                print(f"Generated {sent} events to Kafka and DB...")

        if chunk:
            db.bulk_insert_mappings(PendingEvent, chunk)
            db.commit()
            inserted += len(chunk)

    producer.flush(timeout=60)
    producer.close(timeout=10)
    return sent, inserted


def write_jsonl(events: Iterable[dict[str, Any]], output_path: Path) -> int:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    written = 0
    with output_path.open("w", encoding="utf-8") as output_file:
        for event in events:
            output_file.write(json.dumps(event) + "\n")
            written += 1
    return written


def prepare_db_references(users: int, articles: int) -> tuple[list[int], list[tuple[int, int]]]:
    ensure_seed_data(users=users, articles=articles)
    with SessionLocal() as db:
        user_ids = load_user_ids(db, users)
        article_rows = load_articles(db, articles)
    if not user_ids:
        raise RuntimeError("No users found for event generation.")
    if not article_rows:
        raise RuntimeError("No articles found for event generation.")
    return user_ids, article_rows


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate realistic SmartFeed user events.")
    parser.add_argument("--users", type=positive_int, default=500)
    parser.add_argument("--articles", type=positive_int, default=1000)
    parser.add_argument("--events", type=positive_int, default=100000)
    parser.add_argument("--to-kafka", action="store_true", help="Send generated events to Kafka.")
    parser.add_argument("--to-db", action="store_true", help="Insert generated events into pending_events.")
    parser.add_argument("--fallback", action="store_true", help="Use fallback scale: 100 users, 200 articles, 10000 events.")
    parser.add_argument("--output", type=Path, default=default_data_dir() / "generated_events.jsonl")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    users = 100 if args.fallback else args.users
    articles_count = 200 if args.fallback else args.articles
    events_count = 10000 if args.fallback else args.events

    if args.to_db or args.to_kafka:
        user_ids, articles = prepare_db_references(users=users, articles=articles_count)
    else:
        user_ids = synthetic_user_ids(users)
        articles = synthetic_articles(articles_count)

    if args.to_kafka and args.to_db:
        sent, inserted = send_to_kafka_and_db(generate_events(user_ids, articles, events_count))
        print(f"Kafka+DB generation completed: sent_events={sent}, inserted_events={inserted}.")
    elif args.to_kafka:
        sent = send_to_kafka(generate_events(user_ids, articles, events_count))
        print(f"Kafka generation completed: sent_events={sent}.")
    elif args.to_db:
        inserted = write_to_db(generate_events(user_ids, articles, events_count))
        print(f"DB generation completed: inserted_events={inserted}.")
    else:
        written = write_jsonl(
            events=generate_events(user_ids, articles, events_count),
            output_path=args.output,
        )
        print(f"JSONL generation completed: written_events={written}, output={args.output}.")


if __name__ == "__main__":
    main()
