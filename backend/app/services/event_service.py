from __future__ import annotations

import logging
from typing import Any

from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.core.kafka import kafka_event_producer
from app.models import User
from app.repositories.event_repo import EventRepository
from app.schemas.event import BatchEventResponse, EventAcceptedResponse, SmartFeedEvent


logger = logging.getLogger(__name__)


class EventService:
    def __init__(self, db: Session) -> None:
        self.db = db
        self.events = EventRepository(db)

    def publish_event(self, user: User, event: SmartFeedEvent) -> EventAcceptedResponse:
        payload = self._to_kafka_payload(user=user, event=event)
        delivery_result = kafka_event_producer.send(payload)
        try:
            self.events.create_pending_event(
                user_id=user.id,
                event=event,
                error=None if delivery_result.delivered else delivery_result.detail,
            )
            self.db.commit()
        except SQLAlchemyError as exc:
            self.db.rollback()
            logger.exception("Event audit write failed")
            if delivery_result.delivered:
                return EventAcceptedResponse(
                    event_id=event.event_id,
                    status="accepted",
                    delivery="kafka",
                    detail="Kafka delivery succeeded; PostgreSQL audit write failed",
                )
            return EventAcceptedResponse(
                event_id=event.event_id,
                status="accepted",
                delivery="failed",
                detail=f"Kafka unavailable and pending event fallback failed: {exc}",
            )

        if delivery_result.delivered:
            return EventAcceptedResponse(
                event_id=event.event_id,
                status="accepted",
                delivery="kafka",
            )

        return EventAcceptedResponse(
            event_id=event.event_id,
            status="accepted",
            delivery="pending_events",
            detail="Kafka unavailable; event saved to pending_events",
        )

    def publish_batch(self, user: User, events: list[SmartFeedEvent]) -> BatchEventResponse:
        responses = [self.publish_event(user=user, event=event) for event in events]
        failed_count = sum(1 for response in responses if response.delivery == "failed")
        return BatchEventResponse(
            accepted_count=len(responses) - failed_count,
            failed_count=failed_count,
            events=responses,
        )

    def _to_kafka_payload(self, user: User, event: SmartFeedEvent) -> dict[str, Any]:
        payload = event.model_dump(mode="json", by_alias=False)
        payload["event_id"] = str(event.event_id)
        payload["user_id"] = user.id
        payload["session_id"] = str(event.session_id) if event.session_id is not None else None
        return payload
