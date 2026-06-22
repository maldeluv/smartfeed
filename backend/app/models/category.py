from __future__ import annotations

from sqlalchemy import String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class Category(Base):
    __tablename__ = "categories"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(120), unique=True, nullable=False)
    slug: Mapped[str] = mapped_column(String(140), unique=True, index=True, nullable=False)
    description: Mapped[str | None] = mapped_column(Text)

    articles: Mapped[list["Article"]] = relationship(back_populates="category")
    subscriptions: Mapped[list["UserCategorySubscription"]] = relationship(
        back_populates="category",
        cascade="all, delete-orphan",
    )
    user_scores: Mapped[list["UserCategoryScore"]] = relationship(
        back_populates="category",
        cascade="all, delete-orphan",
    )
    daily_stats: Mapped[list["DailyEventStats"]] = relationship(back_populates="category")
