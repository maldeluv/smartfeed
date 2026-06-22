from __future__ import annotations

import argparse
import glob
import os
from dataclasses import dataclass

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F
from pyspark.sql import types as T


VALID_EVENT_TYPES = [
    "view_article",
    "like_article",
    "unlike_article",
    "save_article",
    "unsave_article",
    "subscribe_category",
    "unsubscribe_category",
    "search",
    "open_recommendations",
    "open_recommended_article",
]

ARTICLE_EVENT_TYPES = [
    "view_article",
    "like_article",
    "unlike_article",
    "save_article",
    "unsave_article",
    "open_recommended_article",
]

CATEGORY_EVENT_TYPES = ["subscribe_category", "unsubscribe_category"]

SCORE_BY_EVENT_TYPE = {
    "view_article": 1.0,
    "like_article": 3.0,
    "save_article": 5.0,
    "subscribe_category": 4.0,
    "open_recommended_article": 2.0,
    "unlike_article": -3.0,
    "unsave_article": -5.0,
    "unsubscribe_category": -4.0,
}


EVENT_SCHEMA = T.StructType(
    [
        T.StructField("event_id", T.StringType()),
        T.StructField("user_id", T.LongType()),
        T.StructField("session_id", T.StringType()),
        T.StructField("event_type", T.StringType()),
        T.StructField("article_id", T.LongType()),
        T.StructField("category_id", T.LongType()),
        T.StructField("timestamp", T.StringType()),
        T.StructField("device", T.MapType(T.StringType(), T.StringType())),
        T.StructField("metadata", T.MapType(T.StringType(), T.StringType())),
    ]
)


@dataclass(frozen=True)
class StreamingConfig:
    kafka_bootstrap_servers: str
    topic: str
    starting_offsets: str
    checkpoint_dir: str
    output_base: str
    mongo_uri: str
    mongo_database: str
    mongo_collection: str
    postgres_jdbc_url: str
    postgres_user: str
    postgres_password: str
    processing_time: str
    available_now: bool


def build_spark(config: StreamingConfig) -> SparkSession:
    return (
        SparkSession.builder.appName("SmartFeedStructuredStreaming")
        .config("spark.sql.session.timeZone", "UTC")
        .config("spark.mongodb.write.connection.uri", config.mongo_uri)
        .getOrCreate()
    )


def read_kafka_events(spark: SparkSession, config: StreamingConfig) -> DataFrame:
    raw_events = (
        spark.readStream.format("kafka")
        .option("kafka.bootstrap.servers", config.kafka_bootstrap_servers)
        .option("subscribe", config.topic)
        .option("startingOffsets", config.starting_offsets)
        .option("failOnDataLoss", "false")
        .load()
    )

    parsed = raw_events.select(
        F.col("topic"),
        F.col("partition"),
        F.col("offset"),
        F.col("timestamp").alias("kafka_timestamp"),
        F.col("value").cast("string").alias("raw_json"),
    ).withColumn("event", F.from_json(F.col("raw_json"), EVENT_SCHEMA))

    events = parsed.select(
        "topic",
        "partition",
        "offset",
        "kafka_timestamp",
        "raw_json",
        F.col("event.event_id").alias("event_id"),
        F.col("event.user_id").alias("user_id"),
        F.col("event.session_id").alias("session_id"),
        F.col("event.event_type").alias("event_type"),
        F.col("event.article_id").alias("article_id"),
        F.col("event.category_id").alias("category_id"),
        F.to_timestamp(F.col("event.timestamp")).alias("event_timestamp"),
        F.col("event.device").alias("device"),
        F.col("event.metadata").alias("metadata"),
    )

    article_event = F.col("event_type").isin(ARTICLE_EVENT_TYPES)
    category_event = F.col("event_type").isin(CATEGORY_EVENT_TYPES)
    base_valid = (
        F.col("event_id").isNotNull()
        & F.col("user_id").isNotNull()
        & F.col("session_id").isNotNull()
        & F.col("event_type").isin(VALID_EVENT_TYPES)
        & F.col("event_timestamp").isNotNull()
    )
    links_valid = (
        (~article_event | F.col("article_id").isNotNull())
        & (~category_event | F.col("category_id").isNotNull())
    )

    return (
        events.withColumn("is_valid", base_valid & links_valid)
        .withColumn(
            "validation_error",
            F.when(~base_valid, F.lit("missing_required_field_or_invalid_event_type"))
            .when(~links_valid, F.lit("missing_required_article_or_category_link"))
            .otherwise(F.lit(None)),
        )
        .withWatermark("event_timestamp", "2 days")
    )


