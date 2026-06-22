from __future__ import annotations

import argparse
import glob
import os
from dataclasses import dataclass

from pyspark.ml.recommendation import ALS
from pyspark.sql import DataFrame, SparkSession, Window
from pyspark.sql import functions as F
from pyspark.sql import types as T


EVENT_RATINGS = {
    "view_article": 1.0,
    "like_article": 3.0,
    "save_article": 5.0,
    "open_recommended_article": 2.0,
}

RECOMMENDATION_CANDIDATE_SCHEMA = T.StructType(
    [
        T.StructField("user_id", T.LongType(), nullable=False),
        T.StructField("article_id", T.LongType(), nullable=False),
        T.StructField("score", T.DoubleType(), nullable=False),
        T.StructField("reason", T.StringType(), nullable=False),
        T.StructField("model_version", T.StringType(), nullable=False),
        T.StructField("source_priority", T.IntegerType(), nullable=False),
    ]
)


@dataclass(frozen=True)
class RecommendationConfig:
    top_n: int
    min_events: int
    model_version: str
    mongo_uri: str
    mongo_database: str
    mongo_collection: str
    postgres_jdbc_url: str
    postgres_user: str
    postgres_password: str
    fallback_pool_size: int
    rank: int
    max_iter: int
    reg_param: float
    alpha: float


