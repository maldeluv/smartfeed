package com.smartfeed.ui.saved;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smartfeed.R;
import com.smartfeed.data.api.ApiClient;
import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.local.SessionManager;
import com.smartfeed.data.model.ArticleDto;
import com.smartfeed.data.model.ArticlePageDto;
import com.smartfeed.data.model.StatusResponse;
import com.smartfeed.data.repository.ActionRepository;
import com.smartfeed.data.repository.ApiResult;
import com.smartfeed.data.repository.ArticleRepository;
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

public final class SavedViewModel extends AndroidViewModel {

    private static final int SAVED_LIMIT = 100;

    private final MutableLiveData<SavedState> state = new MutableLiveData<>(
            new SavedState(
                    false,
                    Collections.emptyList(),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    null
            )
    );
    private final MutableLiveData<OneTimeEvent<String>> notifications =
            new MutableLiveData<>();
    private final ArticleRepository articleRepository;
    private final ActionRepository actionRepository;
    private final LocalCacheRepository localCacheRepository;
    private final Map<Long, Call<StatusResponse>> likeCalls = new HashMap<>();
    private final Map<Long, Call<StatusResponse>> saveCalls = new HashMap<>();
    private Call<ArticlePageDto> loadCall;
    private boolean cleared;

    public SavedViewModel(@NonNull Application application) {
        super(application);
        SmartFeedApi api = ApiClient.create(new SessionManager(application));
        articleRepository = new ArticleRepository(api);
        actionRepository = new ActionRepository(application, api);
        localCacheRepository = new LocalCacheRepository(application);
        loadSaved();
    }

    public LiveData<SavedState> getState() {
        return state;
    }

    public LiveData<OneTimeEvent<String>> getNotifications() {
        return notifications;
    }

    public void refresh() {
        loadSaved();
    }

    public void clearError() {
        SavedState current = state.getValue();
        if (current != null && current.getErrorMessage() != null) {
            publishState(current.getArticles(), null);
        }
    }

    public void toggleLike(ArticleDto requestedArticle) {
        long articleId = requestedArticle.getId();
        if (likeCalls.containsKey(articleId)) {
            return;
        }
        ArticleDto article = findArticle(articleId);
        if (article == null) {
            return;
        }
        boolean previousValue = article.isLiked();
        boolean targetValue = !previousValue;
        replaceArticle(article.withLiked(targetValue), true, false);
        Call<StatusResponse> call = actionRepository.setArticleLiked(
                articleId,
                targetValue,
                result -> finishLike(articleId, previousValue, result)
        );
        likeCalls.put(articleId, call);
    }

    public void toggleSave(ArticleDto requestedArticle) {
        long articleId = requestedArticle.getId();
        if (saveCalls.containsKey(articleId)) {
            return;
        }
        SavedState current = state.getValue();
        ArticleDto article = findArticle(articleId);
        if (current == null || article == null) {
            return;
        }
        boolean previousValue = article.isSaved();
        boolean targetValue = !previousValue;
        int previousIndex = current.getArticles().indexOf(article);
        List<ArticleDto> optimisticArticles = new ArrayList<>(current.getArticles());
        if (targetValue) {
            optimisticArticles.set(previousIndex, article.withSaved(true));
        } else {
            optimisticArticles.remove(previousIndex);
        }
        publishStateWithPending(optimisticArticles, articleId, false, true);
        Call<StatusResponse> call = actionRepository.setArticleSaved(
                articleId,
                targetValue,
                result -> finishSave(
                        article,
                        previousIndex,
                        previousValue,
                        result
                )
        );
        saveCalls.put(articleId, call);
    }

    private void loadSaved() {
        if (loadCall != null && !loadCall.isCanceled()) {
            return;
        }
        SavedState current = state.getValue();
        List<ArticleDto> articles = current == null
                ? Collections.emptyList()
                : current.getArticles();
        state.setValue(new SavedState(
                true,
                articles,
                likeCalls.keySet(),
                saveCalls.keySet(),
                null
        ));
        if (!NetworkMonitor.isConnected(getApplication())) {
            loadCachedSaved(current);
            return;
        }
        loadCall = articleRepository.loadSavedArticles(
                SAVED_LIMIT,
                0,
                this::handleLoadResult
        );
    }

    private void handleLoadResult(ApiResult<ArticlePageDto> result) {
        loadCall = null;
        ArticlePageDto page = result.getData();
        if (result.isSuccess() && page != null) {
            List<ArticleDto> articles = page.getItems() == null
                    ? Collections.emptyList()
                    : page.getItems();
            localCacheRepository.cacheSavedArticles(articles);
            publishState(articles, null);
            return;
        }
        SavedState current = state.getValue();
        if (result.getType() == ApiResult.Type.NETWORK_ERROR) {
            loadCachedSaved(current);
            return;
        }
        publishState(
                current == null ? Collections.emptyList() : current.getArticles(),
                errorMessage(result, R.string.error_saved_load)
        );
    }

