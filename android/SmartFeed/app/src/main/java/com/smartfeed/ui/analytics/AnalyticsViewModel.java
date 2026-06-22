package com.smartfeed.ui.analytics;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smartfeed.R;
import com.smartfeed.data.api.ApiClient;
import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.local.SessionManager;
import com.smartfeed.data.model.UserAnalyticsDto;
import com.smartfeed.data.repository.AnalyticsRepository;
import com.smartfeed.data.repository.ApiResult;
import com.smartfeed.util.NetworkMonitor;
import com.smartfeed.util.UserMessageResolver;

import retrofit2.Call;

public final class AnalyticsViewModel extends AndroidViewModel {

    private static final int FAVORITE_CATEGORY_LIMIT = 5;

    private final MutableLiveData<AnalyticsState> state = new MutableLiveData<>(
            new AnalyticsState(false, null, null)
    );
    private final AnalyticsRepository repository;
    private Call<UserAnalyticsDto> activeCall;

    public AnalyticsViewModel(@NonNull Application application) {
        super(application);
        SmartFeedApi api = ApiClient.create(new SessionManager(application));
        repository = new AnalyticsRepository(api);
        loadAnalytics();
    }

    public LiveData<AnalyticsState> getState() {
        return state;
    }

    public void refresh() {
        loadAnalytics();
    }

    public void clearError() {
        AnalyticsState current = state.getValue();
        if (current != null && current.getErrorMessage() != null) {
            state.setValue(new AnalyticsState(
                    current.isLoading(),
                    current.getAnalytics(),
                    null
            ));
        }
    }

    private void loadAnalytics() {
        if (activeCall != null && !activeCall.isCanceled()) {
            return;
        }

        AnalyticsState current = state.getValue();
        UserAnalyticsDto analytics = current == null ? null : current.getAnalytics();
        state.setValue(new AnalyticsState(true, analytics, null));

        if (!NetworkMonitor.isConnected(getApplication())) {
            state.setValue(new AnalyticsState(
                    false,
                    analytics,
                    getApplication().getString(R.string.error_network)
            ));
            return;
        }

        activeCall = repository.loadMyAnalytics(
                FAVORITE_CATEGORY_LIMIT,
                this::handleResult
        );
    }

    private void handleResult(ApiResult<UserAnalyticsDto> result) {
        activeCall = null;
        AnalyticsState current = state.getValue();
        UserAnalyticsDto currentAnalytics = current == null
                ? null
                : current.getAnalytics();
        if (result.isSuccess() && result.getData() != null) {
            state.postValue(new AnalyticsState(false, result.getData(), null));
            return;
        }
        state.postValue(new AnalyticsState(
                false,
                currentAnalytics,
                errorMessage(result)
        ));
    }

    private String errorMessage(ApiResult<?> result) {
        return UserMessageResolver.resolve(
                getApplication(),
                result,
                R.string.error_analytics_load
        );
    }

    @Override
    protected void onCleared() {
        if (activeCall != null && !activeCall.isCanceled()) {
            activeCall.cancel();
        }
        super.onCleared();
    }

    public static final class AnalyticsState {

        private final boolean loading;
        private final UserAnalyticsDto analytics;
        private final String errorMessage;

        private AnalyticsState(
                boolean loading,
                UserAnalyticsDto analytics,
                String errorMessage
        ) {
            this.loading = loading;
            this.analytics = analytics;
            this.errorMessage = errorMessage;
        }

        public boolean isLoading() {
            return loading;
        }

        public UserAnalyticsDto getAnalytics() {
            return analytics;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