def positive_int(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("value must be a positive integer")
    return parsed


def build_spark(config: RecommendationConfig) -> SparkSession:
    return (
        SparkSession.builder.appName("SmartFeedBatchRecommendationsALS")
        .config("spark.sql.session.timeZone", "UTC")
        .config("spark.mongodb.read.connection.uri", config.mongo_uri)
        .getOrCreate()
    )


def read_postgres_table(spark: SparkSession, config: RecommendationConfig, table: str) -> DataFrame:
    return (
        spark.read.format("jdbc")
        .option("url", config.postgres_jdbc_url)
        .option("dbtable", table)
        .option("user", config.postgres_user)
        .option("password", config.postgres_password)
        .option("driver", "org.postgresql.Driver")
        .load()
    )


def read_users(spark: SparkSession, config: RecommendationConfig) -> DataFrame:
    return (
        read_postgres_table(spark, config, "users")
        .where((F.col("is_active").isNotNull()) & F.col("is_active") & (F.col("role") == "user"))
        .select(F.col("id").cast("long").alias("user_id"))
        .dropDuplicates(["user_id"])
    )


def read_articles(spark: SparkSession, config: RecommendationConfig) -> DataFrame:
    return (
        read_postgres_table(spark, config, "articles")
        .select(
            F.col("id").cast("long").alias("article_id"),
            F.col("category_id").cast("long").alias("category_id"),
            F.coalesce(F.col("popularity_score").cast("double"), F.lit(0.0)).alias(
                "article_popularity_score"
            ),
            "published_at",
        )
        .dropDuplicates(["article_id"])
    )


def read_subscriptions(spark: SparkSession, config: RecommendationConfig) -> DataFrame:
    return (
        read_postgres_table(spark, config, "user_category_subscriptions")
        .select(
            F.col("user_id").cast("long").alias("user_id"),
            F.col("category_id").cast("long").alias("category_id"),
        )
        .dropDuplicates(["user_id", "category_id"])
    )


def read_events_from_mongo(spark: SparkSession, config: RecommendationConfig) -> DataFrame:
    return (
        spark.read.format("mongodb")
        .option("database", config.mongo_database)
        .option("collection", config.mongo_collection)
        .load()
        .select(
            F.col("event_id").cast("string").alias("event_id"),
            F.col("user_id").cast("long").alias("user_id"),
            F.col("article_id").cast("long").alias("article_id"),
            F.col("category_id").cast("long").alias("category_id"),
            F.col("event_type").cast("string").alias("event_type"),
        )
    )


def read_events_from_pending_events(spark: SparkSession, config: RecommendationConfig) -> DataFrame:
    return (
        read_postgres_table(spark, config, "pending_events")
        .select(
            F.col("event_id").cast("string").alias("event_id"),
            F.col("user_id").cast("long").alias("user_id"),
            F.col("article_id").cast("long").alias("article_id"),
            F.col("category_id").cast("long").alias("category_id"),
            F.col("event_type").cast("string").alias("event_type"),
        )
    )


def is_empty(df: DataFrame) -> bool:
    return df.rdd.isEmpty()


def read_interaction_events(spark: SparkSession, config: RecommendationConfig) -> DataFrame:
    postgres_events = read_events_from_pending_events(spark, config)
    try:
        mongo_events = read_events_from_mongo(spark, config)
        if not is_empty(mongo_events):
            print(
                "Using deduplicated MongoDB + PostgreSQL events: "
                f"{config.mongo_database}.{config.mongo_collection} + pending_events"
            )
            return deduplicate_events(mongo_events.unionByName(postgres_events))
        print("MongoDB raw events collection is empty; using PostgreSQL pending_events.")
    except Exception as exc:
        print(f"MongoDB events source is unavailable; using pending_events. Error: {exc}")

    return deduplicate_events(postgres_events)


def deduplicate_events(events: DataFrame) -> DataFrame:
    with_event_id = events.where(F.col("event_id").isNotNull()).dropDuplicates(["event_id"])
    without_event_id = events.where(F.col("event_id").isNull())
    return with_event_id.unionByName(without_event_id)


def rating_expression() -> F.Column:
    expression = F.lit(None).cast("double")
    for event_type, rating in EVENT_RATINGS.items():
        expression = F.when(F.col("event_type") == event_type, F.lit(rating)).otherwise(expression)
    return expression


def build_ratings(events: DataFrame) -> DataFrame:
    return (
        events.withColumn("event_rating", rating_expression())
        .where(
            F.col("user_id").isNotNull()
            & F.col("article_id").isNotNull()
            & F.col("event_rating").isNotNull()
        )
        .groupBy("user_id", "article_id")
        .agg(
            F.sum("event_rating").alias("rating"),
            F.count("*").alias("events_count"),
        )
        .where(F.col("rating") > 0)
    )


def build_user_event_counts(users: DataFrame, ratings: DataFrame) -> DataFrame:
    counts = ratings.groupBy("user_id").agg(F.sum("events_count").cast("long").alias("event_count"))
    return users.join(counts, "user_id", "left").fillna({"event_count": 0})


def empty_candidates(spark: SparkSession) -> DataFrame:
    return spark.createDataFrame([], RECOMMENDATION_CANDIDATE_SCHEMA)


def build_als_recommendations(
    spark: SparkSession,
    ratings: DataFrame,
    user_event_counts: DataFrame,
    interacted_articles: DataFrame,
    config: RecommendationConfig,
) -> DataFrame:
    eligible_users = user_event_counts.where(F.col("event_count") >= config.min_events).select(
        F.col("user_id").cast("int").alias("user_id")
    )
    als_ratings = ratings.select(
        F.col("user_id").cast("int").alias("user_id"),
        F.col("article_id").cast("int").alias("article_id"),
        F.col("rating").cast("float").alias("rating"),
    )

    if is_empty(als_ratings) or is_empty(eligible_users):
        return empty_candidates(spark)

    als = ALS(
        userCol="user_id",
        itemCol="article_id",
        ratingCol="rating",
        implicitPrefs=True,
        coldStartStrategy="drop",
        nonnegative=True,
        rank=config.rank,
        maxIter=config.max_iter,
        regParam=config.reg_param,
        alpha=config.alpha,
    )
    model = als.fit(als_ratings)
    recommendation_count = max(config.top_n * 3, config.top_n + 20)

    recommendations = model.recommendForUserSubset(eligible_users, recommendation_count)
    exploded = (
        recommendations.select("user_id", F.explode("recommendations").alias("recommendation"))
        .select(
            F.col("user_id").cast("long").alias("user_id"),
            F.col("recommendation.article_id").cast("long").alias("article_id"),
            F.col("recommendation.rating").cast("double").alias("score"),
        )
        .join(interacted_articles, ["user_id", "article_id"], "left_anti")
        .withColumn("reason", F.lit("als_implicit_feedback"))
        .withColumn("model_version", F.lit(config.model_version))
        .withColumn("source_priority", F.lit(1))
    )

    window = Window.partitionBy("user_id").orderBy(F.desc("score"), F.asc("article_id"))
    return (
        exploded.withColumn("row_number", F.row_number().over(window))
        .where(F.col("row_number") <= config.top_n)
        .drop("row_number")
    )


def build_article_popularity(articles: DataFrame, ratings: DataFrame) -> DataFrame:
    event_popularity = ratings.groupBy("article_id").agg(
        F.sum("rating").alias("interaction_score"),
        F.sum("events_count").alias("interaction_events_count"),
    )
    return (
        articles.join(event_popularity, "article_id", "left")
        .fillna({"interaction_score": 0.0, "interaction_events_count": 0})
        .withColumn(
            "fallback_score",
            F.col("interaction_score") + F.col("article_popularity_score") * F.lit(0.1),
        )
    )


def build_fallback_recommendations(
    users: DataFrame,
    articles: DataFrame,
    subscriptions: DataFrame,
    ratings: DataFrame,
    interacted_articles: DataFrame,
    config: RecommendationConfig,
) -> DataFrame:
    article_popularity = build_article_popularity(articles, ratings).cache()

    subscribed_candidates = (
        subscriptions.join(users, "user_id", "inner")
        .join(article_popularity, "category_id", "inner")
        .join(interacted_articles, ["user_id", "article_id"], "left_anti")
        .select(
            "user_id",
            "article_id",
            F.col("fallback_score").cast("double").alias("score"),
            F.lit("fallback_subscribed_category").alias("reason"),
            F.lit(config.model_version).alias("model_version"),
            F.lit(2).alias("source_priority"),
        )
    )

    global_articles = article_popularity.orderBy(
        F.desc("fallback_score"),
        F.desc_nulls_last("published_at"),
        F.asc("article_id"),
    ).limit(config.fallback_pool_size)
    global_candidates = (
        users.crossJoin(global_articles)
        .join(interacted_articles, ["user_id", "article_id"], "left_anti")
        .select(
            "user_id",
            "article_id",
            F.col("fallback_score").cast("double").alias("score"),
            F.lit("fallback_global_popular").alias("reason"),
            F.lit(config.model_version).alias("model_version"),
            F.lit(3).alias("source_priority"),
        )
    )

    fallback = subscribed_candidates.unionByName(global_candidates)
    dedupe_window = Window.partitionBy("user_id", "article_id").orderBy(
        F.asc("source_priority"),
        F.desc("score"),
    )
    rank_window = Window.partitionBy("user_id").orderBy(
        F.asc("source_priority"),
        F.desc("score"),
        F.asc("article_id"),
    )
    result = (
        fallback.withColumn("dedupe_rank", F.row_number().over(dedupe_window))
        .where(F.col("dedupe_rank") == 1)
        .drop("dedupe_rank")
        .withColumn("row_number", F.row_number().over(rank_window))
        .where(F.col("row_number") <= config.top_n)
        .drop("row_number")
    )
    article_popularity.unpersist()
    return result


def combine_recommendations(
    als_recommendations: DataFrame,
    fallback_recommendations: DataFrame,
    config: RecommendationConfig,
) -> DataFrame:
    combined = als_recommendations.unionByName(fallback_recommendations)
    dedupe_window = Window.partitionBy("user_id", "article_id").orderBy(
        F.asc("source_priority"),
        F.desc("score"),
    )
    rank_window = Window.partitionBy("user_id").orderBy(
        F.asc("source_priority"),
        F.desc("score"),
        F.asc("article_id"),
    )
    return (
        combined.withColumn("dedupe_rank", F.row_number().over(dedupe_window))
        .where(F.col("dedupe_rank") == 1)
        .drop("dedupe_rank")
        .withColumn("row_number", F.row_number().over(rank_window))
        .where(F.col("row_number") <= config.top_n)
        .drop("row_number", "source_priority")
    )


def open_postgres_jdbc_connection(spark: SparkSession, config: RecommendationConfig):
    gateway = spark.sparkContext._gateway
    jvm = gateway.jvm
    properties = jvm.java.util.Properties()
    properties.setProperty("user", config.postgres_user)
    properties.setProperty("password", config.postgres_password)

    try:
        jvm.java.lang.Class.forName("org.postgresql.Driver")
        return jvm.java.sql.DriverManager.getConnection(config.postgres_jdbc_url, properties)
    except Exception:
        jar_candidates = glob.glob("/tmp/.ivy2/jars/org.postgresql_postgresql-*.jar")
        if not jar_candidates:
            raise RuntimeError("PostgreSQL JDBC driver jar was not found in /tmp/.ivy2/jars")

        jar_file = jvm.java.io.File(jar_candidates[0])
        url_array = gateway.new_array(jvm.java.net.URL, 1)
        url_array[0] = jar_file.toURI().toURL()
        loader = jvm.java.net.URLClassLoader(url_array)
        driver_class = jvm.java.lang.Class.forName("org.postgresql.Driver", True, loader)
        driver = driver_class.newInstance()
        return driver.connect(config.postgres_jdbc_url, properties)


def replace_recommendations(
    spark: SparkSession,
    recommendations: DataFrame,
    config: RecommendationConfig,
) -> int:
    rows = recommendations.collect()
    if not rows:
        print("No recommendations generated; PostgreSQL table was not changed.")
        return 0

    connection = open_postgres_jdbc_connection(spark, config)
    connection.setAutoCommit(False)
    delete_statement = None
    insert_statement = None
    try:
        delete_statement = connection.prepareStatement(
            "DELETE FROM recommendations WHERE model_version = ?"
        )
        delete_statement.setString(1, config.model_version)
        delete_statement.executeUpdate()

        insert_statement = connection.prepareStatement(
            """
            INSERT INTO recommendations
                (user_id, article_id, score, reason, model_version, created_at)
            VALUES (?, ?, ?, ?, ?, NOW())
            ON CONFLICT (user_id, article_id, model_version)
            DO UPDATE SET
                score = EXCLUDED.score,
                reason = EXCLUDED.reason,
                created_at = NOW()
            """
        )
        for row in rows:
            insert_statement.setLong(1, int(row["user_id"]))
            insert_statement.setLong(2, int(row["article_id"]))
            insert_statement.setDouble(3, float(row["score"] or 0.0))
            insert_statement.setString(4, str(row["reason"]))
            insert_statement.setString(5, str(row["model_version"]))
            insert_statement.addBatch()
        insert_statement.executeBatch()
        connection.commit()
        return len(rows)
    except Exception:
        connection.rollback()
        raise
    finally:
        if delete_statement is not None:
            delete_statement.close()
        if insert_statement is not None:
            insert_statement.close()
        connection.close()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train SmartFeed ALS recommendations.")
    parser.add_argument("--top-n", type=positive_int, default=int(os.getenv("RECOMMENDATION_TOP_N", "10")))
    parser.add_argument("--min-events", type=positive_int, default=int(os.getenv("RECOMMENDATION_MIN_EVENTS", "5")))
    parser.add_argument("--model-version", default=os.getenv("RECOMMENDATION_MODEL_VERSION", "als-v1"))
    parser.add_argument(
        "--mongo-uri",
        default=os.getenv(
            "MONGODB_URL",
            "mongodb://smartfeed:smartfeed@mongo:27017/smartfeed?authSource=admin",
        ),
    )
    parser.add_argument("--mongo-database", default=os.getenv("MONGO_DB", "smartfeed"))
    parser.add_argument(
        "--mongo-collection",
        default=os.getenv("MONGO_RAW_EVENTS_COLLECTION", "raw_user_events"),
    )
    parser.add_argument(
        "--postgres-jdbc-url",
        default=os.getenv("POSTGRES_JDBC_URL", "jdbc:postgresql://postgres:5432/smartfeed"),
    )
    parser.add_argument("--postgres-user", default=os.getenv("POSTGRES_USER", "smartfeed"))
    parser.add_argument("--postgres-password", default=os.getenv("POSTGRES_PASSWORD", "smartfeed"))
    parser.add_argument("--fallback-pool-size", type=positive_int, default=200)
    parser.add_argument("--rank", type=positive_int, default=20)
    parser.add_argument("--max-iter", type=positive_int, default=10)
    parser.add_argument("--reg-param", type=float, default=0.08)
    parser.add_argument("--alpha", type=float, default=20.0)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    args.fallback_pool_size = max(args.fallback_pool_size, args.top_n)
    config = RecommendationConfig(**vars(args))

    spark = build_spark(config)
    spark.sparkContext.setLogLevel("WARN")
    try:
        users = read_users(spark, config).cache()
        articles = read_articles(spark, config).cache()
        subscriptions = read_subscriptions(spark, config).cache()
        events = read_interaction_events(spark, config).cache()

        if is_empty(users):
            raise RuntimeError("No active users found in PostgreSQL users table.")
        if is_empty(articles):
            raise RuntimeError("No articles found in PostgreSQL articles table.")

        ratings = build_ratings(events).cache()
        interacted_articles = ratings.select("user_id", "article_id").dropDuplicates().cache()
        user_event_counts = build_user_event_counts(users, ratings).cache()

        als_recommendations = build_als_recommendations(
            spark=spark,
            ratings=ratings,
            user_event_counts=user_event_counts,
            interacted_articles=interacted_articles,
            config=config,
        )
        fallback_recommendations = build_fallback_recommendations(
            users=users,
            articles=articles,
            subscriptions=subscriptions,
            ratings=ratings,
            interacted_articles=interacted_articles,
            config=config,
        )
        final_recommendations = combine_recommendations(
            als_recommendations=als_recommendations,
            fallback_recommendations=fallback_recommendations,
            config=config,
        ).cache()

        users_count = users.count()
        ratings_count = ratings.count()
        recommendations_count = replace_recommendations(spark, final_recommendations, config)
        print(
            "SmartFeed ALS batch completed: "
            f"users={users_count}, ratings={ratings_count}, "
            f"saved_recommendations={recommendations_count}, "
            f"model_version={config.model_version}."
        )
    finally:
        spark.stop()


if __name__ == "__main__":
    main()
