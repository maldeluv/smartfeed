from collections.abc import Generator
from functools import lru_cache

from pymongo import MongoClient
from pymongo.database import Database

from app.core.config import settings


@lru_cache
def get_mongo_client() -> MongoClient:
    return MongoClient(settings.MONGODB_URL, serverSelectionTimeoutMS=2000)


def get_mongo_database() -> Generator[Database, None, None]:
    yield get_mongo_client()[settings.MONGO_DB]
