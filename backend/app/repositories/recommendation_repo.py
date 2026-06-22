from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session, selectinload

from app.models import Article, ArticleLike, Recommendation, SavedArticle


class RecommendationRepository:
    def __init__(self, db: Session) -> None:
        self.db = db

    def list_for_user(
        self,
        user_id: int,
        limit: int,
        offset: int,
    ) -> tuple[list[Recommendation], int]:
        statement = (
            select(Recommendation)
            .options(
                selectinload(Recommendation.article).selectinload(Article.category),
            )
            .where(Recommendation.user_id == user_id)
            .order_by(
                Recommendation.score.desc(),
                Recommendation.created_at.desc(),
                Recommendation.article_id.asc(),
            )
            .limit(limit)
            .offset(offset)
        )
        count_statement = select(func.count(Recommendation.id)).where(
            Recommendation.user_id == user_id
        )
        recommendations = list(self.db.scalars(statement).all())
        total = int(self.db.scalar(count_statement) or 0)
        return recommendations, total

    def category_affinities(self, user_id: int) -> dict[int, float]:
        affinities: dict[int, float] = {}
        like_statement = (
            select(Article.category_id, func.count(ArticleLike.id))
            .join(Article, Article.id == ArticleLike.article_id)
            .where(ArticleLike.user_id == user_id)
            .group_by(Article.category_id)
        )
        save_statement = (
            select(Article.category_id, func.count(SavedArticle.id))
            .join(Article, Article.id == SavedArticle.article_id)
            .where(SavedArticle.user_id == user_id)
            .group_by(Article.category_id)
        )
        for category_id, count in self.db.execute(like_statement):
            affinities[int(category_id)] = affinities.get(int(category_id), 0.0) + float(count) * 3.0
        for category_id, count in self.db.execute(save_statement):
            affinities[int(category_id)] = affinities.get(int(category_id), 0.0) + float(count) * 5.0
        return affinities
