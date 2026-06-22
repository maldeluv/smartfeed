from functools import lru_cache

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    PROJECT_NAME: str = "SmartFeed Backend"
    ENVIRONMENT: str = "local"
    BACKEND_PORT: int = 8000
    SECRET_KEY: str = "change-me-in-local-env"
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 10080

    POSTGRES_DB: str = "smartfeed"
    POSTGRES_USER: str = "smartfeed"
    POSTGRES_PASSWORD: str = "smartfeed"
    POSTGRES_HOST: str = "postgres"
    POSTGRES_PORT: int = 5432
    DATABASE_URL: str = Field(
        default="postgresql+psycopg://smartfeed:smartfeed@postgres:5432/smartfeed"
    )

    MONGO_INITDB_ROOT_USERNAME: str = "smartfeed"
    MONGO_INITDB_ROOT_PASSWORD: str = "smartfeed"
    MONGO_DB: str = "smartfeed"
    MONGODB_URL: str = Field(
        default="mongodb://smartfeed:smartfeed@mongo:27017/smartfeed?authSource=admin"
    )
    MONGO_RAW_EVENTS_COLLECTION: str = "raw_user_events"

    KAFKA_BOOTSTRAP_SERVERS: str = "kafka:29092"
    KAFKA_USER_EVENTS_TOPIC: str = "smartfeed.user_events"
    SPARK_MASTER_URL: str = "spark://spark-master:7077"
    JUPYTER_TOKEN: str = "smartfeed"

    @model_validator(mode="after")
    def normalize_database_url(self) -> "Settings":
        if self.DATABASE_URL.startswith("postgresql://"):
            self.DATABASE_URL = self.DATABASE_URL.replace(
                "postgresql://",
                "postgresql+psycopg://",
                1,
            )
        return self

    model_config = SettingsConfigDict(
        env_file=(".env", "../.env"),
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
