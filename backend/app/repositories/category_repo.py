from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Category


class CategoryRepository:
    def __init__(self, db: Session) -> None:
        self.db = db

    def list_categories(self) -> list[Category]:
        statement = select(Category).order_by(Category.name.asc())
        return list(self.db.scalars(statement).all())

    def get_category(self, category_id: int) -> Category | None:
        return self.db.get(Category, category_id)
