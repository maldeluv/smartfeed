from __future__ import annotations

from datetime import date, datetime, timedelta, timezone
from typing import Any

from pymongo.database import Database
from pymongo.errors import PyMongoError
from sqlalchemy import case, distinct, func, select
from sqlalchemy.orm import Session

from app.core.config import settings
from app.models import Article, Category, PendingEvent, UserCategoryScore


EVENT_SCORE_WEIGHTS = {
    "view_article": 1.0,
    "like_article": 3.0,
    "save_article": 5.0,
    "open_recommended_article": 2.0,
}


class AnalyticsRepository:
    def __init__(self, db: Session, mongo_database: Database | None = None) -> None:
        self.db = db
        self.mongo_collection = (
            mongo_database[settings.MONGO_RAW_EVENTS_COLLECTION]
            if mongo_database is not None
            else None
        )

    def mongo_events_available(self) -> bool:
        if self.mongo_collection is None:
            return False
        try:
            return self.mongo_collection.count_documents({}, limit=1) > 0
        except PyMongoError:
            return False

    def get_personal_from_mongo(self, user_id: int, limit: int) -> dict[str, Any]:
        counts = self._mongo_event_counts(match={"user_id": user_id})
        return {
            "source": "mongo.raw_user_events",
            "favorite_categories": self._personal_favorite_categories_mongo(
                user_id=user_id,
                limit=limit,
            ),
            "views_count": counts.get("view_article", 0),
            "likes_count": counts.get("like_article", 0),
            "saves_count": counts.get("save_article", 0),
            "recommendations_opened": counts.get("open_recommendations", 0),
            "recommended_articles_opened": counts.get("open_recommended_article", 0),
            "recommendation_ctr": self._ctr(
                opened=counts.get("open_recommendations", 0),
                clicked=counts.get("open_recommended_article", 0),
            ),
        }

    def get_personal_from_postgres(self, user_id: int, limit: int) -> dict[str, Any]:
        counts = self._postgres_event_counts(user_id=user_id)
        score_totals = self._postgres_user_score_totals(user_id=user_id)
        views_count = counts.get("view_article", 0) or score_totals["views_count"]
        likes_count = counts.get("like_article", 0) or score_totals["likes_count"]
        saves_count = counts.get("save_article", 0) or score_totals["saves_count"]

        favorite_categories = self._personal_favorite_categories_postgres(
            user_id=user_id,
            limit=limit,
        )
        if not favorite_categories:
            favorite_categories = self._personal_favorite_categories_from_scores(
                user_id=user_id,
                limit=limit,
            )

        return {
            "source": "postgres.pending_events",
            "favorite_categories": favorite_categories,
            "views_count": views_count,
            "likes_count": likes_count,
            "saves_count": saves_count,
            "recommendations_opened": counts.get("open_recommendations", 0),
            "recommended_articles_opened": counts.get("open_recommended_article", 0),
            "recommendation_ctr": self._ctr(
                opened=counts.get("open_recommendations", 0),
                clicked=counts.get("open_recommended_article", 0),
            ),
        }

    def get_global_from_mongo(self, limit: int, days: int) -> dict[str, Any]:
        counts = self._mongo_event_counts(match={})
        return {
            "source": "mongo.raw_user_events",
            "top_categories": self._top_categories_mongo(limit=limit),
            "top_articles": self._top_articles_mongo(limit=limit),
            "events_by_type": self._events_by_type_mongo(),
            "activity_by_day": self._activity_by_day_mongo(days=days),
            "recommendations_opened": counts.get("open_recommendations", 0),
            "recommended_articles_opened": counts.get("open_recommended_article", 0),
            "recommendation_ctr": self._ctr(
                opened=counts.get("open_recommendations", 0),
                clicked=counts.get("open_recommended_article", 0),
            ),
        }

    def get_global_from_postgres(self, limit: int, days: int) -> dict[str, Any]:
        counts = self._postgres_event_counts()
        return {
            "source": "postgres.pending_events",
            "top_categories": self._top_categories_postgres(limit=limit),
            "top_articles": self._top_articles_postgres(limit=limit),
            "events_by_type": self._events_by_type_postgres(),
            "activity_by_day": self._activity_by_day_postgres(days=days),
            "recommendations_opened": counts.get("open_recommendations", 0),
            "recommended_articles_opened": counts.get("open_recommended_article", 0),
            "recommendation_ctr": self._ctr(
                opened=counts.get("open_recommendations", 0),
                clicked=counts.get("open_recommended_article", 0),
            ),
        }

    def _personal_favorite_categories_mongo(self, user_id: int, limit: int) -> list[dict[str, Any]]:
        pipeline = [
            {"$match": {"user_id": user_id, "category_id": {"$ne": None}}},
            {
                "$group": {
                    "_id": "$category_id",
                    "events_count": {"$sum": 1},
                    "views_count": self._mongo_sum_event("view_article"),
                    "likes_count": self._mongo_sum_event("like_article"),
                    "saves_count": self._mongo_sum_event("save_article"),
                    "score": self._mongo_sum_score(),
                }
            },
            {"$sort": {"score": -1, "events_count": -1, "_id": 1}},
            {"$limit": limit},
        ]
        rows = list(self.mongo_collection.aggregate(pipeline)) if self.mongo_collection is not None else []
        category_names = self._category_name_map([int(row["_id"]) for row in rows])
        return [
            {
                "category_id": int(row["_id"]),
                "category_name": category_names.get(int(row["_id"])),
                "score": float(row.get("score") or 0.0),
                "events_count": int(row.get("events_count") or 0),
                "views_count": int(row.get("views_count") or 0),
                "likes_count": int(row.get("likes_count") or 0),
                "saves_count": int(row.get("saves_count") or 0),
            }
            for row in rows
        ]

    def _top_categories_mongo(self, limit: int) -> list[dict[str, Any]]:
        pipeline = [
            {"$match": {"category_id": {"$ne": None}}},
            {
                "$group": {
                    "_id": "$category_id",
                    "events_count": {"$sum": 1},
                    "users": {"$addToSet": "$user_id"},
                    "score": self._mongo_sum_score(),
                }
            },
            {"$project": {"events_count": 1, "score": 1, "unique_users_count": {"$size": "$users"}}},
            {"$sort": {"score": -1, "events_count": -1, "_id": 1}},
            {"$limit": limit},
        ]
        rows = list(self.mongo_collection.aggregate(pipeline)) if self.mongo_collection is not None else []
        category_names = self._category_name_map([int(row["_id"]) for row in rows])
        return [
            {
                "category_id": int(row["_id"]),
                "category_name": category_names.get(int(row["_id"])),
                "events_count": int(row.get("events_count") or 0),
                "unique_users_count": int(row.get("unique_users_count") or 0),
                "score": float(row.get("score") or 0.0),
            }
            for row in rows
        ]

    def _top_articles_mongo(self, limit: int) -> list[dict[str, Any]]:
        pipeline = [
            {"$match": {"article_id": {"$ne": None}}},
            {
                "$group": {
                    "_id": "$article_id",
                    "views_count": self._mongo_sum_event("view_article"),
                    "likes_count": self._mongo_sum_event("like_article"),
                    "saves_count": self._mongo_sum_event("save_article"),
                    "opens_from_recommendations_count": self._mongo_sum_event(
                        "open_recommended_article"
                    ),
                    "score": self._mongo_sum_score(),
                }
            },
            {"$sort": {"score": -1, "views_count": -1, "_id": 1}},
            {"$limit": limit},
        ]
        rows = list(self.mongo_collection.aggregate(pipeline)) if self.mongo_collection is not None else []
        articles = self._article_map([int(row["_id"]) for row in rows])
        return [
            self._top_article_response(
                article_id=int(row["_id"]),
                article=articles.get(int(row["_id"])),
                views_count=int(row.get("views_count") or 0),
                likes_count=int(row.get("likes_count") or 0),
                saves_count=int(row.get("saves_count") or 0),
                opens_count=int(row.get("opens_from_recommendations_count") or 0),
                score=float(row.get("score") or 0.0),
            )
            for row in rows
        ]

    def _events_by_type_mongo(self) -> list[dict[str, Any]]:
        pipeline = [
            {"$match": {"event_type": {"$ne": None}}},
            {
                "$group": {
                    "_id": "$event_type",
                    "events_count": {"$sum": 1},
                    "users": {"$addToSet": "$user_id"},
                }
            },
            {"$project": {"events_count": 1, "unique_users_count": {"$size": "$users"}}},
            {"$sort": {"events_count": -1, "_id": 1}},
        ]
        rows = list(self.mongo_collection.aggregate(pipeline)) if self.mongo_collection is not None else []
        return [
            {
                "event_type": str(row["_id"]),
                "events_count": int(row.get("events_count") or 0),
                "unique_users_count": int(row.get("unique_users_count") or 0),
            }
            for row in rows
        ]

    def _activity_by_day_mongo(self, days: int) -> list[dict[str, Any]]:
        start = datetime.now(timezone.utc) - timedelta(days=days)
        pipeline = [
            {
                "$addFields": {
                    "activity_ts": {"$toDate": {"$ifNull": ["$event_timestamp", "$timestamp"]}}
                }
            },
            {"$match": {"activity_ts": {"$ne": None, "$gte": start}}},
            {
                "$group": {
                    "_id": {"$dateToString": {"format": "%Y-%m-%d", "date": "$activity_ts"}},
                    "events_count": {"$sum": 1},
                    "users": {"$addToSet": "$user_id"},
                }
            },
            {"$project": {"events_count": 1, "unique_users_count": {"$size": "$users"}}},
            {"$sort": {"_id": 1}},
        ]
        rows = list(self.mongo_collection.aggregate(pipeline)) if self.mongo_collection is not None else []
        return [
            {
                "date": date.fromisoformat(str(row["_id"])),
                "events_count": int(row.get("events_count") or 0),
                "unique_users_count": int(row.get("unique_users_count") or 0),
            }
            for row in rows
        ]

    def _mongo_event_counts(self, match: dict[str, Any]) -> dict[str, int]:
        if self.mongo_collection is None:
            return {}
        pipeline = [
            {"$match": match},
            {"$group": {"_id": "$event_type", "events_count": {"$sum": 1}}},
        ]
        return {
            str(row["_id"]): int(row.get("events_count") or 0)
            for row in self.mongo_collection.aggregate(pipeline)
            if row.get("_id") is not None
        }

    def _personal_favorite_categories_postgres(
        self,
        user_id: int,
        limit: int,
    ) -> list[dict[str, Any]]:
        score_expr = self._postgres_score_sum().label("score")
        views_expr = self._postgres_sum_event("view_article").label("views_count")
        likes_expr = self._postgres_sum_event("like_article").label("likes_count")
        saves_expr = self._postgres_sum_event("save_article").label("saves_count")
        events_expr = func.count(PendingEvent.id).label("events_count")

        statement = (
            select(
                PendingEvent.category_id,
                Category.name.label("category_name"),
                events_expr,
                views_expr,
                likes_expr,
                saves_expr,
                score_expr,
            )
            .join(Category, Category.id == PendingEvent.category_id)
            .where(PendingEvent.user_id == user_id, PendingEvent.category_id.is_not(None))
            .group_by(PendingEvent.category_id, Category.name)
            .order_by(score_expr.desc(), events_expr.desc(), PendingEvent.category_id.asc())
            .limit(limit)
        )
        return [
            {
                "category_id": int(row.category_id),
                "category_name": row.category_name,
                "score": float(row.score or 0.0),
                "events_count": int(row.events_count or 0),
                "views_count": int(row.views_count or 0),
                "likes_count": int(row.likes_count or 0),
                "saves_count": int(row.saves_count or 0),
            }
            for row in self.db.execute(statement).all()
        ]

    def _personal_favorite_categories_from_scores(
        self,
        user_id: int,
        limit: int,
    ) -> list[dict[str, Any]]:
        statement = (
            select(UserCategoryScore, Category.name.label("category_name"))
            .join(Category, Category.id == UserCategoryScore.category_id)
            .where(UserCategoryScore.user_id == user_id)
            .order_by(UserCategoryScore.score.desc(), UserCategoryScore.category_id.asc())
            .limit(limit)
        )
        rows = self.db.execute(statement).all()
        return [
            {
                "category_id": int(row.UserCategoryScore.category_id),
                "category_name": row.category_name,
                "score": float(row.UserCategoryScore.score or 0.0),
                "events_count": int(
                    (row.UserCategoryScore.views_count or 0)
                    + (row.UserCategoryScore.likes_count or 0)
                    + (row.UserCategoryScore.saves_count or 0)
                ),
                "views_count": int(row.UserCategoryScore.views_count or 0),
                "likes_count": int(row.UserCategoryScore.likes_count or 0),
                "saves_count": int(row.UserCategoryScore.saves_count or 0),
            }
            for row in rows
        ]

    def _postgres_user_score_totals(self, user_id: int) -> dict[str, int]:
        statement = select(
            func.coalesce(func.sum(UserCategoryScore.views_count), 0).label("views_count"),
            func.coalesce(func.sum(UserCategoryScore.likes_count), 0).label("likes_count"),
            func.coalesce(func.sum(UserCategoryScore.saves_count), 0).label("saves_count"),
        ).where(UserCategoryScore.user_id == user_id)
        row = self.db.execute(statement).one()
        return {
            "views_count": int(row.views_count or 0),
            "likes_count": int(row.likes_count or 0),
            "saves_count": int(row.saves_count or 0),
        }

    def _top_categories_postgres(self, limit: int) -> list[dict[str, Any]]:
        score_expr = self._postgres_score_sum().label("score")
        events_expr = func.count(PendingEvent.id).label("events_count")
        users_expr = func.count(distinct(PendingEvent.user_id)).label("unique_users_count")
        statement = (
            select(
                PendingEvent.category_id,
                Category.name.label("category_name"),
                events_expr,
                users_expr,
                score_expr,
            )
            .join(Category, Category.id == PendingEvent.category_id)
            .where(PendingEvent.category_id.is_not(None))
            .group_by(PendingEvent.category_id, Category.name)
            .order_by(score_expr.desc(), events_expr.desc(), PendingEvent.category_id.asc())
            .limit(limit)
        )
        rows = self.db.execute(statement).all()
        if rows:
            return [
                {
                    "category_id": int(row.category_id),
                    "category_name": row.category_name,
                    "events_count": int(row.events_count or 0),
                    "unique_users_count": int(row.unique_users_count or 0),
                    "score": float(row.score or 0.0),
                }
                for row in rows
            ]
        return self._top_categories_from_scores(limit=limit)

    def _top_categories_from_scores(self, limit: int) -> list[dict[str, Any]]:
        statement = (
            select(
                UserCategoryScore.category_id,
                Category.name.label("category_name"),
                func.count(UserCategoryScore.user_id).label("unique_users_count"),
                func.coalesce(func.sum(UserCategoryScore.score), 0).label("score"),
                func.coalesce(
                    func.sum(
                        UserCategoryScore.views_count
                        + UserCategoryScore.likes_count
                        + UserCategoryScore.saves_count
                    ),
                    0,
                ).label("events_count"),
            )
            .join(Category, Category.id == UserCategoryScore.category_id)
            .group_by(UserCategoryScore.category_id, Category.name)
            .order_by(func.sum(UserCategoryScore.score).desc(), UserCategoryScore.category_id.asc())
            .limit(limit)
        )
        return [
            {
                "category_id": int(row.category_id),
                "category_name": row.category_name,
                "events_count": int(row.events_count or 0),
                "unique_users_count": int(row.unique_users_count or 0),
                "score": float(row.score or 0.0),
            }
            for row in self.db.execute(statement).all()
        ]

    def _top_articles_postgres(self, limit: int) -> list[dict[str, Any]]:
        score_expr = self._postgres_score_sum().label("score")
        views_expr = self._postgres_sum_event("view_article").label("views_count")
        likes_expr = self._postgres_sum_event("like_article").label("likes_count")
        saves_expr = self._postgres_sum_event("save_article").label("saves_count")
        opens_expr = self._postgres_sum_event("open_recommended_article").label(
            "opens_from_recommendations_count"
        )
        statement = (
            select(
                PendingEvent.article_id,
                Article.title,
                Article.category_id,
                Category.name.label("category_name"),
                views_expr,
                likes_expr,
                saves_expr,
                opens_expr,
                score_expr,
            )
            .join(Article, Article.id == PendingEvent.article_id)
            .join(Category, Category.id == Article.category_id)
            .where(PendingEvent.article_id.is_not(None))
            .group_by(PendingEvent.article_id, Article.title, Article.category_id, Category.name)
            .order_by(score_expr.desc(), views_expr.desc(), PendingEvent.article_id.asc())
            .limit(limit)
        )
        rows = self.db.execute(statement).all()
        if rows:
            return [
                self._top_article_response(
                    article_id=int(row.article_id),
                    article={
                        "title": row.title,
                        "category_id": row.category_id,
                        "category_name": row.category_name,
                    },
                    views_count=int(row.views_count or 0),
                    likes_count=int(row.likes_count or 0),
                    saves_count=int(row.saves_count or 0),
                    opens_count=int(row.opens_from_recommendations_count or 0),
                    score=float(row.score or 0.0),
                )
                for row in rows
            ]
        return self._top_articles_by_seed_popularity(limit=limit)

    def _top_articles_by_seed_popularity(self, limit: int) -> list[dict[str, Any]]:
        statement = (
            select(
                Article.id,
                Article.title,
                Article.category_id,
                Category.name.label("category_name"),
                Article.popularity_score,
            )
            .join(Category, Category.id == Article.category_id)
            .order_by(Article.popularity_score.desc(), Article.published_at.desc(), Article.id.asc())
            .limit(limit)
        )
        return [
            self._top_article_response(
                article_id=int(row.id),
                article={
                    "title": row.title,
                    "category_id": row.category_id,
                    "category_name": row.category_name,
                },
                views_count=0,
                likes_count=0,
                saves_count=0,
                opens_count=0,
                score=float(row.popularity_score or 0.0),
            )
            for row in self.db.execute(statement).all()
        ]

    def _events_by_type_postgres(self) -> list[dict[str, Any]]:
        statement = (
            select(
                PendingEvent.event_type,
                func.count(PendingEvent.id).label("events_count"),
                func.count(distinct(PendingEvent.user_id)).label("unique_users_count"),
            )
            .group_by(PendingEvent.event_type)
            .order_by(func.count(PendingEvent.id).desc(), PendingEvent.event_type.asc())
        )
        return [
            {
                "event_type": row.event_type,
                "events_count": int(row.events_count or 0),
                "unique_users_count": int(row.unique_users_count or 0),
            }
            for row in self.db.execute(statement).all()
        ]

    def _activity_by_day_postgres(self, days: int) -> list[dict[str, Any]]:
        start = datetime.now(timezone.utc) - timedelta(days=days)
        event_date = func.date(PendingEvent.timestamp).label("event_date")
        statement = (
            select(
                event_date,
                func.count(PendingEvent.id).label("events_count"),
                func.count(distinct(PendingEvent.user_id)).label("unique_users_count"),
            )
            .where(PendingEvent.timestamp >= start)
            .group_by(event_date)
            .order_by(event_date.asc())
        )
        return [
            {
                "date": self._parse_date(row.event_date),
                "events_count": int(row.events_count or 0),
                "unique_users_count": int(row.unique_users_count or 0),
            }
            for row in self.db.execute(statement).all()
        ]

    def _postgres_event_counts(self, user_id: int | None = None) -> dict[str, int]:
        statement = select(
            PendingEvent.event_type,
            func.count(PendingEvent.id).label("events_count"),
        ).group_by(PendingEvent.event_type)
        if user_id is not None:
            statement = statement.where(PendingEvent.user_id == user_id)
        return {
            row.event_type: int(row.events_count or 0)
            for row in self.db.execute(statement).all()
        }

    def _category_name_map(self, category_ids: list[int]) -> dict[int, str]:
        if not category_ids:
            return {}
        statement = select(Category.id, Category.name).where(Category.id.in_(set(category_ids)))
        return {int(row.id): row.name for row in self.db.execute(statement).all()}

    def _article_map(self, article_ids: list[int]) -> dict[int, dict[str, Any]]:
        if not article_ids:
            return {}
        statement = (
            select(
                Article.id,
                Article.title,
                Article.category_id,
                Category.name.label("category_name"),
            )
            .join(Category, Category.id == Article.category_id)
            .where(Article.id.in_(set(article_ids)))
        )
        return {
            int(row.id): {
                "title": row.title,
                "category_id": int(row.category_id),
                "category_name": row.category_name,
            }
            for row in self.db.execute(statement).all()
        }

    def _top_article_response(
        self,
        article_id: int,
        article: dict[str, Any] | None,
        views_count: int,
        likes_count: int,
        saves_count: int,
        opens_count: int,
        score: float,
    ) -> dict[str, Any]:
        article = article or {}
        return {
            "article_id": article_id,
            "title": article.get("title"),
            "category_id": article.get("category_id"),
            "category_name": article.get("category_name"),
            "views_count": views_count,
            "likes_count": likes_count,
            "saves_count": saves_count,
            "opens_from_recommendations_count": opens_count,
            "score": score,
        }

    def _postgres_score_sum(self):
        expression = 0.0
        for event_type, score in EVENT_SCORE_WEIGHTS.items():
            expression = expression + case(
                (PendingEvent.event_type == event_type, score),
                else_=0.0,
            )
        return func.coalesce(func.sum(expression), 0.0)

    def _postgres_sum_event(self, event_type: str):
        return func.coalesce(
            func.sum(case((PendingEvent.event_type == event_type, 1), else_=0)),
            0,
        )

    def _mongo_sum_event(self, event_type: str) -> dict[str, Any]:
        return {"$sum": {"$cond": [{"$eq": ["$event_type", event_type]}, 1, 0]}}

    def _mongo_sum_score(self) -> dict[str, Any]:
        return {
            "$sum": {
                "$switch": {
                    "branches": [
                        {"case": {"$eq": ["$event_type", event_type]}, "then": score}
                        for event_type, score in EVENT_SCORE_WEIGHTS.items()
                    ],
                    "default": 0.0,
                }
            }
        }

    def _ctr(self, opened: int, clicked: int) -> float:
        if opened <= 0:
            return 0.0
        return round(clicked / opened, 4)

    def _parse_date(self, value: Any) -> date:
        if isinstance(value, date):
            return value
        return date.fromisoformat(str(value))
