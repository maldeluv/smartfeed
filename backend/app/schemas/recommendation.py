from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict

from app.schemas.article import ArticleListItem


class RecommendationRead(BaseModel):
    id: int
    user_id: int
    article_id: int
    score: float
    reason: str | None = None
    model_version: str
    created_at: datetime
    article: ArticleListItem

    model_config = ConfigDict(from_attributes=True)


class RecommendationPage(BaseModel):
    items: list[RecommendationRead]
    total: int
    limit: int
    offset: int