def write_raw_events_to_mongo(batch_df: DataFrame, config: StreamingConfig) -> None:
    raw_events = batch_df.select(
        "event_id",
        "user_id",
        "session_id",
        "event_type",
        "article_id",
        "category_id",
        "event_timestamp",
        "device",
        "metadata",
        "raw_json",
        "topic",
        "partition",
        "offset",
        "kafka_timestamp",
    )
    (
        raw_events.write.format("mongodb")
        .mode("append")
        .option("database", config.mongo_database)
        .option("collection", config.mongo_collection)
        .save()
    )


def append_parquet(df: DataFrame, path: str) -> None:
    df.write.mode("append").parquet(path)


def write_invalid_events(invalid_df: DataFrame, config: StreamingConfig) -> None:
    if invalid_df.rdd.isEmpty():
        return
    append_parquet(invalid_df, f"{config.output_base}/dead_letter_events")


def write_event_type_aggregates(valid_df: DataFrame, config: StreamingConfig) -> None:
    aggregates = (
        valid_df.groupBy(
            F.window("event_timestamp", "1 day").alias("event_window"),
            "event_type",
        )
        .agg(
            F.count("*").alias("events_count"),
            F.countDistinct("user_id").alias("unique_users_count"),
        )
        .select(
            F.col("event_window.start").alias("window_start"),
            F.col("event_window.end").alias("window_end"),
            "event_type",
            "events_count",
            "unique_users_count",
        )
    )
    if not aggregates.rdd.isEmpty():
        append_parquet(aggregates, f"{config.output_base}/aggregates/events_by_type")


def write_top_category_aggregates(valid_df: DataFrame, config: StreamingConfig) -> None:
    aggregates = (
        valid_df.where(F.col("category_id").isNotNull())
        .groupBy(
            F.window("event_timestamp", "1 day").alias("event_window"),
            "category_id",
        )
        .agg(
            F.count("*").alias("events_count"),
            F.countDistinct("user_id").alias("unique_users_count"),
        )
        .select(
            F.col("event_window.start").alias("window_start"),
            F.col("event_window.end").alias("window_end"),
            "category_id",
            "events_count",
            "unique_users_count",
        )
    )
    if not aggregates.rdd.isEmpty():
        append_parquet(aggregates, f"{config.output_base}/aggregates/top_categories")


def write_user_activity_aggregates(valid_df: DataFrame, config: StreamingConfig) -> None:
    aggregates = (
        valid_df.groupBy(
            F.to_date("event_timestamp").alias("event_date"),
            "user_id",
        )
        .agg(
            F.count("*").alias("events_count"),
            F.countDistinct("session_id").alias("sessions_count"),
        )
    )
    if not aggregates.rdd.isEmpty():
        append_parquet(aggregates, f"{config.output_base}/aggregates/user_activity_daily")


def write_user_category_score_deltas(deltas: DataFrame, config: StreamingConfig, batch_id: int) -> None:
    if deltas.rdd.isEmpty():
        return

    audit_df = deltas.withColumn("batch_id", F.lit(batch_id)).withColumn(
        "processed_at",
        F.current_timestamp(),
    )
    append_parquet(audit_df, f"{config.output_base}/aggregates/user_category_score_deltas")


def score_expression() -> F.Column:
    expression = F.lit(0.0)
    for event_type, score in SCORE_BY_EVENT_TYPE.items():
        expression = F.when(F.col("event_type") == event_type, F.lit(score)).otherwise(expression)
    return expression


def build_user_category_score_deltas(valid_df: DataFrame) -> DataFrame:
    return (
        valid_df.where(F.col("category_id").isNotNull())
        .withColumn("score_delta", score_expression())
        .withColumn(
            "views_delta",
            F.when(F.col("event_type") == "view_article", F.lit(1)).otherwise(F.lit(0)),
        )
        .withColumn(
            "likes_delta",
            F.when(F.col("event_type") == "like_article", F.lit(1))
            .when(F.col("event_type") == "unlike_article", F.lit(-1))
            .otherwise(F.lit(0)),
        )
        .withColumn(
            "saves_delta",
            F.when(F.col("event_type") == "save_article", F.lit(1))
            .when(F.col("event_type") == "unsave_article", F.lit(-1))
            .otherwise(F.lit(0)),
        )
        .groupBy("user_id", "category_id")
        .agg(
            F.sum("score_delta").alias("score_delta"),
            F.sum("views_delta").alias("views_delta"),
            F.sum("likes_delta").alias("likes_delta"),
            F.sum("saves_delta").alias("saves_delta"),
        )
    )


