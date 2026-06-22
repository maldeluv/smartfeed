from __future__ import annotations

from datetime import date

from pydantic import BaseModel


class FavoriteCategoryAnalytics(BaseModel):
    category_id: int
    category_name: str | None = None
    score: float
    events_count: int
    views_count: int
    likes_count: int
    saves_count: int


class PersonalAnalytics(BaseModel):
    source: str
    favorite_categories: list[FavoriteCategoryAnalytics]
    views_count: int
    likes_count: int
    saves_count: int
    recommendations_opened: int
    recommended_articles_opened: int
    recommendation_ctr: float


class EventTypeAnalytics(BaseModel):
    event_type: str
    events_count: int
    unique_users_count: int


class TopCategoryAnalytics(BaseModel):
    category_id: int
    category_name: str | None = None
    events_count: int
    unique_users_count: int
    score: float


class TopArticleAnalytics(BaseModel):
    article_id: int
    title: str | None = None
    category_id: int | None = None
    category_name: str | None = None
    views_count: int
    likes_count: int
    saves_count: int
    opens_from_recommendations_count: int
    score: float


class DailyActivityAnalytics(BaseModel):
    date: date
    events_count: int
    unique_users_count: int


class GlobalAnalytics(BaseModel):
    source: str
    top_categories: list[TopCategoryAnalytics]
    top_articles: list[TopArticleAnalytics]
    events_by_type: list[EventTypeAnalytics]
    activity_by_day: list[DailyActivityAnalytics]
    recommendations_opened: int
    recommended_articles_opened: int
    recommendation_ctr: float
