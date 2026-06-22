package com.smartfeed.data.repository;

public final class ApiResult<T> {

    public enum Type {
        SUCCESS,
        HTTP_ERROR,
        NETWORK_ERROR,
        UNEXPECTED_ERROR
    }

    private final Type type;
    private final T data;
    private final int httpCode;
    private final String message;
    private final Throwable cause;

    private ApiResult(Type type, T data, int httpCode, String message, Throwable cause) {
        this.type = type;
        this.data = data;
        this.httpCode = httpCode;
        this.message = message;
        this.cause = cause;
    }

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(Type.SUCCESS, data, 0, null, null);
    }

    public static <T> ApiResult<T> httpError(int httpCode, String message) {
        return new ApiResult<>(Type.HTTP_ERROR, null, httpCode, message, null);
    }

    public static <T> ApiResult<T> networkError(String message, Throwable cause) {
        return new ApiResult<>(Type.NETWORK_ERROR, null, 0, message, cause);
    }

    public static <T> ApiResult<T> unexpectedError(String message, Throwable cause) {
        return new ApiResult<>(Type.UNEXPECTED_ERROR, null, 0, message, cause);
    }

    public Type getType() {
        return type;
    }

    public boolean isSuccess() {
        return type == Type.SUCCESS;
    }

    public T getData() {
        return data;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getCause() {
        return cause;
    }
}
