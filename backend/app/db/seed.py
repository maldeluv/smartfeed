from __future__ import annotations

import re
from datetime import datetime, timedelta, timezone

from passlib.context import CryptContext
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.database import SessionLocal
from app.db.init_db import init_db
from app.models import Article, Category, User


pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

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
    "building production APIs with FastAPI",
    "designing PostgreSQL indexes for real feeds",
    "using Kafka as an event backbone",
    "processing user activity with Spark",
    "storing raw events in MongoDB",
    "ranking articles for a cold-start user",
    "keeping Android actions offline-first",
    "observability basics for data products",
    "schema design for recommendation systems",
    "testing data pipelines locally",
    "scaling a mobile content backend",
    "turning interaction events into analytics",
]


def slugify(value: str) -> str:
    normalized = value.lower().strip()
    normalized = re.sub(r"[^a-z0-9]+", "-", normalized)
    return normalized.strip("-")


def password_hash(password: str) -> str:
    return pwd_context.hash(password)


def get_or_create_user(
    db: Session,
    email: str,
    full_name: str,
    role: str,
    password: str,
) -> User:
    user = db.scalar(select(User).where(User.email == email))
    if user is not None:
        return user

    user = User(
        email=email,
        full_name=full_name,
        role=role,
        password_hash=password_hash(password),
        is_active=True,
    )
    db.add(user)
    db.flush()
    return user


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


def create_users(db: Session) -> None:
    get_or_create_user(
        db=db,
        email="admin@smartfeed.local",
        full_name="SmartFeed Admin",
        role="admin",
        password="admin12345",
    )
    get_or_create_user(
        db=db,
        email="demo@smartfeed.local",
        full_name="Demo User",
        role="user",
        password="demo12345",
    )


def create_categories(db: Session) -> list[Category]:
    return [
        get_or_create_category(db, name=name, slug=slug, description=description)
        for name, slug, description in CATEGORIES
    ]


def create_articles(db: Session, categories: list[Category]) -> int:
    created_count = 0
    now = datetime.now(timezone.utc)

    for index in range(1, 201):
        category = categories[(index - 1) % len(categories)]
        topic = ARTICLE_TOPICS[(index - 1) % len(ARTICLE_TOPICS)]
        title = f"{category.name}: {topic.title()} #{index:03d}"
        slug = slugify(title)
        source_url = f"https://example.com/smartfeed/articles/{slug}"

        article = db.scalar(select(Article).where(Article.source_url == source_url))
        if article is not None:
            continue

        published_at = now - timedelta(days=index % 60, hours=index % 24)
        article = Article(
            title=title,
            summary=(
                f"A practical SmartFeed article about {topic} for engineers "
                "working with mobile products and data platforms."
            ),
            content=(
                f"{title}\n\n"
                "This seed article is demo content for the SmartFeed training project. "
                "It explains the core idea, implementation context, data impact, "
                "and practical tradeoffs for a realistic recommendation system."
            ),
            source_url=source_url,
            category_id=category.id,
            author="SmartFeed Editorial",
            published_at=published_at,
            popularity_score=round(100.0 - (index * 0.37 % 80), 2),
        )
        db.add(article)
        created_count += 1

    db.flush()
    return created_count


def seed() -> None:
    init_db()
    with SessionLocal() as db:
        create_users(db)
        categories = create_categories(db)
        created_articles = create_articles(db, categories)
        db.commit()

    print(
        "Seed completed: 2 users, "
        f"{len(categories)} categories, {created_articles} new articles."
    )


if __name__ == "__main__":
    seed()
