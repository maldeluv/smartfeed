package com.smartfeed.ui.feed;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smartfeed.R;
import com.smartfeed.data.api.ApiClient;
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

public final class FeedViewModel extends AndroidViewModel {

    private static final int FEED_LIMIT = 50;
    private final MutableLiveData<FeedState> state = new MutableLiveData<>(
            new FeedState(
                    false,
                    Collections.emptyList(),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    null
            )
    );
    private final MutableLiveData<OneTimeEvent<String>> notifications =
            new MutableLiveData<>();
    private final ArticleRepository repository;
    private final ActionRepository actionRepository;
    private final LocalCacheRepository localCacheRepository;
    private final Map<Long, Call<StatusResponse>> likeCalls = new HashMap<>();
    private final Map<Long, Call<StatusResponse>> saveCalls = new HashMap<>();
    private Call<ArticlePageDto> activeCall;
    private boolean cleared;

    public FeedViewModel(@NonNull Application application) {
        super(application);
        SessionManager sessionManager = new SessionManager(application);
        com.smartfeed.data.api.SmartFeedApi api = ApiClient.create(sessionManager);
        repository = new ArticleRepository(api);
        actionRepository = new ActionRepository(application, api);
        localCacheRepository = new LocalCacheRepository(application);
        loadArticles();
    }

    public LiveData<FeedState> getState() {
        return state;
    }

    public LiveData<OneTimeEvent<String>> getNotifications() {
        return notifications;
    }

    public void refresh() {
        loadArticles();
    }

