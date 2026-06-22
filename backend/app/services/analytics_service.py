from __future__ import annotations

from pymongo.database import Database
from sqlalchemy.orm import Session

from app.models import User
from app.repositories.analytics_repo import AnalyticsRepository
from app.schemas.analytics import GlobalAnalytics, PersonalAnalytics


class AnalyticsService:
    def __init__(self, db: Session, mongo_database: Database | None = None) -> None:
        self.analytics = AnalyticsRepository(db=db, mongo_database=mongo_database)

    def get_personal_analytics(self, user: User, limit: int) -> PersonalAnalytics:
        # PostgreSQL is the synchronous audit source used by the mobile client.
        # MongoDB remains the Big Data source for global/processed analytics.
        payload = self.analytics.get_personal_from_postgres(user_id=user.id, limit=limit)
        return PersonalAnalytics(**payload)

    def get_global_analytics(self, limit: int, days: int) -> GlobalAnalytics:
        if self.analytics.mongo_events_available():
            payload = self.analytics.get_global_from_mongo(limit=limit, days=days)
        else:
            payload = self.analytics.get_global_from_postgres(limit=limit, days=days)
        return GlobalAnalytics(**payload)
