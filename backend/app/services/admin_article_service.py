from __future__ import annotations

from fastapi import HTTPException, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models import Article
from app.repositories.article_repo import ArticleRepository
from app.repositories.category_repo import CategoryRepository
from app.schemas.article import ArticleCreate, ArticleUpdate


class AdminArticleService:
    def __init__(self, db: Session) -> None:
        self.db = db
        self.articles = ArticleRepository(db)
        self.categories = CategoryRepository(db)

    def create_article(self, payload: ArticleCreate) -> Article:
        self._ensure_category(payload.category_id)
        try:
            article = self.articles.create_article(payload)
            self.db.commit()
        except IntegrityError as exc:
            self.db.rollback()
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Article conflicts with existing data",
            ) from exc
        return self._get_article_or_404(article.id)

    def update_article(self, article_id: int, payload: ArticleUpdate) -> Article:
        article = self._get_article_or_404(article_id)
        if payload.category_id is not None:
            self._ensure_category(payload.category_id)
        try:
            article = self.articles.update_article(article=article, payload=payload)
            self.db.commit()
        except IntegrityError as exc:
            self.db.rollback()
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Article conflicts with existing data",
            ) from exc
        return self._get_article_or_404(article.id)

    def delete_article(self, article_id: int) -> None:
        article = self._get_article_or_404(article_id)
        try:
            self.articles.delete_article(article)
            self.db.commit()
        except IntegrityError as exc:
            self.db.rollback()
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Article cannot be deleted because it is referenced by existing data",
            ) from exc

    def _ensure_category(self, category_id: int) -> None:
        if self.categories.get_category(category_id) is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Category not found",
            )

    def _get_article_or_404(self, article_id: int) -> Article:
        article = self.articles.get_article(article_id)
        if article is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Article not found",
            )
        return article
