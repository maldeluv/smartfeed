from fastapi import FastAPI

from app.api.routers import (
    admin,
    analytics,
    articles,
    auth,
    categories,
    events,
    recommendations,
    saved,
    users,
)
from app.core.config import settings


app = FastAPI(title=settings.PROJECT_NAME)


@app.get("/health", tags=["health"])
def health_check() -> dict[str, str]:
    return {"status": "ok"}


app.include_router(auth.router)
app.include_router(users.router)
app.include_router(categories.router)
app.include_router(articles.router)
app.include_router(saved.router)
app.include_router(events.router)
app.include_router(analytics.router)
app.include_router(admin.router)
app.include_router(recommendations.router)