def upsert_user_category_scores(spark: SparkSession, deltas: DataFrame, config: StreamingConfig) -> None:
    rows = deltas.collect()
    if not rows:
        return

    connection = open_postgres_jdbc_connection(spark, config)
    connection.setAutoCommit(False)
    statement = connection.prepareStatement(
        """
        INSERT INTO user_category_scores
            (user_id, category_id, score, views_count, likes_count, saves_count, updated_at)
        VALUES (?, ?, GREATEST(0, ?), GREATEST(0, ?), GREATEST(0, ?), GREATEST(0, ?), NOW())
        ON CONFLICT (user_id, category_id)
        DO UPDATE SET
            score = GREATEST(0, user_category_scores.score + EXCLUDED.score),
            views_count = GREATEST(0, user_category_scores.views_count + EXCLUDED.views_count),
            likes_count = GREATEST(0, user_category_scores.likes_count + EXCLUDED.likes_count),
            saves_count = GREATEST(0, user_category_scores.saves_count + EXCLUDED.saves_count),
            updated_at = NOW()
        """
    )
    try:
        for row in rows:
            statement.setLong(1, int(row["user_id"]))
            statement.setLong(2, int(row["category_id"]))
            statement.setDouble(3, float(row["score_delta"] or 0.0))
            statement.setInt(4, int(row["views_delta"] or 0))
            statement.setInt(5, int(row["likes_delta"] or 0))
            statement.setInt(6, int(row["saves_delta"] or 0))
            statement.addBatch()
        statement.executeBatch()
        connection.commit()
    finally:
        statement.close()
        connection.close()


def open_postgres_jdbc_connection(spark: SparkSession, config: StreamingConfig):
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


def process_batch(config: StreamingConfig, spark: SparkSession):
    def _process(batch_df: DataFrame, batch_id: int) -> None:
        if batch_df.rdd.isEmpty():
            return

        invalid_df = batch_df.where(~F.col("is_valid"))
        valid_df = batch_df.where(F.col("is_valid")).cache()

        write_invalid_events(invalid_df, config)
        if not valid_df.rdd.isEmpty():
            write_raw_events_to_mongo(valid_df, config)
            write_event_type_aggregates(valid_df, config)
            write_top_category_aggregates(valid_df, config)
            write_user_activity_aggregates(valid_df, config)
            score_deltas = build_user_category_score_deltas(valid_df).cache()
            write_user_category_score_deltas(score_deltas, config, batch_id)
            try:
                upsert_user_category_scores(spark=spark, deltas=score_deltas, config=config)
            except Exception as exc:
                print(
                    "PostgreSQL user_category_scores upsert failed; "
                    f"score deltas were saved to parquet fallback. Error: {exc}"
                )
            finally:
                score_deltas.unpersist()

        valid_df.unpersist()
        print(f"Processed Spark micro-batch {batch_id}.")

    return _process


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="SmartFeed Spark Structured Streaming job.")
    parser.add_argument("--kafka-bootstrap-servers", default=os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092"))
    parser.add_argument("--topic", default=os.getenv("KAFKA_USER_EVENTS_TOPIC", "smartfeed.user_events"))
    parser.add_argument("--starting-offsets", default=os.getenv("SPARK_STARTING_OFFSETS", "latest"))
    parser.add_argument("--checkpoint-dir", default=os.getenv("SPARK_CHECKPOINT_DIR", "/opt/spark/work-dir/data/checkpoints/structured_streaming"))
    parser.add_argument("--output-base", default=os.getenv("SPARK_OUTPUT_BASE", "/opt/spark/work-dir/data/streaming"))
    parser.add_argument("--mongo-uri", default=os.getenv("MONGODB_URL", "mongodb://smartfeed:smartfeed@mongo:27017/smartfeed?authSource=admin"))
    parser.add_argument("--mongo-database", default=os.getenv("MONGO_DB", "smartfeed"))
    parser.add_argument("--mongo-collection", default=os.getenv("MONGO_RAW_EVENTS_COLLECTION", "raw_user_events"))
    parser.add_argument("--postgres-jdbc-url", default=os.getenv("POSTGRES_JDBC_URL", "jdbc:postgresql://postgres:5432/smartfeed"))
    parser.add_argument("--postgres-user", default=os.getenv("POSTGRES_USER", "smartfeed"))
    parser.add_argument("--postgres-password", default=os.getenv("POSTGRES_PASSWORD", "smartfeed"))
    parser.add_argument("--processing-time", default=os.getenv("SPARK_PROCESSING_TIME", "10 seconds"))
    parser.add_argument("--available-now", action="store_true", help="Process available Kafka offsets and stop.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    config = StreamingConfig(**vars(args))
    spark = build_spark(config)
    spark.sparkContext.setLogLevel("WARN")

    stream_df = read_kafka_events(spark, config)
    writer = stream_df.writeStream.foreachBatch(process_batch(config=config, spark=spark)).option(
        "checkpointLocation",
        config.checkpoint_dir,
    )
    if config.available_now:
        writer = writer.trigger(availableNow=True)
    else:
        writer = writer.trigger(processingTime=config.processing_time)

    query = writer.start()
    query.awaitTermination()


if __name__ == "__main__":
    main()
