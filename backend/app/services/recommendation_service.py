from __future__ import annotations

from sqlalchemy.orm import Session

from app.models import User
from app.repositories.recommendation_repo import RecommendationRepository
from app.schemas.recommendation import RecommendationPage, RecommendationRead


class RecommendationService:
    def __init__(self, db: Session) -> None:
        self.recommendations = RecommendationRepository(db)

    def list_my_recommendations(
        self,
        user: User,
        limit: int,
        offset: int,
    ) -> RecommendationPage:
        recommendations, total = self.recommendations.list_for_user(
            user_id=user.id,
            limit=100,
            offset=0,
        )
        affinities = self.recommendations.category_affinities(user.id)
        items: list[RecommendationRead] = []
        for recommendation in recommendations:
            item = RecommendationRead.model_validate(recommendation)
            category_id = recommendation.article.category_id
            boost = affinities.get(category_id, 0.0)
            if boost > 0:
                item = item.model_copy(
                    update={
                        "score": round(item.score + boost, 4),
                        "reason": "personalized_recent_activity",
                    }
                )
            items.append(item)
        items.sort(key=lambda item: (-item.score, item.article_id))
        return RecommendationPage(
            items=items[offset : offset + limit],
            total=total,
            limit=limit,
            offset=offset,
        )
