package com.smartfeed.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class UserAnalyticsDto {

    private String source;

    @SerializedName("favorite_categories")
    private List<FavoriteCategoryDto> favoriteCategories;

    @SerializedName("views_count")
    private int viewsCount;

    @SerializedName("likes_count")
    private int likesCount;

    @SerializedName("saves_count")
    private int savesCount;

    @SerializedName("recommendations_opened")
    private int recommendationsOpened;

    @SerializedName("recommended_articles_opened")
    private int recommendedArticlesOpened;

    @SerializedName("recommendation_ctr")
    private double recommendationCtr;

    public String getSource() {
        return source;
    }

    public List<FavoriteCategoryDto> getFavoriteCategories() {
        return favoriteCategories;
    }

    public int getViewsCount() {
        return viewsCount;
    }

    public int getLikesCount() {
        return likesCount;
    }

    public int getSavesCount() {
        return savesCount;
    }

    public int getRecommendationsOpened() {
        return recommendationsOpened;
    }

    public int getRecommendedArticlesOpened() {
        return recommendedArticlesOpened;
    }

    public double getRecommendationCtr() {
        return recommendationCtr;
    }

    public static final class FavoriteCategoryDto {

        @SerializedName("category_id")
        private long categoryId;

        @SerializedName("category_name")
        private String categoryName;

        private double score;

        @SerializedName("events_count")
        private int eventsCount;

        @SerializedName("views_count")
        private int viewsCount;

        @SerializedName("likes_count")
        private int likesCount;

        @SerializedName("saves_count")
        private int savesCount;

        public long getCategoryId() {
            return categoryId;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public double getScore() {
            return score;
        }

        public int getEventsCount() {
            return eventsCount;
        }

        public int getViewsCount() {
            return viewsCount;
        }

        public int getLikesCount() {
            return likesCount;
        }

        public int getSavesCount() {
            return savesCount;
        }
    }
}
