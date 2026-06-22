from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from typing import Any

from kafka import KafkaProducer

from app.core.config import settings


logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class KafkaDeliveryResult:
    delivered: bool
    detail: str | None = None


class KafkaEventProducer:
    def __init__(self) -> None:
        self._producer: KafkaProducer | None = None

    def send(self, event: dict[str, Any]) -> KafkaDeliveryResult:
        try:
            producer = self._get_producer()
            future = producer.send(settings.KAFKA_USER_EVENTS_TOPIC, event)
            future.get(timeout=3)
            producer.flush(timeout=3)
        except Exception as exc:  # Kafka may be down in local dev.
            logger.warning("Kafka delivery failed: %s", exc)
            return KafkaDeliveryResult(delivered=False, detail=str(exc))

        return KafkaDeliveryResult(delivered=True)

    def _get_producer(self) -> KafkaProducer:
        if self._producer is None:
            self._producer = KafkaProducer(
                bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS,
                value_serializer=lambda value: json.dumps(value).encode("utf-8"),
                linger_ms=5,
                request_timeout_ms=3000,
                api_version_auto_timeout_ms=3000,
            )
        return self._producer


kafka_event_producer = KafkaEventProducer()
