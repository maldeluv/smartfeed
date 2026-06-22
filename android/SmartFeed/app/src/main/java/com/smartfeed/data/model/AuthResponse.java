package com.smartfeed.data.model;

import com.google.gson.annotations.SerializedName;

public final class AuthResponse {

    @SerializedName("access_token")
    private String accessToken;

    @SerializedName("token_type")
    private String tokenType;

    private UserDto user;

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public UserDto getUser() {
        return user;
    }
}
