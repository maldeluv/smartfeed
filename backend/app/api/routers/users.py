from typing import Annotated

from fastapi import APIRouter, Depends

from app.api.deps import current_user
from app.models import User
from app.schemas.user import UserRead


router = APIRouter(prefix="/api/v1/users", tags=["users"])


@router.get("/me", response_model=UserRead)
def get_me(user: Annotated[User, Depends(current_user)]) -> UserRead:
    return user
