package com.smartfeed.data.api;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public final class AuthInterceptor implements Interceptor {

    private static final String AUTHORIZATION = "Authorization";
    private final TokenProvider tokenProvider;

    public AuthInterceptor(TokenProvider tokenProvider) {
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String token = tokenProvider.getAccessToken();

        if (token == null || token.trim().isEmpty() || request.header(AUTHORIZATION) != null) {
            return chain.proceed(request);
        }

        Request authenticatedRequest = request.newBuilder()
                .header(AUTHORIZATION, "Bearer " + token.trim())
                .build();
        return chain.proceed(authenticatedRequest);
    }
}
