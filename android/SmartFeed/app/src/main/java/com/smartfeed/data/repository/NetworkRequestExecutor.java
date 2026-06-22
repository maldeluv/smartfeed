package com.smartfeed.data.repository;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Objects;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class NetworkRequestExecutor {

    private NetworkRequestExecutor() {
    }

    public static <T> void enqueue(Call<T> call, RepositoryCallback<T> callback) {
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(callback, "callback");

        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(Call<T> request, Response<T> response) {
                if (response.isSuccessful()) {
                    callback.onResult(ApiResult.success(response.body()));
                    return;
                }

                callback.onResult(ApiResult.httpError(
                        response.code(),
                        readErrorMessage(response)
                ));
            }

            @Override
            public void onFailure(Call<T> request, Throwable throwable) {
                if (throwable instanceof IOException) {
                    callback.onResult(ApiResult.networkError(
                            "Unable to connect to SmartFeed backend",
                            throwable
                    ));
                    return;
                }

                callback.onResult(ApiResult.unexpectedError(
                        "Unexpected request error",
                        throwable
                ));
            }
        });
    }

    private static String readErrorMessage(Response<?> response) {
        ResponseBody errorBody = response.errorBody();
        if (errorBody == null) {
            return "HTTP " + response.code();
        }

        try {
            String rawBody = errorBody.string();
            if (rawBody.trim().isEmpty()) {
                return "HTTP " + response.code();
            }

            JsonElement root = JsonParser.parseString(rawBody);
            if (!root.isJsonObject()) {
                return rawBody;
            }

            JsonElement detail = root.getAsJsonObject().get("detail");
            if (detail == null) {
                return rawBody;
            }
            if (detail.isJsonPrimitive()) {
                return detail.getAsString();
            }
            if (detail.isJsonArray() && !detail.getAsJsonArray().isEmpty()) {
                JsonElement first = detail.getAsJsonArray().get(0);
                if (first.isJsonObject()) {
                    JsonObject firstError = first.getAsJsonObject();
                    JsonElement message = firstError.get("msg");
                    if (message != null && message.isJsonPrimitive()) {
                        return message.getAsString();
                    }
                }
            }
            return detail.toString();
        } catch (Exception ignored) {
            return "HTTP " + response.code();
        }
    }
}
