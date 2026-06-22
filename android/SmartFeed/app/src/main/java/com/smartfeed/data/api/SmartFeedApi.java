package com.smartfeed.data.api;

import com.smartfeed.data.model.ArticleDto;
import com.smartfeed.data.model.ArticlePageDto;
import com.smartfeed.data.model.AuthRequest;
import com.smartfeed.data.model.AuthResponse;
import com.smartfeed.data.model.BatchEventResponseDto;
import com.smartfeed.data.model.BatchEventsRequestDto;
import com.smartfeed.data.model.CategoryDto;
import com.smartfeed.data.model.EventAcceptedResponseDto;
import com.smartfeed.data.model.RecommendationPageDto;
import com.smartfeed.data.model.SmartFeedEventDto;
import com.smartfeed.data.model.StatusResponse;
import com.smartfeed.data.model.UserAnalyticsDto;
import com.smartfeed.data.model.UserDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface SmartFeedApi {

    @POST("api/v1/auth/register")
    Call<AuthResponse> register(@Body AuthRequest request);

    @POST("api/v1/auth/login")
    Call<AuthResponse> login(@Body AuthRequest request);

    @GET("api/v1/users/me")
    Call<UserDto> getCurrentUser();

    @GET("api/v1/categories")
    Call<List<CategoryDto>> getCategories();

    @POST("api/v1/categories/{id}/subscribe")
    Call<StatusResponse> subscribeCategory(@Path("id") long categoryId);

    @DELETE("api/v1/categories/{id}/subscribe")
    Call<StatusResponse> unsubscribeCategory(@Path("id") long categoryId);

    @GET("api/v1/articles")
    Call<ArticlePageDto> getArticles(
            @Query("category_id") Long categoryId,
            @Query("search") String search,
            @Query("limit") Integer limit,
            @Query("offset") Integer offset
    );

    @GET("api/v1/articles/{id}")
    Call<ArticleDto> getArticle(@Path("id") long articleId);

    @POST("api/v1/articles/{id}/like")
    Call<StatusResponse> likeArticle(@Path("id") long articleId);

    @DELETE("api/v1/articles/{id}/like")
    Call<StatusResponse> unlikeArticle(@Path("id") long articleId);

    @POST("api/v1/articles/{id}/save")
    Call<StatusResponse> saveArticle(@Path("id") long articleId);

    @DELETE("api/v1/articles/{id}/save")
    Call<StatusResponse> unsaveArticle(@Path("id") long articleId);

    @GET("api/v1/saved")
    Call<ArticlePageDto> getSavedArticles(
            @Query("limit") Integer limit,
            @Query("offset") Integer offset
    );

    @POST("api/v1/events")
    Call<EventAcceptedResponseDto> sendEvent(@Body SmartFeedEventDto event);

    @POST("api/v1/events/batch")
    Call<BatchEventResponseDto> sendEvents(@Body BatchEventsRequestDto request);

    @GET("api/v1/recommendations")
    Call<RecommendationPageDto> getRecommendations(
            @Query("limit") Integer limit,
            @Query("offset") Integer offset
    );

    @GET("api/v1/analytics/me")
    Call<UserAnalyticsDto> getMyAnalytics(@Query("limit") Integer limit);
}