    public void clearError() {
        FeedState current = state.getValue();
        if (current != null && current.getErrorMessage() != null) {
            state.setValue(new FeedState(
                    current.isLoading(),
                    current.getArticles(),
                    current.getPendingLikes(),
                    current.getPendingSaves(),
                    null
            ));
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
        updateArticle(article.withLiked(targetValue), true, false);
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
        ArticleDto article = findArticle(articleId);
        if (article == null) {
            return;
        }

        boolean previousValue = article.isSaved();
        boolean targetValue = !previousValue;
        updateArticle(article.withSaved(targetValue), false, true);
        Call<StatusResponse> call = actionRepository.setArticleSaved(
                articleId,
                targetValue,
                result -> finishSave(articleId, previousValue, result)
        );
        saveCalls.put(articleId, call);
    }

    private void loadArticles() {
        if (activeCall != null && !activeCall.isCanceled()) {
            return;
        }

        FeedState current = state.getValue();
        List<ArticleDto> currentArticles = current == null
                ? Collections.emptyList()
                : current.getArticles();
        Set<Long> pendingLikes = current == null
                ? Collections.emptySet()
                : current.getPendingLikes();
        Set<Long> pendingSaves = current == null
                ? Collections.emptySet()
                : current.getPendingSaves();
        state.setValue(new FeedState(
                true,
                currentArticles,
                pendingLikes,
                pendingSaves,
                null
        ));

        if (!NetworkMonitor.isConnected(getApplication())) {
            loadCachedFeed(currentArticles, current);
            return;
        }

        activeCall = repository.loadArticles(FEED_LIMIT, 0, this::handleResult);
    }

    private void handleResult(ApiResult<ArticlePageDto> result) {
        activeCall = null;
        FeedState current = state.getValue();
        List<ArticleDto> currentArticles = current == null
                ? Collections.emptyList()
                : current.getArticles();

        ArticlePageDto page = result.getData();
        if (result.isSuccess() && page != null) {
            List<ArticleDto> articles = page.getItems() == null
                    ? Collections.emptyList()
                    : page.getItems();
            localCacheRepository.cacheFeed(articles);
            state.postValue(new FeedState(
                    false,
                    articles,
                    current == null ? Collections.emptySet() : current.getPendingLikes(),
                    current == null ? Collections.emptySet() : current.getPendingSaves(),
                    null
            ));
            return;
        }

        if (result.getType() == ApiResult.Type.NETWORK_ERROR) {
            loadCachedFeed(currentArticles, current);
            return;
        }

        String message = UserMessageResolver.resolve(
                getApplication(),
                result,
                R.string.error_articles_load
        );
        state.postValue(new FeedState(
                false,
                currentArticles,
                current == null ? Collections.emptySet() : current.getPendingLikes(),
                current == null ? Collections.emptySet() : current.getPendingSaves(),
                message
        ));
    }

    private void loadCachedFeed(
            List<ArticleDto> currentArticles,
            FeedState previousState
    ) {
        localCacheRepository.loadFeed(cachedArticles -> {
            if (cleared) {
                return;
            }
            List<ArticleDto> articles = cachedArticles.isEmpty()
                    ? currentArticles
                    : cachedArticles;
            String message = articles.isEmpty()
                    ? getApplication().getString(R.string.error_network)
                    : getApplication().getString(R.string.offline_feed_message);
            state.setValue(new FeedState(
                    false,
                    articles,
                    previousState == null
                            ? Collections.emptySet()
                            : previousState.getPendingLikes(),
                    previousState == null
                            ? Collections.emptySet()
                            : previousState.getPendingSaves(),
                    message
            ));
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
            updateArticle(article.withLiked(previousValue), false, false);
            notifyActionFailure(result, R.string.error_like_action);
        } else {
            if (article != null) {
                localCacheRepository.updateArticleLiked(articleId, article.isLiked());
            }
            publishPendingState();
        }
    }

    private void finishSave(
            long articleId,
            boolean previousValue,
            ApiResult<StatusResponse> result
    ) {
        saveCalls.remove(articleId);
        ArticleDto article = findArticle(articleId);
        if (!result.isSuccess() && article != null) {
            updateArticle(article.withSaved(previousValue), false, false);
            notifyActionFailure(result, R.string.error_save_action);
        } else {
            if (article != null) {
                localCacheRepository.updateArticleSaved(article);
            }
            publishPendingState();
        }
    }

    private ArticleDto findArticle(long articleId) {
        FeedState current = state.getValue();
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

    private void updateArticle(
            ArticleDto updatedArticle,
            boolean addPendingLike,
            boolean addPendingSave
    ) {
        FeedState current = state.getValue();
        if (current == null) {
            return;
        }
        List<ArticleDto> articles = new ArrayList<>(current.getArticles());
        for (int index = 0; index < articles.size(); index++) {
            if (articles.get(index).getId() == updatedArticle.getId()) {
                articles.set(index, updatedArticle);
                break;
            }
        }
        Set<Long> pendingLikes = new HashSet<>(likeCalls.keySet());
        Set<Long> pendingSaves = new HashSet<>(saveCalls.keySet());
        if (addPendingLike) {
            pendingLikes.add(updatedArticle.getId());
        }
        if (addPendingSave) {
            pendingSaves.add(updatedArticle.getId());
        }
        state.setValue(new FeedState(
                current.isLoading(),
                articles,
                pendingLikes,
                pendingSaves,
                current.getErrorMessage()
        ));
    }

    private void publishPendingState() {
        FeedState current = state.getValue();
        if (current == null) {
            return;
        }
        state.setValue(new FeedState(
                current.isLoading(),
                current.getArticles(),
                likeCalls.keySet(),
                saveCalls.keySet(),
                current.getErrorMessage()
        ));
    }

    private void notifyActionFailure(ApiResult<?> result, int fallbackMessage) {
        notifications.setValue(new OneTimeEvent<>(UserMessageResolver.resolve(
                getApplication(),
                result,
                fallbackMessage
        )));
    }

    @Override
    protected void onCleared() {
        cleared = true;
        if (activeCall != null && !activeCall.isCanceled()) {
            activeCall.cancel();
        }
        for (Call<StatusResponse> call : likeCalls.values()) {
            call.cancel();
        }
        for (Call<StatusResponse> call : saveCalls.values()) {
            call.cancel();
        }
        super.onCleared();
    }

    public static final class FeedState {

        private final boolean loading;
        private final List<ArticleDto> articles;
        private final Set<Long> pendingLikes;
        private final Set<Long> pendingSaves;
        private final String errorMessage;

        private FeedState(
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
