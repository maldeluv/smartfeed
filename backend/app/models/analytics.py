from __future__ import annotations

from datetime import date, datetime

from sqlalchemy import Date, DateTime, Float, ForeignKey, String, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class UserCategoryScore(Base):
    __tablename__ = "user_category_scores"
    __table_args__ = (UniqueConstraint("user_id", "category_id", name="uq_user_category_score"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"),
        index=True,
        nullable=False,
    )
    category_id: Mapped[int] = mapped_column(
        ForeignKey("categories.id", ondelete="CASCADE"),
        index=True,
        nullable=False,
    )
    score: Mapped[float] = mapped_column(Float, default=0.0, nullable=False)
    views_count: Mapped[int] = mapped_column(default=0, nullable=False)
    likes_count: Mapped[int] = mapped_column(default=0, nullable=False)
    saves_count: Mapped[int] = mapped_column(default=0, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )

    user: Mapped["User"] = relationship(back_populates="category_scores")
    category: Mapped["Category"] = relationship(back_populates="user_scores")


class DailyEventStats(Base):
    __tablename__ = "daily_event_stats"
    __table_args__ = (
        UniqueConstraint("date", "event_type", "category_id", name="uq_daily_event_stats"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    date: Mapped[date] = mapped_column(Date, index=True, nullable=False)
    event_type: Mapped[str] = mapped_column(String(80), index=True, nullable=False)
    category_id: Mapped[int | None] = mapped_column(
        ForeignKey("categories.id", ondelete="SET NULL"),
        index=True,
    )
    events_count: Mapped[int] = mapped_column(default=0, nullable=False)
    unique_users_count: Mapped[int] = mapped_column(default=0, nullable=False)

    category: Mapped["Category | None"] = relationship(back_populates="daily_stats")
