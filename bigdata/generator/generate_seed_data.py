from __future__ import annotations

import argparse
import json
import random
import re
from datetime import datetime, timedelta, timezone
from pathlib import Path

from common import bootstrap_backend_path, default_data_dir, positive_int

bootstrap_backend_path()

from app.core.database import SessionLocal
from app.core.security import get_password_hash
from app.db.init_db import init_db
from app.models import Article, Category, User
from sqlalchemy import func, select
from sqlalchemy.orm import Session


CATEGORIES = [
    ("Artificial Intelligence", "ai", "AI systems, LLMs, agents, and product use cases."),
    ("Machine Learning", "machine-learning", "Classical ML, MLOps, ranking, and model evaluation."),
    ("Backend", "backend", "APIs, services, Python, databases, and distributed systems."),
    ("Frontend", "frontend", "Web interfaces, browser performance, and design systems."),
    ("Mobile", "mobile", "Android, iOS, offline sync, and mobile UX."),
    ("Big Data", "big-data", "Kafka, Spark, batch processing, and data platforms."),
    ("Data Engineering", "data-engineering", "Pipelines, warehouses, quality, and orchestration."),
    ("DevOps", "devops", "CI/CD, observability, containers, and release automation."),
    ("Cloud", "cloud", "Cloud architecture, managed services, reliability, and cost control."),
    ("Cybersecurity", "cybersecurity", "Application security, identity, privacy, and threat modeling."),
    ("Databases", "databases", "PostgreSQL, MongoDB, indexing, transactions, and query tuning."),
    ("Architecture", "architecture", "System design, scalability, integration, and tradeoffs."),
]

ARTICLE_TOPICS = [
    "event-driven mobile analytics",
    "recommendation systems for IT content",
    "FastAPI service boundaries",
    "PostgreSQL indexing for feeds",
    "Kafka producer reliability",
    "Spark Structured Streaming windows",
    "MongoDB raw event storage",
    "Android offline sync queues",
    "feature stores for personalization",
    "observability for data platforms",
    "secure API design",
    "cloud cost control for prototypes",
    "batch analytics with Spark SQL",
    "ranking content for cold start users",
    "database schema evolution",
    "mobile UX for saved articles",
]


def slugify(value: str) -> str:
    normalized = value.lower().strip()
    normalized = re.sub(r"[^a-z0-9]+", "-", normalized)
    return normalized.strip("-")


def get_or_create_category(
    db: Session,
    name: str,
    slug: str,
    description: str,
) -> Category:
    category = db.scalar(select(Category).where(Category.slug == slug))
    if category is not None:
        return category

    category = Category(name=name, slug=slug, description=description)
    db.add(category)
    db.flush()
    return category


def ensure_categories(db: Session) -> list[Category]:
    return [
        get_or_create_category(db, name=name, slug=slug, description=description)
        for name, slug, description in CATEGORIES
    ]


def count_demo_users(db: Session) -> int:
    statement = select(func.count(User.id)).where(User.role == "user", User.is_active.is_(True))
    return int(db.scalar(statement) or 0)


def ensure_users(db: Session, target_users: int) -> int:
    created = 0
    existing_count = count_demo_users(db)
    candidate_index = 1
    password_hash = get_password_hash("password123")

    while existing_count < target_users:
        email = f"generated.user{candidate_index:04d}@smartfeed.local"
        user = db.scalar(select(User).where(User.email == email))
        if user is None:
            db.add(
                User(
                    email=email,
                    full_name=f"Generated User {candidate_index:04d}",
                    password_hash=password_hash,
                    role="user",
                    is_active=True,
                )
            )
            created += 1
            existing_count += 1
        candidate_index += 1

    db.flush()
    return created


def count_articles(db: Session) -> int:
    return int(db.scalar(select(func.count(Article.id))) or 0)


