from __future__ import annotations

import random
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path


def add_generator_path() -> None:
    candidates = [
        Path("/app/generator"),
        Path(__file__).resolve().parents[2] / "bigdata" / "generator",
    ]
    for candidate in candidates:
        if (candidate / "generate_events.py").exists():
            sys.path.insert(0, str(candidate))
            return
    raise RuntimeError("bigdata/generator path was not found")


def test_generate_100000_events_are_valid() -> None:
    add_generator_path()

    from generate_events import (  # noqa: PLC0415
        ARTICLE_EVENT_TYPES,
        CATEGORY_EVENT_TYPES,
        generate_events,
        parse_event_timestamp,
        synthetic_articles,
        synthetic_user_ids,
    )

    random.seed(42)
    users_count = 500
    articles_count = 1000
    events_count = 100000
    user_ids = synthetic_user_ids(users_count)
    articles = synthetic_articles(articles_count)
    article_categories = dict(articles)
    category_ids = set(article_categories.values())
    event_type_counts: dict[str, int] = {}
    min_timestamp = datetime.now(timezone.utc) - timedelta(days=31)
    max_timestamp = datetime.now(timezone.utc) + timedelta(minutes=1)

    generated_count = 0
    for event in generate_events(user_ids=user_ids, articles=articles, total_events=events_count):
        generated_count += 1
        event_type = event["event_type"]
        event_type_counts[event_type] = event_type_counts.get(event_type, 0) + 1

        assert event["event_id"]
        assert event["user_id"] in user_ids
        assert event["session_id"]
        assert event["metadata"] is not None
        assert event["device"]["platform"] == "android"

        timestamp = parse_event_timestamp(event["timestamp"])
        assert min_timestamp <= timestamp <= max_timestamp

        if event_type in ARTICLE_EVENT_TYPES:
            assert event["article_id"] in article_categories
            assert event["category_id"] == article_categories[event["article_id"]]
        elif event_type in CATEGORY_EVENT_TYPES:
            assert event["article_id"] is None
            assert event["category_id"] in category_ids
        else:
            assert event["article_id"] is None

    assert generated_count == events_count
    assert len(event_type_counts) >= 8
