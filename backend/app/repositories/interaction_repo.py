from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from app.models import Article, ArticleLike, Category, SavedArticle, UserCategorySubscription


class InteractionRepository:
    def __init__(self, db: Session) -> None:
        self.db = db

    def get_category(self, category_id: int) -> Category | None:
        return self.db.get(Category, category_id)

    def get_article(self, article_id: int) -> Article | None:
        return self.db.get(Article, article_id)

    def get_subscription(
        self,
        user_id: int,
        category_id: int,
    ) -> UserCategorySubscription | None:
        statement = select(UserCategorySubscription).where(
            UserCategorySubscription.user_id == user_id,
            UserCategorySubscription.category_id == category_id,
        )
        return self.db.scalar(statement)

    def get_subscribed_category_ids(self, user_id: int) -> set[int]:
        statement = select(UserCategorySubscription.category_id).where(
            UserCategorySubscription.user_id == user_id,
        )
        return set(self.db.scalars(statement).all())

    def subscribe(self, user_id: int, category_id: int) -> None:
        if self.get_subscription(user_id, category_id) is not None:
            return
        self.db.add(UserCategorySubscription(user_id=user_id, category_id=category_id))
        self.db.flush()

    def unsubscribe(self, user_id: int, category_id: int) -> None:
        subscription = self.get_subscription(user_id, category_id)
        if subscription is None:
            return
        self.db.delete(subscription)
        self.db.flush()

    def get_like(self, user_id: int, article_id: int) -> ArticleLike | None:
        statement = select(ArticleLike).where(
            ArticleLike.user_id == user_id,
            ArticleLike.article_id == article_id,
        )
        return self.db.scalar(statement)

    def get_liked_article_ids(
        self,
        user_id: int,
        article_ids: list[int],
    ) -> set[int]:
        if not article_ids:
            return set()
        statement = select(ArticleLike.article_id).where(
            ArticleLike.user_id == user_id,
            ArticleLike.article_id.in_(article_ids),
        )
        return set(self.db.scalars(statement).all())

    def like_article(self, user_id: int, article_id: int) -> None:
        if self.get_like(user_id, article_id) is not None:
            return
        self.db.add(ArticleLike(user_id=user_id, article_id=article_id))
        self.db.flush()

    def unlike_article(self, user_id: int, article_id: int) -> None:
        like = self.get_like(user_id, article_id)
        if like is None:
            return
        self.db.delete(like)
        self.db.flush()

    def get_saved_article(self, user_id: int, article_id: int) -> SavedArticle | None:
        statement = select(SavedArticle).where(
            SavedArticle.user_id == user_id,
            SavedArticle.article_id == article_id,
        )
        return self.db.scalar(statement)

    def get_saved_article_ids(
        self,
        user_id: int,
        article_ids: list[int],
    ) -> set[int]:
        if not article_ids:
            return set()
        statement = select(SavedArticle.article_id).where(
            SavedArticle.user_id == user_id,
            SavedArticle.article_id.in_(article_ids),
        )
        return set(self.db.scalars(statement).all())

    def save_article(self, user_id: int, article_id: int) -> None:
        if self.get_saved_article(user_id, article_id) is not None:
            return
        self.db.add(SavedArticle(user_id=user_id, article_id=article_id))
        self.db.flush()

    def unsave_article(self, user_id: int, article_id: int) -> None:
        saved_article = self.get_saved_article(user_id, article_id)
        if saved_article is None:
            return
        self.db.delete(saved_article)
        self.db.flush()

    def list_saved_articles(
        self,
        user_id: int,
        limit: int,
        offset: int,
    ) -> tuple[list[Article], int]:
        saved_article_ids = select(SavedArticle.article_id).where(SavedArticle.user_id == user_id)
        statement = (
            select(Article)
            .options(selectinload(Article.category))
            .where(Article.id.in_(saved_article_ids))
            .order_by(Article.published_at.desc(), Article.id.desc())
            .limit(limit)
            .offset(offset)
        )
        count_statement = select(SavedArticle).where(SavedArticle.user_id == user_id)
        articles = list(self.db.scalars(statement).all())
        total = len(self.db.scalars(count_statement).all())
        return articles, total
