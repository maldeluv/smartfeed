from typing import Annotated

from fastapi import APIRouter, Depends, Response, status
from sqlalchemy.orm import Session

from app.api.deps import current_admin
from app.core.database import get_db
from app.models import User
from app.schemas.article import ArticleCreate, ArticleDetail, ArticleUpdate
from app.services.admin_article_service import AdminArticleService


router = APIRouter(prefix="/api/v1/admin", tags=["admin"])


@router.post("/articles", response_model=ArticleDetail, status_code=status.HTTP_201_CREATED)
def create_article(
    payload: ArticleCreate,
    _: Annotated[User, Depends(current_admin)],
    db: Annotated[Session, Depends(get_db)],
) -> ArticleDetail:
    return AdminArticleService(db).create_article(payload)


@router.put("/articles/{article_id}", response_model=ArticleDetail)
@router.patch("/articles/{article_id}", response_model=ArticleDetail)
def update_article(
    article_id: int,
    payload: ArticleUpdate,
    _: Annotated[User, Depends(current_admin)],
    db: Annotated[Session, Depends(get_db)],
) -> ArticleDetail:
    return AdminArticleService(db).update_article(article_id=article_id, payload=payload)


@router.delete("/articles/{article_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_article(
    article_id: int,
    _: Annotated[User, Depends(current_admin)],
    db: Annotated[Session, Depends(get_db)],
) -> Response:
    AdminArticleService(db).delete_article(article_id=article_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
