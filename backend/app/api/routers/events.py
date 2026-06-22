from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.api.deps import current_user
from app.core.database import get_db
from app.models import User
from app.schemas.event import (
    BatchEventResponse,
    BatchEventsRequest,
    EventAcceptedResponse,
    SmartFeedEvent,
)
from app.services.event_service import EventService


router = APIRouter(prefix="/api/v1/events", tags=["events"])


@router.post("", response_model=EventAcceptedResponse)
def create_event(
    payload: SmartFeedEvent,
    user: Annotated[User, Depends(current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> EventAcceptedResponse:
    return EventService(db).publish_event(user=user, event=payload)


@router.post("/batch", response_model=BatchEventResponse)
def create_events_batch(
    payload: BatchEventsRequest | list[SmartFeedEvent],
    user: Annotated[User, Depends(current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> BatchEventResponse:
    events = payload if isinstance(payload, list) else payload.events
    return EventService(db).publish_batch(user=user, events=events)
