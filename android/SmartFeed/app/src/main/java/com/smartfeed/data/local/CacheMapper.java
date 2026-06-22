package com.smartfeed.data.local;

import com.smartfeed.data.local.entity.ArticleEntity;
import com.smartfeed.data.local.entity.CategoryEntity;
import com.smartfeed.data.model.ArticleDto;
import com.smartfeed.data.model.CategoryDto;

import java.util.ArrayList;
import java.util.List;

public final class CacheMapper {

    private CacheMapper() {
    }

    public static ArticleEntity toEntity(
            ArticleDto article,
            long cachedAt,
            boolean inFeed
    ) {
        return new ArticleEntity(
                article.getId(),
                article.getTitle(),
                article.getSummary(),
                article.getContent(),
                article.getSourceUrl(),
                article.getCategoryId(),
                article.getCategoryName(),
                article.getAuthor(),
                article.getPublishedAt(),
                article.getCreatedAt(),
                article.getPopularityScore(),
                article.isLiked(),
                article.isSaved(),
                inFeed,
                cachedAt
        );
    }

    public static ArticleDto toDto(ArticleEntity article) {
        return ArticleDto.fromCache(
                article.getId(),
                article.getTitle(),
                article.getSummary(),
                article.getContent(),
                article.getSourceUrl(),
                article.getCategoryId(),
                article.getCategoryName(),
                article.getAuthor(),
                article.getPublishedAt(),
                article.getCreatedAt(),
                article.getPopularityScore(),
                article.isLiked(),
                article.isSaved()
        );
    }

    public static List<ArticleEntity> toArticleEntities(
            List<ArticleDto> articles,
            long cachedAt,
            boolean inFeed
    ) {
        List<ArticleEntity> entities = new ArrayList<>(articles.size());
        for (ArticleDto article : articles) {
            entities.add(toEntity(article, cachedAt, inFeed));
        }
        return entities;
    }

    public static List<ArticleDto> toArticleDtos(List<ArticleEntity> articles) {
        List<ArticleDto> dtos = new ArrayList<>(articles.size());
        for (ArticleEntity article : articles) {
            dtos.add(toDto(article));
        }
        return dtos;
    }

    public static CategoryEntity toEntity(CategoryDto category, long cachedAt) {
        return new CategoryEntity(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.isSubscribed(),
                cachedAt
        );
    }

    public static CategoryDto toDto(CategoryEntity category) {
        return CategoryDto.fromCache(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.isSubscribed()
        );
    }

    public static List<CategoryEntity> toCategoryEntities(
            List<CategoryDto> categories,
            long cachedAt
    ) {
        List<CategoryEntity> entities = new ArrayList<>(categories.size());
        for (CategoryDto category : categories) {
            entities.add(toEntity(category, cachedAt));
        }
        return entities;
    }

    public static List<CategoryDto> toCategoryDtos(List<CategoryEntity> categories) {
        List<CategoryDto> dtos = new ArrayList<>(categories.size());
        for (CategoryEntity category : categories) {
            dtos.add(toDto(category));
        }
        return dtos;
    }
}
