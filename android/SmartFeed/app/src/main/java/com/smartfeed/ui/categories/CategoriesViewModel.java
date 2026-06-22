package com.smartfeed.ui.categories;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smartfeed.R;
import com.smartfeed.data.api.ApiClient;
import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.local.SessionManager;
import com.smartfeed.data.model.CategoryDto;
import com.smartfeed.data.model.StatusResponse;
import com.smartfeed.data.repository.ActionRepository;
import com.smartfeed.data.repository.ApiResult;
import com.smartfeed.data.repository.CategoryRepository;
import com.smartfeed.data.repository.LocalCacheRepository;
import com.smartfeed.util.OneTimeEvent;
import com.smartfeed.util.NetworkMonitor;
import com.smartfeed.util.UserMessageResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;

public final class CategoriesViewModel extends AndroidViewModel {

    private final MutableLiveData<CategoriesState> state = new MutableLiveData<>(
            new CategoriesState(
                    false,
                    Collections.emptyList(),
                    Collections.emptySet(),
                    null
            )
    );
    private final MutableLiveData<OneTimeEvent<String>> notifications =
            new MutableLiveData<>();
    private final CategoryRepository categoryRepository;
    private final ActionRepository actionRepository;
    private final LocalCacheRepository localCacheRepository;
    private final Map<Long, Call<StatusResponse>> actionCalls = new HashMap<>();
    private Call<List<CategoryDto>> loadCall;
    private boolean cleared;

    public CategoriesViewModel(@NonNull Application application) {
        super(application);
        SmartFeedApi api = ApiClient.create(new SessionManager(application));
        categoryRepository = new CategoryRepository(api);
        actionRepository = new ActionRepository(application, api);
        localCacheRepository = new LocalCacheRepository(application);
        loadCategories();
    }

    public LiveData<CategoriesState> getState() {
        return state;
    }

    public LiveData<OneTimeEvent<String>> getNotifications() {
        return notifications;
    }

    public void refresh() {
        loadCategories();
    }

    public void clearError() {
        CategoriesState current = state.getValue();
        if (current != null && current.getErrorMessage() != null) {
            state.setValue(new CategoriesState(
                    current.isLoading(),
                    current.getCategories(),
                    current.getPendingIds(),
                    null
            ));
        }
    }

    public void toggleSubscription(CategoryDto requestedCategory) {
        long categoryId = requestedCategory.getId();
        if (actionCalls.containsKey(categoryId)) {
            return;
        }
        CategoryDto category = findCategory(categoryId);
        if (category == null) {
            return;
        }
        boolean previousValue = category.isSubscribed();
        boolean targetValue = !previousValue;
        updateCategory(category.withSubscribed(targetValue), true);
        Call<StatusResponse> call = actionRepository.setCategorySubscribed(
                categoryId,
                targetValue,
                result -> finishAction(categoryId, previousValue, result)
        );
        actionCalls.put(categoryId, call);
    }

    private void loadCategories() {
        if (loadCall != null && !loadCall.isCanceled()) {
            return;
        }
        CategoriesState current = state.getValue();
        List<CategoryDto> categories = current == null
                ? Collections.emptyList()
                : current.getCategories();
        state.setValue(new CategoriesState(
                true,
                categories,
                actionCalls.keySet(),
                null
        ));
        if (!NetworkMonitor.isConnected(getApplication())) {
            loadCachedCategories(categories);
            return;
        }
        loadCall = categoryRepository.loadCategories(this::handleLoadResult);
    }

    private void handleLoadResult(ApiResult<List<CategoryDto>> result) {
        loadCall = null;
        CategoriesState current = state.getValue();
        List<CategoryDto> categories = current == null
                ? Collections.emptyList()
                : current.getCategories();
        if (result.isSuccess() && result.getData() != null) {
            localCacheRepository.cacheCategories(result.getData());
            state.postValue(new CategoriesState(
                    false,
                    result.getData(),
                    actionCalls.keySet(),
                    null
            ));
            return;
        }
        if (result.getType() == ApiResult.Type.NETWORK_ERROR) {
            loadCachedCategories(categories);
            return;
        }
        state.postValue(new CategoriesState(
                false,
                categories,
                actionCalls.keySet(),
                errorMessage(result, R.string.error_categories_load)
        ));
    }

