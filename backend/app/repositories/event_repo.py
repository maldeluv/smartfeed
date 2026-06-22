from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import PendingEvent
from app.schemas.event import SmartFeedEvent


class EventRepository:
    def __init__(self, db: Session) -> None:
        self.db = db

    def create_pending_event(
        self,
        user_id: int,
        event: SmartFeedEvent,
        error: str | None,
    ) -> PendingEvent:
        existing = self.db.scalar(
            select(PendingEvent).where(PendingEvent.event_id == str(event.event_id))
        )
        if existing is not None:
            return existing
        pending_event = PendingEvent(
            event_id=str(event.event_id),
            user_id=user_id,
            session_id=str(event.session_id) if event.session_id is not None else None,
            event_type=event.event_type,
            article_id=event.article_id,
            category_id=event.category_id,
            timestamp=event.timestamp,
            device=event.device,
            metadata_json=event.metadata,
            error=error,
        )
        self.db.add(pending_event)
        self.db.flush()
        return pending_event
