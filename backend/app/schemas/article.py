from datetime import datetime, timezone

from pydantic import BaseModel, ConfigDict, Field

from app.schemas.category import CategoryRead


class ArticleListItem(BaseModel):
    id: int
    title: str
    summary: str
    source_url: str | None = None
    category_id: int
    author: str | None = None
    published_at: datetime
    created_at: datetime
    popularity_score: float
    category: CategoryRead | None = None
    is_liked: bool = False
    is_saved: bool = False

    model_config = ConfigDict(from_attributes=True)


class ArticleDetail(ArticleListItem):
    content: str


class ArticlePage(BaseModel):
    items: list[ArticleListItem]
    total: int
    limit: int
    offset: int


class ArticleCreate(BaseModel):
    title: str = Field(min_length=3, max_length=255)
    summary: str = Field(min_length=10)
    content: str = Field(min_length=10)
    source_url: str | None = Field(default=None, max_length=500)
    category_id: int = Field(ge=1)
    author: str | None = Field(default=None, max_length=120)
    published_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    popularity_score: float = Field(default=0.0, ge=0)


class ArticleUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=3, max_length=255)
    summary: str | None = Field(default=None, min_length=10)
    content: str | None = Field(default=None, min_length=10)
    source_url: str | None = Field(default=None, max_length=500)
    category_id: int | None = Field(default=None, ge=1)
    author: str | None = Field(default=None, max_length=120)
    published_at: datetime | None = None
    popularity_score: float | None = Field(default=None, ge=0)
