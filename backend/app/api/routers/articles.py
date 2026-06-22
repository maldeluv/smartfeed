from typing import Annotated

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.api.deps import current_user, optional_current_user
from app.core.database import get_db
from app.models import User
from app.schemas.article import ArticleDetail, ArticlePage
from app.schemas.event import StatusResponse
from app.services.content_service import ContentService
from app.services.interaction_service import InteractionService


router = APIRouter(prefix="/api/v1/articles", tags=["articles"])


@router.get("", response_model=ArticlePage)
def list_articles(
    db: Annotated[Session, Depends(get_db)],
    user: Annotated[User | None, Depends(optional_current_user)],
    category_id: int | None = Query(default=None, ge=1),
    search: str | None = Query(default=None, min_length=1, max_length=120),
    limit: int = Query(default=20, ge=1, le=100),
    offset: int = Query(default=0, ge=0),
) -> ArticlePage:
    articles, total = ContentService(db).list_articles(
        category_id=category_id,
        search=search,
        limit=limit,
        offset=offset,
        user=user,
    )
    return ArticlePage(items=articles, total=total, limit=limit, offset=offset)


@router.get("/{article_id}", response_model=ArticleDetail)
def get_article(
    article_id: int,
    db: Annotated[Session, Depends(get_db)],
    user: Annotated[User | None, Depends(optional_current_user)],
) -> ArticleDetail:
    return ContentService(db).get_article(article_id, user=user)


@router.post("/{article_id}/like", response_model=StatusResponse)
def like_article(
    article_id: int,
    user: Annotated[User, Depends(current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> StatusResponse:
    return InteractionService(db).like_article(user=user, article_id=article_id)


@router.delete("/{article_id}/like", response_model=StatusResponse)
def unlike_article(
    article_id: int,
    user: Annotated[User, Depends(current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> StatusResponse:
    return InteractionService(db).unlike_article(user=user, article_id=article_id)


@router.post("/{article_id}/save", response_model=StatusResponse)
def save_article(
    article_id: int,
    user: Annotated[User, Depends(current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> StatusResponse:
    return InteractionService(db).save_article(user=user, article_id=article_id)


@router.delete("/{article_id}/save", response_model=StatusResponse)
def unsave_article(
    article_id: int,
    user: Annotated[User, Depends(current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> StatusResponse:
    return InteractionService(db).unsave_article(user=user, article_id=article_id)
