from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Any, Literal
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


EventType = Literal[
    "view_article",
    "like_article",
    "unlike_article",
    "save_article",
    "unsave_article",
    "subscribe_category",
    "unsubscribe_category",
    "search",
    "open_recommendations",
    "open_recommended_article",
]


class SmartFeedEvent(BaseModel):
    event_id: UUID = Field(default_factory=uuid4, alias="eventId")
    session_id: UUID | None = Field(default=None, alias="sessionId")
    event_type: EventType = Field(alias="eventType")
    article_id: int | None = Field(default=None, ge=1, alias="articleId")
    category_id: int | None = Field(default=None, ge=1, alias="categoryId")
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    device: dict[str, Any] | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)

    model_config = ConfigDict(populate_by_name=True)

    @field_validator("metadata", "device")
    @classmethod
    def validate_json_object(
        cls,
        value: dict[str, Any] | None,
    ) -> dict[str, Any] | None:
        if value is None:
            return value
        try:
            json.dumps(value)
        except (TypeError, ValueError) as exc:
            raise ValueError("Value must be JSON-serializable") from exc
        return value

    @model_validator(mode="after")
    def validate_event_links(self) -> "SmartFeedEvent":
        article_events = {
            "view_article",
            "like_article",
            "unlike_article",
            "save_article",
            "unsave_article",
            "open_recommended_article",
        }
        category_events = {"subscribe_category", "unsubscribe_category"}

        if self.event_type in article_events and self.article_id is None:
            raise ValueError("articleId is required for this event type")
        if self.event_type in category_events and self.category_id is None:
            raise ValueError("categoryId is required for this event type")
        return self


class EventAcceptedResponse(BaseModel):
    event_id: UUID
    status: str
    delivery: str
    detail: str | None = None


class BatchEventResponse(BaseModel):
    accepted_count: int
    failed_count: int
    events: list[EventAcceptedResponse]


class BatchEventsRequest(BaseModel):
    events: list[SmartFeedEvent] = Field(min_length=1, max_length=1000)


class StatusResponse(BaseModel):
    status: str
    event: EventAcceptedResponse
