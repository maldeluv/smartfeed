package com.smartfeed.ui.profile;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smartfeed.R;
import com.smartfeed.data.api.ApiClient;
import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.local.SessionManager;
import com.smartfeed.data.model.UserDto;
import com.smartfeed.data.repository.ApiResult;
import com.smartfeed.data.repository.EventRepository;
import com.smartfeed.data.repository.UserRepository;
import com.smartfeed.util.NetworkMonitor;
import com.smartfeed.util.UserMessageResolver;

import retrofit2.Call;

public final class ProfileViewModel extends AndroidViewModel {

    private final EventRepository eventRepository;
    private final LiveData<Integer> pendingEventCount;
    private final MutableLiveData<ProfileState> state = new MutableLiveData<>(
            new ProfileState(false, null, null)
    );
    private final SessionManager sessionManager;
    private final UserRepository userRepository;
    private Call<UserDto> userCall;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        sessionManager = new SessionManager(application);
        SmartFeedApi api = ApiClient.create(sessionManager);
        userRepository = new UserRepository(api);
        eventRepository = new EventRepository(application);
        pendingEventCount = eventRepository.observePendingCount();
        loadUser();
    }

    public LiveData<ProfileState> getState() {
        return state;
    }

    public LiveData<Integer> getPendingEventCount() {
        return pendingEventCount;
    }

    public void syncNow() {
        eventRepository.requestSync();
    }

    public void refreshUser() {
        loadUser();
    }

    public void logout() {
        sessionManager.clearSession();
    }

    private void loadUser() {
        if (userCall != null && !userCall.isCanceled()) {
            return;
        }

        ProfileState current = state.getValue();
        UserDto user = current == null ? null : current.getUser();
        state.setValue(new ProfileState(true, user, null));
        if (!NetworkMonitor.isConnected(getApplication())) {
            state.setValue(new ProfileState(
                    false,
                    user,
                    getApplication().getString(R.string.error_network)
            ));
            return;
        }

        userCall = userRepository.loadCurrentUser(this::handleUserResult);
    }

    private void handleUserResult(ApiResult<UserDto> result) {
        userCall = null;
        ProfileState current = state.getValue();
        UserDto currentUser = current == null ? null : current.getUser();
        if (result.isSuccess() && result.getData() != null) {
            state.postValue(new ProfileState(false, result.getData(), null));
            return;
        }
        state.postValue(new ProfileState(
                false,
                currentUser,
                errorMessage(result)
        ));
    }

    private String errorMessage(ApiResult<?> result) {
        return UserMessageResolver.resolve(
                getApplication(),
                result,
                R.string.error_profile_load
        );
    }

    @Override
    protected void onCleared() {
        if (userCall != null && !userCall.isCanceled()) {
            userCall.cancel();
        }
        super.onCleared();
    }

    public static final class ProfileState {

        private final boolean loading;
        private final UserDto user;
        private final String errorMessage;

        private ProfileState(boolean loading, UserDto user, String errorMessage) {
            this.loading = loading;
            this.user = user;
            this.errorMessage = errorMessage;
        }

        public boolean isLoading() {
            return loading;
        }

        public UserDto getUser() {
            return user;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
