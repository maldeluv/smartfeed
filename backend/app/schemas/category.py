from pydantic import BaseModel, ConfigDict


class CategoryRead(BaseModel):
    id: int
    name: str
    slug: str
    description: str | None = None
    is_subscribed: bool = False

    model_config = ConfigDict(from_attributes=True)
