from datetime import datetime

from pydantic import BaseModel, ConfigDict


class UserRead(BaseModel):
    id: int
    email: str
    full_name: str
    role: str
    created_at: datetime
    last_login_at: datetime | None = None
    is_active: bool

    model_config = ConfigDict(from_attributes=True)
