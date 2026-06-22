from typing import Annotated

from fastapi import APIRouter, Depends, Query
from pymongo.database import Database
from sqlalchemy.orm import Session

from app.api.deps import current_admin, current_user
from app.core.database import get_db
from app.core.mongodb import get_mongo_database
from app.models import User
from app.schemas.analytics import GlobalAnalytics, PersonalAnalytics
from app.services.analytics_service import AnalyticsService


router = APIRouter(prefix="/api/v1/analytics", tags=["analytics"])


@router.get("/me", response_model=PersonalAnalytics)
def get_my_analytics(
    user: Annotated[User, Depends(current_user)],
    db: Annotated[Session, Depends(get_db)],
    mongo_database: Annotated[Database, Depends(get_mongo_database)],
    limit: int = Query(default=5, ge=1, le=50),
) -> PersonalAnalytics:
    return AnalyticsService(db=db, mongo_database=mongo_database).get_personal_analytics(
        user=user,
        limit=limit,
    )


@router.get("/global", response_model=GlobalAnalytics)
def get_global_analytics(
    _: Annotated[User, Depends(current_admin)],
    db: Annotated[Session, Depends(get_db)],
    mongo_database: Annotated[Database, Depends(get_mongo_database)],
    limit: int = Query(default=10, ge=1, le=100),
    days: int = Query(default=30, ge=1, le=365),
) -> GlobalAnalytics:
    return AnalyticsService(db=db, mongo_database=mongo_database).get_global_analytics(
        limit=limit,
        days=days,
    )
