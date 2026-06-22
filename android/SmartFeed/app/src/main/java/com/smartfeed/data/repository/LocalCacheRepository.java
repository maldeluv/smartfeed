package com.smartfeed.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.smartfeed.data.local.AppDatabase;
import com.smartfeed.data.local.CacheMapper;
import com.smartfeed.data.local.entity.ArticleEntity;
import com.smartfeed.data.local.entity.CategoryEntity;
import com.smartfeed.data.local.entity.SavedArticleEntity;
import com.smartfeed.data.model.ArticleDto;
import com.smartfeed.data.model.CategoryDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LocalCacheRepository {

    private static final String TAG = "SmartFeedCache";

    private final AppDatabase database;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public LocalCacheRepository(Context context) {
        database = AppDatabase.getInstance(
                Objects.requireNonNull(context, "context").getApplicationContext()
        );
    }

    public void cacheFeed(List<ArticleDto> articles) {
        List<ArticleDto> snapshot = new ArrayList<>(articles);
        AppDatabase.DATABASE_EXECUTOR.execute(() -> database.runInTransaction(() -> {
            long cachedAt = System.currentTimeMillis();
            cacheArticleCategories(snapshot, cachedAt);
            database.articleDao().clearFeedFlags();
            database.articleDao().upsertAll(
                    CacheMapper.toArticleEntities(snapshot, cachedAt, true)
            );
            syncSavedRelations(snapshot, cachedAt);
        }));
    }

    public void loadFeed(CacheCallback<List<ArticleDto>> callback) {
        executeRead(
                () -> CacheMapper.toArticleDtos(database.articleDao().getFeed()),
                callback,
                new ArrayList<>()
        );
    }

    public void cacheSavedArticles(List<ArticleDto> articles) {
        List<ArticleDto> snapshot = new ArrayList<>(articles);
        AppDatabase.DATABASE_EXECUTOR.execute(() -> database.runInTransaction(() -> {
            long cachedAt = System.currentTimeMillis();
            cacheArticleCategories(snapshot, cachedAt);
            database.articleDao().clearSavedFlags();
            List<ArticleEntity> entities = new ArrayList<>(snapshot.size());
            List<SavedArticleEntity> savedEntities = new ArrayList<>(snapshot.size());
            for (ArticleDto article : snapshot) {
                ArticleDto savedArticle = article.isSaved()
                        ? article
                        : article.withSaved(true);
                ArticleEntity current = database.articleDao().getById(article.getId());
                boolean inFeed = current != null && current.isInFeed();
                entities.add(CacheMapper.toEntity(savedArticle, cachedAt, inFeed));
                savedEntities.add(new SavedArticleEntity(article.getId(), cachedAt));
            }
            database.articleDao().upsertAll(entities);
            database.savedArticleDao().clear();
            database.savedArticleDao().upsertAll(savedEntities);
        }));
    }

    public void loadSavedArticles(CacheCallback<List<ArticleDto>> callback) {
        executeRead(
                () -> CacheMapper.toArticleDtos(
                        database.savedArticleDao().getSavedArticles()
                ),
                callback,
                new ArrayList<>()
        );
    }

    public void cacheArticleState(ArticleDto article) {
        AppDatabase.DATABASE_EXECUTOR.execute(() -> database.runInTransaction(() -> {
            long cachedAt = System.currentTimeMillis();
            if (article.getCategory() != null) {
                database.categoryDao().upsertAll(Collections.singletonList(
                        CacheMapper.toEntity(article.getCategory(), cachedAt)
                ));
            }
            ArticleEntity current = database.articleDao().getById(article.getId());
            boolean inFeed = current != null && current.isInFeed();
            database.articleDao().upsert(
                    CacheMapper.toEntity(article, cachedAt, inFeed)
            );
            if (article.isSaved()) {
                database.savedArticleDao().upsert(
                        new SavedArticleEntity(article.getId(), cachedAt)
                );
            } else {
                database.savedArticleDao().delete(article.getId());
            }
        }));
    }

    public void updateArticleLiked(long articleId, boolean liked) {
        AppDatabase.DATABASE_EXECUTOR.execute(() ->
                database.articleDao().updateLiked(articleId, liked)
        );
    }

    public void updateArticleSaved(ArticleDto article) {
        cacheArticleState(article);
    }

    public void cacheCategories(List<CategoryDto> categories) {
        List<CategoryDto> snapshot = new ArrayList<>(categories);
        AppDatabase.DATABASE_EXECUTOR.execute(() -> database.categoryDao().upsertAll(
                CacheMapper.toCategoryEntities(snapshot, System.currentTimeMillis())
        ));
    }

    public void loadCategories(CacheCallback<List<CategoryDto>> callback) {
        executeRead(
                () -> CacheMapper.toCategoryDtos(database.categoryDao().getAll()),
                callback,
                new ArrayList<>()
        );
    }

    public void updateCategorySubscribed(long categoryId, boolean subscribed) {
        AppDatabase.DATABASE_EXECUTOR.execute(() ->
                database.categoryDao().updateSubscribed(categoryId, subscribed)
        );
    }

    private void cacheArticleCategories(List<ArticleDto> articles, long cachedAt) {
        Map<Long, CategoryDto> categories = new LinkedHashMap<>();
        for (ArticleDto article : articles) {
            CategoryDto category = article.getCategory();
            if (category != null) {
                categories.put(category.getId(), category);
            }
        }
        if (!categories.isEmpty()) {
            database.categoryDao().upsertAll(CacheMapper.toCategoryEntities(
                    new ArrayList<>(categories.values()),
                    cachedAt
            ));
        }
    }

    private void syncSavedRelations(List<ArticleDto> articles, long cachedAt) {
        for (ArticleDto article : articles) {
            if (article.isSaved()) {
                database.savedArticleDao().upsert(
                        new SavedArticleEntity(article.getId(), cachedAt)
                );
            } else {
                database.savedArticleDao().delete(article.getId());
            }
        }
    }

    private <T> void executeRead(
            CacheRead<T> read,
            CacheCallback<T> callback,
            T fallback
    ) {
        AppDatabase.DATABASE_EXECUTOR.execute(() -> {
            T value;
            try {
                value = read.load();
            } catch (RuntimeException exception) {
                Log.e(TAG, "Room cache read failed", exception);
                value = fallback;
            }
            T result = value;
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    public interface CacheCallback<T> {

        void onResult(T data);
    }

    private interface CacheRead<T> {

        T load();
    }
}
