from __future__ import annotations

from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session, selectinload

from app.models import Article
from app.schemas.article import ArticleCreate, ArticleUpdate


class ArticleRepository:
    def __init__(self, db: Session) -> None:
        self.db = db

    def list_articles(
        self,
        category_id: int | None = None,
        search: str | None = None,
        limit: int = 20,
        offset: int = 0,
    ) -> tuple[list[Article], int]:
        filters = []
        if category_id is not None:
            filters.append(Article.category_id == category_id)
        if search:
            search_pattern = f"%{search.strip()}%"
            filters.append(
                or_(
                    Article.title.ilike(search_pattern),
                    Article.summary.ilike(search_pattern),
                    Article.content.ilike(search_pattern),
                )
            )

        statement = (
            select(Article)
            .options(selectinload(Article.category))
            .order_by(Article.published_at.desc(), Article.id.desc())
            .limit(limit)
            .offset(offset)
        )
        count_statement = select(func.count(Article.id))

        if filters:
            statement = statement.where(*filters)
            count_statement = count_statement.where(*filters)

        articles = list(self.db.scalars(statement).all())
        total = int(self.db.scalar(count_statement) or 0)
        return articles, total

    def get_article(self, article_id: int) -> Article | None:
        statement = (
            select(Article)
            .options(selectinload(Article.category))
            .where(Article.id == article_id)
        )
        return self.db.scalar(statement)

    def create_article(self, payload: ArticleCreate) -> Article:
        article = Article(**payload.model_dump())
        self.db.add(article)
        self.db.flush()
        return article

    def update_article(self, article: Article, payload: ArticleUpdate) -> Article:
        update_data = payload.model_dump(exclude_unset=True)
        for field_name, value in update_data.items():
            setattr(article, field_name, value)
        self.db.flush()
        return article

    def delete_article(self, article: Article) -> None:
        self.db.delete(article)
        self.db.flush()