def ensure_articles(db: Session, target_articles: int, categories: list[Category]) -> int:
    created = 0
    existing_count = count_articles(db)
    candidate_index = 1
    now = datetime.now(timezone.utc)

    while existing_count < target_articles:
        category = categories[(candidate_index - 1) % len(categories)]
        topic = ARTICLE_TOPICS[(candidate_index - 1) % len(ARTICLE_TOPICS)]
        title = f"{category.name}: {topic.title()} Demo #{candidate_index:04d}"
        slug = slugify(title)
        source_url = f"https://example.com/smartfeed/generated/{candidate_index:04d}-{slug}"

        article = db.scalar(select(Article).where(Article.source_url == source_url))
        if article is None:
            published_at = now - timedelta(days=candidate_index % 30, minutes=candidate_index * 7 % 1440)
            db.add(
                Article(
                    title=title,
                    summary=(
                        f"Generated SmartFeed article about {topic} for realistic "
                        "feed, recommendation, and analytics demos."
                    ),
                    content=(
                        f"{title}\n\n"
                        "This generated article is part of the SmartFeed synthetic "
                        "dataset. It is linked to a real category so generated events "
                        "can preserve article-category consistency."
                    ),
                    source_url=source_url,
                    category_id=category.id,
                    author=random.choice(["SmartFeed Lab", "Data Desk", "Mobile Engineering"]),
                    published_at=published_at,
                    popularity_score=round(random.uniform(1.0, 100.0), 2),
                )
            )
            created += 1
            existing_count += 1
        candidate_index += 1

    db.flush()
    return created


def ensure_seed_data(users: int, articles: int) -> tuple[int, int]:
    init_db()
    with SessionLocal() as db:
        categories = ensure_categories(db)
        created_users = ensure_users(db, users)
        created_articles = ensure_articles(db, articles, categories)
        db.commit()
    return created_users, created_articles


def write_seed_files(users: int, articles: int, output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    users_path = output_dir / "generated_users.jsonl"
    articles_path = output_dir / "generated_articles.jsonl"

    with users_path.open("w", encoding="utf-8") as users_file:
        for index in range(1, users + 1):
            users_file.write(
                json.dumps(
                    {
                        "email": f"generated.user{index:04d}@smartfeed.local",
                        "full_name": f"Generated User {index:04d}",
                        "role": "user",
                    }
                )
                + "\n"
            )

    with articles_path.open("w", encoding="utf-8") as articles_file:
        for index in range(1, articles + 1):
            category_name, category_slug, _ = CATEGORIES[(index - 1) % len(CATEGORIES)]
            topic = ARTICLE_TOPICS[(index - 1) % len(ARTICLE_TOPICS)]
            title = f"{category_name}: {topic.title()} Demo #{index:04d}"
            articles_file.write(
                json.dumps(
                    {
                        "title": title,
                        "category_slug": category_slug,
                        "source_url": f"https://example.com/smartfeed/generated/{index:04d}-{slugify(title)}",
                    }
                )
                + "\n"
            )

    print(f"Wrote seed JSONL files: {users_path}, {articles_path}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate SmartFeed seed users and articles.")
    parser.add_argument("--users", type=positive_int, default=500)
    parser.add_argument("--articles", type=positive_int, default=1000)
    parser.add_argument("--events", type=positive_int, default=100000, help="Accepted for stage CLI compatibility; not used by this script.")
    parser.add_argument("--to-kafka", action="store_true", help="Accepted for stage CLI compatibility; not used by this script.")
    parser.add_argument("--to-db", action="store_true", help="Insert generated seed data into PostgreSQL.")
    parser.add_argument("--fallback", action="store_true", help="Use fallback scale: 100 users, 200 articles.")
    parser.add_argument("--output-dir", type=Path, default=default_data_dir())
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    users = 100 if args.fallback else args.users
    articles = 200 if args.fallback else args.articles

    if args.to_kafka:
        print("generate_seed_data.py does not send data to Kafka; use generate_events.py.")

    if args.to_db:
        created_users, created_articles = ensure_seed_data(users=users, articles=articles)
        print(
            "Seed generation completed: "
            f"target_users={users}, target_articles={articles}, "
            f"created_users={created_users}, created_articles={created_articles}."
        )
        return

    write_seed_files(users=users, articles=articles, output_dir=args.output_dir)


if __name__ == "__main__":
    main()
