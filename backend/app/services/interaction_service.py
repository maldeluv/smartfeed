from __future__ import annotations

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.models import User
from app.repositories.interaction_repo import InteractionRepository
from app.schemas.article import ArticlePage
from app.schemas.event import EventAcceptedResponse, SmartFeedEvent, StatusResponse
from app.services.content_service import ContentService
from app.services.event_service import EventService


class InteractionService:
    def __init__(self, db: Session) -> None:
        self.db = db
        self.interactions = InteractionRepository(db)
        self.events = EventService(db)

    def subscribe_category(self, user: User, category_id: int) -> StatusResponse:
        self._ensure_category(category_id)
        self.interactions.subscribe(user_id=user.id, category_id=category_id)
        self.db.commit()
        event_response = self._publish_action_event(
            user=user,
            event_type="subscribe_category",
            category_id=category_id,
        )
        return StatusResponse(status="subscribed", event=event_response)

    def unsubscribe_category(self, user: User, category_id: int) -> StatusResponse:
        self._ensure_category(category_id)
        self.interactions.unsubscribe(user_id=user.id, category_id=category_id)
        self.db.commit()
        event_response = self._publish_action_event(
            user=user,
            event_type="unsubscribe_category",
            category_id=category_id,
        )
        return StatusResponse(status="unsubscribed", event=event_response)

    def like_article(self, user: User, article_id: int) -> StatusResponse:
        article = self._ensure_article(article_id)
        self.interactions.like_article(user_id=user.id, article_id=article_id)
        self.db.commit()
        event_response = self._publish_action_event(
            user=user,
            event_type="like_article",
            article_id=article_id,
            category_id=article.category_id,
        )
        return StatusResponse(status="liked", event=event_response)

    def unlike_article(self, user: User, article_id: int) -> StatusResponse:
        article = self._ensure_article(article_id)
        self.interactions.unlike_article(user_id=user.id, article_id=article_id)
        self.db.commit()
        event_response = self._publish_action_event(
            user=user,
            event_type="unlike_article",
            article_id=article_id,
            category_id=article.category_id,
        )
        return StatusResponse(status="unliked", event=event_response)

    def save_article(self, user: User, article_id: int) -> StatusResponse:
        article = self._ensure_article(article_id)
        self.interactions.save_article(user_id=user.id, article_id=article_id)
        self.db.commit()
        event_response = self._publish_action_event(
            user=user,
            event_type="save_article",
            article_id=article_id,
            category_id=article.category_id,
        )
        return StatusResponse(status="saved", event=event_response)

    def unsave_article(self, user: User, article_id: int) -> StatusResponse:
        article = self._ensure_article(article_id)
        self.interactions.unsave_article(user_id=user.id, article_id=article_id)
        self.db.commit()
        event_response = self._publish_action_event(
            user=user,
            event_type="unsave_article",
            article_id=article_id,
            category_id=article.category_id,
        )
        return StatusResponse(status="unsaved", event=event_response)

    def list_saved_articles(self, user: User, limit: int, offset: int) -> ArticlePage:
        articles, total = self.interactions.list_saved_articles(
            user_id=user.id,
            limit=limit,
            offset=offset,
        )
        ContentService(self.db).apply_article_states(articles, user)
        return ArticlePage(items=articles, total=total, limit=limit, offset=offset)

    def _ensure_category(self, category_id: int) -> None:
        if self.interactions.get_category(category_id) is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Category not found",
            )

    def _ensure_article(self, article_id: int):
        article = self.interactions.get_article(article_id)
        if article is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Article not found",
            )
        return article

    def _publish_action_event(
        self,
        user: User,
        event_type: str,
        article_id: int | None = None,
        category_id: int | None = None,
    ) -> EventAcceptedResponse:
        event = SmartFeedEvent(
            event_type=event_type,
            article_id=article_id,
            category_id=category_id,
            metadata={"source": "api_action"},
        )
        return self.events.publish_event(user=user, event=event)
