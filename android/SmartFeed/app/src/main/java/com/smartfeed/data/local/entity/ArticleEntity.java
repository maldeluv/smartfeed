package com.smartfeed.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "articles",
        indices = {
                @Index("category_id"),
                @Index("published_at")
        }
)
public final class ArticleEntity {

    @PrimaryKey
    private final long id;
    private final String title;
    private final String summary;
    private final String content;
    @ColumnInfo(name = "source_url")
    private final String sourceUrl;
    @ColumnInfo(name = "category_id")
    private final long categoryId;
    @ColumnInfo(name = "category_name")
    private final String categoryName;
    private final String author;
    @ColumnInfo(name = "published_at")
    private final String publishedAt;
    @ColumnInfo(name = "created_at")
    private final String createdAt;
    @ColumnInfo(name = "popularity_score")
    private final double popularityScore;
    @ColumnInfo(name = "is_liked")
    private final boolean liked;
    @ColumnInfo(name = "is_saved")
    private final boolean saved;
    @ColumnInfo(name = "in_feed")
    private final boolean inFeed;
    @ColumnInfo(name = "cached_at")
    private final long cachedAt;

    public ArticleEntity(
            long id,
            String title,
            String summary,
            String content,
            String sourceUrl,
            long categoryId,
            String categoryName,
            String author,
            String publishedAt,
            String createdAt,
            double popularityScore,
            boolean liked,
            boolean saved,
            boolean inFeed,
            long cachedAt
    ) {
        this.id = id;
        this.title = title;
        this.summary = summary;
        this.content = content;
        this.sourceUrl = sourceUrl;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.author = author;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
        this.popularityScore = popularityScore;
        this.liked = liked;
        this.saved = saved;
        this.inFeed = inFeed;
        this.cachedAt = cachedAt;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getContent() {
        return content;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getAuthor() {
        return author;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public double getPopularityScore() {
        return popularityScore;
    }

    public boolean isLiked() {
        return liked;
    }

    public boolean isSaved() {
        return saved;
    }

    public boolean isInFeed() {
        return inFeed;
    }

    public long getCachedAt() {
        return cachedAt;
    }
}
