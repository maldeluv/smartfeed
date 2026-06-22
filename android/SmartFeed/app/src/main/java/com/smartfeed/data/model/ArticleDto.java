package com.smartfeed.data.model;

import com.google.gson.annotations.SerializedName;

public final class ArticleDto {

    private long id;
    private String title;
    private String summary;
    private String content;

    @SerializedName("source_url")
    private String sourceUrl;

    @SerializedName("category_id")
    private long categoryId;

    @SerializedName(value = "category_name", alternate = {"categoryName"})
    private String categoryName;

    private CategoryDto category;
    private String author;

    @SerializedName("published_at")
    private String publishedAt;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("popularity_score")
    private double popularityScore;

    @SerializedName(value = "is_liked", alternate = {"isLiked"})
    private Boolean liked;

    @SerializedName(value = "is_saved", alternate = {"isSaved"})
    private Boolean saved;

    private ArticleDto(ArticleDto source, boolean liked, boolean saved) {
        id = source.id;
        title = source.title;
        summary = source.summary;
        content = source.content;
        sourceUrl = source.sourceUrl;
        categoryId = source.categoryId;
        categoryName = source.categoryName;
        category = source.category;
        author = source.author;
        publishedAt = source.publishedAt;
        createdAt = source.createdAt;
        popularityScore = source.popularityScore;
        this.liked = liked;
        this.saved = saved;
    }

    public static ArticleDto fromCache(
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
            boolean saved
    ) {
        ArticleDto article = new ArticleDto();
        article.id = id;
        article.title = title;
        article.summary = summary;
        article.content = content;
        article.sourceUrl = sourceUrl;
        article.categoryId = categoryId;
        article.categoryName = categoryName;
        article.author = author;
        article.publishedAt = publishedAt;
        article.createdAt = createdAt;
        article.popularityScore = popularityScore;
        article.liked = liked;
        article.saved = saved;
        return article;
    }

    private ArticleDto() {
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
        return categoryName != null
                ? categoryName
                : category == null ? null : category.getName();
    }

    public CategoryDto getCategory() {
        return category;
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
        return Boolean.TRUE.equals(liked);
    }

    public boolean isSaved() {
        return Boolean.TRUE.equals(saved);
    }

    public ArticleDto withLiked(boolean value) {
        return new ArticleDto(this, value, isSaved());
    }

    public ArticleDto withSaved(boolean value) {
        return new ArticleDto(this, isLiked(), value);
    }
}
