from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.api.deps import current_user, optional_current_user
from app.core.database import get_db
from app.models import User
from app.schemas.category import CategoryRead
from app.schemas.event import StatusResponse
from app.services.content_service import ContentService
from app.services.interaction_service import InteractionService


router = APIRouter(prefix="/api/v1/categories", tags=["categories"])


@router.get("", response_model=list[CategoryRead])
def list_categories(
    db: Annotated[Session, Depends(get_db)],
    user: Annotated[User | None, Depends(optional_current_user)],
) -> list[CategoryRead]:
    return ContentService(db).list_categories(user=user)


@router.post("/{category_id}/subscribe", response_model=StatusResponse)
def subscribe_category(
    category_id: int,
    user: Annotated[User, Depends(current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> StatusResponse:
    return InteractionService(db).subscribe_category(user=user, category_id=category_id)


@router.delete("/{category_id}/subscribe", response_model=StatusResponse)
def unsubscribe_category(
    category_id: int,
    user: Annotated[User, Depends(current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> StatusResponse:
    return InteractionService(db).unsubscribe_category(user=user, category_id=category_id)
