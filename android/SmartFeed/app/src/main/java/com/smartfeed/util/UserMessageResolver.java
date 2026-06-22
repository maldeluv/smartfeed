package com.smartfeed.util;

import android.content.Context;

import androidx.annotation.StringRes;

import com.smartfeed.R;
import com.smartfeed.data.repository.ApiResult;

public final class UserMessageResolver {

    private UserMessageResolver() {
    }

    public static String resolve(
            Context context,
            ApiResult<?> result,
            @StringRes int fallbackMessage
    ) {
        if (result.getType() == ApiResult.Type.NETWORK_ERROR) {
            return context.getString(R.string.error_network);
        }
        if (result.getType() == ApiResult.Type.UNEXPECTED_ERROR) {
            return context.getString(R.string.error_unknown);
        }
        if (result.getType() == ApiResult.Type.HTTP_ERROR) {
            int code = result.getHttpCode();
            if (code == 401) {
                return context.getString(R.string.error_session_expired);
            }
            if (code == 403) {
                return context.getString(R.string.error_access_denied);
            }
            if (code == 429) {
                return context.getString(R.string.error_too_many_requests);
            }
            if (code >= 500) {
                return context.getString(R.string.error_server);
            }
        }
        String message = result.getMessage();
        return message == null || message.trim().isEmpty()
                ? context.getString(fallbackMessage)
                : message;
    }

    public static String resolveLogin(Context context, ApiResult<?> result) {
        if (result.getType() == ApiResult.Type.HTTP_ERROR
                && result.getHttpCode() == 401) {
            return context.getString(R.string.error_invalid_credentials);
        }
        return resolve(context, result, R.string.error_login);
    }

    public static String resolveRegistration(Context context, ApiResult<?> result) {
        if (result.getType() == ApiResult.Type.HTTP_ERROR
                && result.getHttpCode() == 409) {
            return context.getString(R.string.error_email_exists);
        }
        return resolve(context, result, R.string.error_registration);
    }
}
