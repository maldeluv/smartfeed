package com.smartfeed.ui.recommendations;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smartfeed.R;
import com.smartfeed.data.api.ApiClient;
import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.local.SessionManager;
import com.smartfeed.data.model.RecommendationDto;
import com.smartfeed.data.model.RecommendationPageDto;
import com.smartfeed.data.repository.ApiResult;
import com.smartfeed.data.repository.RecommendationRepository;
import com.smartfeed.data.repository.EventRepository;
import com.smartfeed.util.EventFactory;
import com.smartfeed.util.NetworkMonitor;
import com.smartfeed.util.UserMessageResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;

public final class RecommendationsViewModel extends AndroidViewModel {

    private static final int RECOMMENDATIONS_LIMIT = 50;

    private final MutableLiveData<RecommendationsState> state = new MutableLiveData<>(
            new RecommendationsState(false, Collections.emptyList(), null)
    );
    private final RecommendationRepository repository;
    private final EventRepository eventRepository;
    private final SessionManager sessionManager;
    private Call<RecommendationPageDto> activeCall;

    public RecommendationsViewModel(@NonNull Application application) {
        super(application);
        sessionManager = new SessionManager(application);
        SmartFeedApi api = ApiClient.create(sessionManager);
        repository = new RecommendationRepository(api);
        eventRepository = new EventRepository(application);
        eventRepository.enqueueEvent(
                EventFactory.recommendationsOpened(sessionManager),
                ignored -> { }
        );
        loadRecommendations();
    }

    public LiveData<RecommendationsState> getState() {
        return state;
    }

    public void refresh() {
        loadRecommendations();
    }

    public void clearError() {
        RecommendationsState current = state.getValue();
        if (current != null && current.getErrorMessage() != null) {
            state.setValue(new RecommendationsState(
                    current.isLoading(),
                    current.getRecommendations(),
                    null
            ));
        }
    }

    private void loadRecommendations() {
        if (activeCall != null && !activeCall.isCanceled()) {
            return;
        }

        RecommendationsState current = state.getValue();
        List<RecommendationDto> currentItems = current == null
                ? Collections.emptyList()
                : current.getRecommendations();
        state.setValue(new RecommendationsState(true, currentItems, null));

        if (!NetworkMonitor.isConnected(getApplication())) {
            state.setValue(new RecommendationsState(
                    false,
                    currentItems,
                    getApplication().getString(R.string.error_network)
            ));
            return;
        }

        activeCall = repository.loadRecommendations(
                RECOMMENDATIONS_LIMIT,
                0,
                this::handleResult
        );
    }

    private void handleResult(ApiResult<RecommendationPageDto> result) {
        activeCall = null;
        RecommendationsState current = state.getValue();
        List<RecommendationDto> currentItems = current == null
                ? Collections.emptyList()
                : current.getRecommendations();
        RecommendationPageDto page = result.getData();
        if (result.isSuccess() && page != null) {
            List<RecommendationDto> items = page.getItems() == null
                    ? Collections.emptyList()
                    : page.getItems();
            state.postValue(new RecommendationsState(false, items, null));
            return;
        }

        state.postValue(new RecommendationsState(
                false,
                currentItems,
                errorMessage(result)
        ));
    }

    private String errorMessage(ApiResult<?> result) {
        return UserMessageResolver.resolve(
                getApplication(),
                result,
                R.string.error_recommendations_load
        );
    }

    @Override
    protected void onCleared() {
        if (activeCall != null && !activeCall.isCanceled()) {
            activeCall.cancel();
        }
        super.onCleared();
    }

    public static final class RecommendationsState {

        private final boolean loading;
        private final List<RecommendationDto> recommendations;
        private final String errorMessage;

        private RecommendationsState(
                boolean loading,
                List<RecommendationDto> recommendations,
                String errorMessage
        ) {
            this.loading = loading;
            this.recommendations = Collections.unmodifiableList(
                    new ArrayList<>(recommendations)
            );
            this.errorMessage = errorMessage;
        }

        public boolean isLoading() {
            return loading;
        }

        public List<RecommendationDto> getRecommendations() {
            return recommendations;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
