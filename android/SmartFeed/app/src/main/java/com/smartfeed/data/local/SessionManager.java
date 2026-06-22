package com.smartfeed.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.smartfeed.data.api.TokenProvider;
import com.smartfeed.worker.EventSyncScheduler;

import java.util.Objects;
import java.util.UUID;

public final class SessionManager implements TokenProvider {

    private static final String PREFERENCES_NAME = "smartfeed_session";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_SESSION_ID = "session_id";

    private final SharedPreferences preferences;
    private final Context applicationContext;

    public SessionManager(Context context) {
        applicationContext = Objects.requireNonNull(context, "context")
                .getApplicationContext();
        preferences = applicationContext.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );
    }

    @Override
    public String getAccessToken() {
        return preferences.getString(KEY_ACCESS_TOKEN, null);
    }

    public boolean hasAccessToken() {
        String token = getAccessToken();
        return token != null && !token.trim().isEmpty();
    }

    public void saveAccessToken(String accessToken) {
        String normalizedToken = Objects.requireNonNull(accessToken, "accessToken").trim();
        if (normalizedToken.isEmpty()) {
            throw new IllegalArgumentException("accessToken must not be empty");
        }
        preferences.edit().putString(KEY_ACCESS_TOKEN, normalizedToken).apply();
        EventSyncScheduler.enqueueOneTime(applicationContext);
    }

    public synchronized String getOrCreateSessionId() {
        String sessionId = preferences.getString(KEY_SESSION_ID, null);
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            return sessionId;
        }

        String newSessionId = UUID.randomUUID().toString();
        preferences.edit().putString(KEY_SESSION_ID, newSessionId).apply();
        return newSessionId;
    }

    public void clearSession() {
        preferences.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_SESSION_ID)
                .apply();
    }
}
