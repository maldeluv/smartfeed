package com.smartfeed.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "categories")
public final class CategoryEntity {

    @PrimaryKey
    private final long id;
    private final String name;
    private final String slug;
    private final String description;
    @ColumnInfo(name = "is_subscribed")
    private final boolean subscribed;
    @ColumnInfo(name = "cached_at")
    private final long cachedAt;

    public CategoryEntity(
            long id,
            String name,
            String slug,
            String description,
            boolean subscribed,
            long cachedAt
    ) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.subscribed = subscribed;
        this.cachedAt = cachedAt;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSubscribed() {
        return subscribed;
    }

    public long getCachedAt() {
        return cachedAt;
    }
}
