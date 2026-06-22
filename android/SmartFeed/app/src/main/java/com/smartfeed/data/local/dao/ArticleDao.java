package com.smartfeed.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.smartfeed.data.local.entity.ArticleEntity;

import java.util.List;

@Dao
public interface ArticleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ArticleEntity article);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<ArticleEntity> articles);

    @Query("SELECT * FROM articles WHERE in_feed = 1 ORDER BY published_at DESC, id DESC")
    List<ArticleEntity> getFeed();

    @Query("SELECT * FROM articles WHERE id = :articleId LIMIT 1")
    ArticleEntity getById(long articleId);

    @Query("UPDATE articles SET in_feed = 0")
    void clearFeedFlags();

    @Query("UPDATE articles SET is_liked = :liked WHERE id = :articleId")
    void updateLiked(long articleId, boolean liked);

    @Query("UPDATE articles SET is_saved = :saved WHERE id = :articleId")
    void updateSaved(long articleId, boolean saved);

    @Query("UPDATE articles SET is_saved = 0")
    void clearSavedFlags();
}
