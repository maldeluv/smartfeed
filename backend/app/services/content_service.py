from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.models import Article, Category, User
from app.repositories.article_repo import ArticleRepository
from app.repositories.category_repo import CategoryRepository
from app.repositories.interaction_repo import InteractionRepository


class ContentService:
    def __init__(self, db: Session) -> None:
        self.categories = CategoryRepository(db)
        self.articles = ArticleRepository(db)
        self.interactions = InteractionRepository(db)

    def list_categories(self, user: User | None = None) -> list[Category]:
        categories = self.categories.list_categories()
        self.apply_category_states(categories, user)
        return categories

    def list_articles(
        self,
        category_id: int | None,
        search: str | None,
        limit: int,
        offset: int,
        user: User | None = None,
    ) -> tuple[list[Article], int]:
        articles, total = self.articles.list_articles(
            category_id=category_id,
            search=search,
            limit=limit,
            offset=offset,
        )
        self.apply_article_states(articles, user)
        return articles, total

    def get_article(self, article_id: int, user: User | None = None) -> Article:
        article = self.articles.get_article(article_id)
        if article is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Article not found",
            )
        self.apply_article_states([article], user)
        return article

    def apply_category_states(
        self,
        categories: list[Category],
        user: User | None,
    ) -> None:
        subscribed_ids = (
            self.interactions.get_subscribed_category_ids(user.id)
            if user is not None
            else set()
        )
        for category in categories:
            category.is_subscribed = category.id in subscribed_ids

    def apply_article_states(
        self,
        articles: list[Article],
        user: User | None,
    ) -> None:
        article_ids = [article.id for article in articles]
        liked_ids = (
            self.interactions.get_liked_article_ids(user.id, article_ids)
            if user is not None
            else set()
        )
        saved_ids = (
            self.interactions.get_saved_article_ids(user.id, article_ids)
            if user is not None
            else set()
        )
        categories_by_id = {
            article.category.id: article.category
            for article in articles
            if article.category is not None
        }
        categories = list(categories_by_id.values())
        self.apply_category_states(categories, user)
        for article in articles:
            article.is_liked = article.id in liked_ids
            article.is_saved = article.id in saved_ids
