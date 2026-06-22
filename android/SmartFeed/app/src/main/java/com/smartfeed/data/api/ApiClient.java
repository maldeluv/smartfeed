package com.smartfeed.data.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.smartfeed.BuildConfig;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {

    private static final long CONNECT_TIMEOUT_SECONDS = 8L;
    private static final long IO_TIMEOUT_SECONDS = 20L;
    private final SmartFeedApi service;

    public ApiClient(TokenProvider tokenProvider) {
        Objects.requireNonNull(tokenProvider, "tokenProvider");

        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(
                BuildConfig.DEBUG
                        ? HttpLoggingInterceptor.Level.BASIC
                        : HttpLoggingInterceptor.Level.NONE
        );

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(new AuthInterceptor(tokenProvider))
                .addInterceptor(loggingInterceptor)
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();

        Gson gson = new GsonBuilder().create();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        service = retrofit.create(SmartFeedApi.class);
    }

    public SmartFeedApi getService() {
        return service;
    }

    public static SmartFeedApi create(TokenProvider tokenProvider) {
        return new ApiClient(tokenProvider).getService();
    }

    public static SmartFeedApi createPublic() {
        return create(TokenProvider.EMPTY);
    }
}
