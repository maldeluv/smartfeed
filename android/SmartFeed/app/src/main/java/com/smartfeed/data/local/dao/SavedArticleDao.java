package com.smartfeed.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.smartfeed.data.local.entity.ArticleEntity;
import com.smartfeed.data.local.entity.SavedArticleEntity;

import java.util.List;

@Dao
public interface SavedArticleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SavedArticleEntity savedArticle);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<SavedArticleEntity> savedArticles);

    @Query("DELETE FROM saved_articles WHERE article_id = :articleId")
    void delete(long articleId);

    @Query("DELETE FROM saved_articles")
    void clear();

    @Query(
            "SELECT articles.* FROM articles "
                    + "INNER JOIN saved_articles "
                    + "ON saved_articles.article_id = articles.id "
                    + "ORDER BY articles.published_at DESC, articles.id DESC"
    )
    List<ArticleEntity> getSavedArticles();
}