    private void loadCachedSaved(SavedState previousState) {
        localCacheRepository.loadSavedArticles(cachedArticles -> {
            if (cleared) {
                return;
            }
            List<ArticleDto> currentArticles = previousState == null
                    ? Collections.emptyList()
                    : previousState.getArticles();
            List<ArticleDto> articles = cachedArticles.isEmpty()
                    ? currentArticles
                    : cachedArticles;
            String message = articles.isEmpty()
                    ? getApplication().getString(R.string.error_network)
                    : getApplication().getString(R.string.offline_saved_message);
            publishState(articles, message);
        });
    }

    private void finishLike(
            long articleId,
            boolean previousValue,
            ApiResult<StatusResponse> result
    ) {
        likeCalls.remove(articleId);
        ArticleDto article = findArticle(articleId);
        if (!result.isSuccess() && article != null) {
            replaceArticle(article.withLiked(previousValue), false, false);
            notifyFailure(result, R.string.error_like_action);
        } else {
            if (article != null) {
                localCacheRepository.updateArticleLiked(articleId, article.isLiked());
            }
            republishPending();
        }
    }

    private void finishSave(
            ArticleDto originalArticle,
            int previousIndex,
            boolean previousValue,
            ApiResult<StatusResponse> result
    ) {
        saveCalls.remove(originalArticle.getId());
        SavedState current = state.getValue();
        if (current == null) {
            return;
        }
        if (!result.isSuccess()) {
            List<ArticleDto> articles = new ArrayList<>(current.getArticles());
            if (findArticle(originalArticle.getId()) == null) {
                int insertionIndex = Math.min(previousIndex, articles.size());
                articles.add(insertionIndex, originalArticle.withSaved(previousValue));
            }
            publishState(articles, current.getErrorMessage());
            notifyFailure(result, R.string.error_save_action);
        } else {
            localCacheRepository.updateArticleSaved(
                    originalArticle.withSaved(!previousValue)
            );
            republishPending();
        }
    }

    private ArticleDto findArticle(long articleId) {
        SavedState current = state.getValue();
        if (current == null) {
            return null;
        }
        for (ArticleDto article : current.getArticles()) {
            if (article.getId() == articleId) {
                return article;
            }
        }
        return null;
    }

    private void replaceArticle(
            ArticleDto updated,
            boolean addPendingLike,
            boolean addPendingSave
    ) {
        SavedState current = state.getValue();
        if (current == null) {
            return;
        }
        List<ArticleDto> articles = new ArrayList<>(current.getArticles());
        for (int index = 0; index < articles.size(); index++) {
            if (articles.get(index).getId() == updated.getId()) {
                articles.set(index, updated);
                break;
            }
        }
        publishStateWithPending(
                articles,
                updated.getId(),
                addPendingLike,
                addPendingSave
        );
    }

    private void publishStateWithPending(
            List<ArticleDto> articles,
            long articleId,
            boolean addPendingLike,
            boolean addPendingSave
    ) {
        SavedState current = state.getValue();
        Set<Long> pendingLikes = new HashSet<>(likeCalls.keySet());
        Set<Long> pendingSaves = new HashSet<>(saveCalls.keySet());
        if (addPendingLike) {
            pendingLikes.add(articleId);
        }
        if (addPendingSave) {
            pendingSaves.add(articleId);
        }
        state.setValue(new SavedState(
                current != null && current.isLoading(),
                articles,
                pendingLikes,
                pendingSaves,
                current == null ? null : current.getErrorMessage()
        ));
    }

    private void publishState(List<ArticleDto> articles, String errorMessage) {
        state.setValue(new SavedState(
                false,
                articles,
                likeCalls.keySet(),
                saveCalls.keySet(),
                errorMessage
        ));
    }

    private void republishPending() {
        SavedState current = state.getValue();
        if (current != null) {
            publishState(current.getArticles(), current.getErrorMessage());
        }
    }

    private void notifyFailure(ApiResult<?> result, int fallbackMessage) {
        notifications.setValue(new OneTimeEvent<>(errorMessage(result, fallbackMessage)));
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
        for (Call<StatusResponse> call : likeCalls.values()) {
            call.cancel();
        }
        for (Call<StatusResponse> call : saveCalls.values()) {
            call.cancel();
        }
        super.onCleared();
    }

    public static final class SavedState {

        private final boolean loading;
        private final List<ArticleDto> articles;
        private final Set<Long> pendingLikes;
        private final Set<Long> pendingSaves;
        private final String errorMessage;

        private SavedState(
                boolean loading,
                List<ArticleDto> articles,
                Set<Long> pendingLikes,
                Set<Long> pendingSaves,
                String errorMessage
        ) {
            this.loading = loading;
            this.articles = Collections.unmodifiableList(new ArrayList<>(articles));
            this.pendingLikes = Collections.unmodifiableSet(new HashSet<>(pendingLikes));
            this.pendingSaves = Collections.unmodifiableSet(new HashSet<>(pendingSaves));
            this.errorMessage = errorMessage;
        }

        public boolean isLoading() {
            return loading;
        }

        public List<ArticleDto> getArticles() {
            return articles;
        }

        public Set<Long> getPendingLikes() {
            return pendingLikes;
        }

        public Set<Long> getPendingSaves() {
            return pendingSaves;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
