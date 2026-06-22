package com.smartfeed.data.api;

public interface TokenProvider {

    TokenProvider EMPTY = () -> null;

    String getAccessToken();
}
