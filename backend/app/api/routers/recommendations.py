from typing import Annotated

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.api.deps import current_user
from app.core.database import get_db
from app.models import User
from app.schemas.recommendation import RecommendationPage
from app.services.recommendation_service import RecommendationService


router = APIRouter(prefix="/api/v1/recommendations", tags=["recommendations"])


@router.get("", response_model=RecommendationPage)
@router.get("/me", response_model=RecommendationPage)
def list_my_recommendations(
    user: Annotated[User, Depends(current_user)],
    db: Annotated[Session, Depends(get_db)],
    limit: int = Query(default=20, ge=1, le=100),
    offset: int = Query(default=0, ge=0),
) -> RecommendationPage:
    return RecommendationService(db).list_my_recommendations(
        user=user,
        limit=limit,
        offset=offset,
    )