    private void loadCachedCategories(List<CategoryDto> currentCategories) {
        localCacheRepository.loadCategories(cachedCategories -> {
            if (cleared) {
                return;
            }
            List<CategoryDto> categories = cachedCategories.isEmpty()
                    ? currentCategories
                    : cachedCategories;
            String message = categories.isEmpty()
                    ? getApplication().getString(R.string.error_network)
                    : getApplication().getString(R.string.offline_categories_message);
            state.setValue(new CategoriesState(
                    false,
                    categories,
                    actionCalls.keySet(),
                    message
            ));
        });
    }

    private void finishAction(
            long categoryId,
            boolean previousValue,
            ApiResult<StatusResponse> result
    ) {
        actionCalls.remove(categoryId);
        CategoryDto category = findCategory(categoryId);
        if (!result.isSuccess() && category != null) {
            updateCategory(category.withSubscribed(previousValue), false);
            notifications.setValue(new OneTimeEvent<>(
                    errorMessage(result, R.string.error_subscription_action)
            ));
        } else {
            if (category != null) {
                localCacheRepository.updateCategorySubscribed(
                        categoryId,
                        category.isSubscribed()
                );
            }
            publishPendingState();
        }
    }

    private CategoryDto findCategory(long categoryId) {
        CategoriesState current = state.getValue();
        if (current == null) {
            return null;
        }
        for (CategoryDto category : current.getCategories()) {
            if (category.getId() == categoryId) {
                return category;
            }
        }
        return null;
    }

    private void updateCategory(CategoryDto updated, boolean addPending) {
        CategoriesState current = state.getValue();
        if (current == null) {
            return;
        }
        List<CategoryDto> categories = new ArrayList<>(current.getCategories());
        for (int index = 0; index < categories.size(); index++) {
            if (categories.get(index).getId() == updated.getId()) {
                categories.set(index, updated);
                break;
            }
        }
        Set<Long> pendingIds = new HashSet<>(actionCalls.keySet());
        if (addPending) {
            pendingIds.add(updated.getId());
        }
        state.setValue(new CategoriesState(
                current.isLoading(),
                categories,
                pendingIds,
                current.getErrorMessage()
        ));
    }

    private void publishPendingState() {
        CategoriesState current = state.getValue();
        if (current != null) {
            state.setValue(new CategoriesState(
                    current.isLoading(),
                    current.getCategories(),
                    actionCalls.keySet(),
                    current.getErrorMessage()
            ));
        }
    }

    private String errorMessage(ApiResult<?> result, int fallbackMessage) {
        return UserMessageResolver.resolve(
                getApplication(),
                result,
                fallbackMessage
        );
    }

    @Override
    protected void onCleared() {
        cleared = true;
        if (loadCall != null) {
            loadCall.cancel();
        }
        for (Call<StatusResponse> call : actionCalls.values()) {
            call.cancel();
        }
        super.onCleared();
    }

    public static final class CategoriesState {

        private final boolean loading;
        private final List<CategoryDto> categories;
        private final Set<Long> pendingIds;
        private final String errorMessage;

        private CategoriesState(
                boolean loading,
                List<CategoryDto> categories,
                Set<Long> pendingIds,
                String errorMessage
        ) {
            this.loading = loading;
            this.categories = Collections.unmodifiableList(new ArrayList<>(categories));
            this.pendingIds = Collections.unmodifiableSet(new HashSet<>(pendingIds));
            this.errorMessage = errorMessage;
        }

        public boolean isLoading() {
            return loading;
        }

        public List<CategoryDto> getCategories() {
            return categories;
        }

        public Set<Long> getPendingIds() {
            return pendingIds;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
