package com.smartfeed.data.repository;

public interface RepositoryCallback<T> {

    void onResult(ApiResult<T> result);
}
