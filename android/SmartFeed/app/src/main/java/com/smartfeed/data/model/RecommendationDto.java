package com.smartfeed.data.model;

import com.google.gson.annotations.SerializedName;

public final class RecommendationDto {

    private long id;

    @SerializedName("user_id")
    private long userId;

    @SerializedName("article_id")
    private long articleId;

    private double score;
    private String reason;

    @SerializedName("model_version")
    private String modelVersion;

    @SerializedName("created_at")
    private String createdAt;

    private ArticleDto article;

    public long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public long getArticleId() {
        return articleId;
    }

    public double getScore() {
        return score;
    }

    public String getReason() {
        return reason;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public ArticleDto getArticle() {
        return article;
    }
}
