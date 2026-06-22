from typing import Annotated

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.api.deps import current_user
from app.core.database import get_db
from app.models import User
from app.schemas.article import ArticlePage
from app.services.interaction_service import InteractionService


router = APIRouter(prefix="/api/v1/saved", tags=["saved"])


@router.get("", response_model=ArticlePage)
def list_saved_articles(
    user: Annotated[User, Depends(current_user)],
    db: Annotated[Session, Depends(get_db)],
    limit: int = Query(default=20, ge=1, le=100),
    offset: int = Query(default=0, ge=0),
) -> ArticlePage:
    return InteractionService(db).list_saved_articles(user=user, limit=limit, offset=offset)
