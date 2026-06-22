package com.smartfeed.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "saved_articles",
        foreignKeys = @ForeignKey(
                entity = ArticleEntity.class,
                parentColumns = "id",
                childColumns = "article_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = @Index("article_id")
)
public final class SavedArticleEntity {

    @PrimaryKey
    @ColumnInfo(name = "article_id")
    private final long articleId;
    @ColumnInfo(name = "saved_at")
    private final long savedAt;

    public SavedArticleEntity(long articleId, long savedAt) {
        this.articleId = articleId;
        this.savedAt = savedAt;
    }

    public long getArticleId() {
        return articleId;
    }

    public long getSavedAt() {
        return savedAt;
    }
}
