package com.smartfeed.ui.article;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smartfeed.R;
import com.smartfeed.data.api.ApiClient;
import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.local.SessionManager;
import com.smartfeed.data.model.ArticleDto;
import com.smartfeed.data.model.StatusResponse;
import com.smartfeed.data.repository.ActionRepository;
import com.smartfeed.data.repository.ApiResult;
import com.smartfeed.data.repository.ArticleRepository;
import com.smartfeed.data.repository.EventRepository;
import com.smartfeed.data.repository.LocalCacheRepository;
import com.smartfeed.util.EventFactory;
import com.smartfeed.util.OneTimeEvent;
import com.smartfeed.util.UserMessageResolver;

import retrofit2.Call;

public final class ArticleDetailViewModel extends AndroidViewModel {

    private static final String TAG = "ArticleDetail";

    private final MutableLiveData<ArticleDetailState> state = new MutableLiveData<>(
            new ArticleDetailState(false, null, false, false, null)
    );
    private final MutableLiveData<OneTimeEvent<String>> notifications =
            new MutableLiveData<>();
    private final ArticleRepository articleRepository;
    private final EventRepository eventRepository;
    private final ActionRepository actionRepository;
    private final LocalCacheRepository localCacheRepository;
    private final SessionManager sessionManager;

    private Call<ArticleDto> articleCall;
    private Call<StatusResponse> likeCall;
    private Call<StatusResponse> saveCall;
    private long articleId = -1L;
    private boolean openedFromRecommendations;
    private boolean openEventSent;

    public ArticleDetailViewModel(@NonNull Application application) {
        super(application);
        sessionManager = new SessionManager(application);
        SmartFeedApi api = ApiClient.create(sessionManager);
        articleRepository = new ArticleRepository(api);
        eventRepository = new EventRepository(application);
        actionRepository = new ActionRepository(application, api);
        localCacheRepository = new LocalCacheRepository(application);
    }

    public LiveData<ArticleDetailState> getState() {
        return state;
    }

    public LiveData<OneTimeEvent<String>> getNotifications() {
        return notifications;
    }

    public void loadArticle(long requestedArticleId, boolean fromRecommendations) {
        if (requestedArticleId <= 0) {
            state.setValue(new ArticleDetailState(
                    false,
                    null,
                    false,
                    false,
                    getApplication().getString(R.string.error_invalid_article)
            ));
            return;
        }

        ArticleDetailState current = state.getValue();
        if (articleId == requestedArticleId
                && current != null
                && (current.isLoading() || current.getArticle() != null)) {
            return;
        }

        articleId = requestedArticleId;
        openedFromRecommendations = fromRecommendations;
        openEventSent = false;
        requestArticle();
    }

    public void retry() {
        if (articleId > 0) {
            requestArticle();
        }
    }

    public void toggleLike() {
        ArticleDetailState current = state.getValue();
        if (current == null || current.getArticle() == null || likeCall != null) {
            return;
        }
        ArticleDto article = current.getArticle();
        boolean previousValue = article.isLiked();
        boolean targetValue = !previousValue;
        state.setValue(new ArticleDetailState(
                false,
                article.withLiked(targetValue),
                true,
                current.isSavePending(),
                null
        ));
        likeCall = actionRepository.setArticleLiked(
                article.getId(),
                targetValue,
                result -> finishLike(previousValue, result)
        );
    }

    public void toggleSave() {
        ArticleDetailState current = state.getValue();
        if (current == null || current.getArticle() == null || saveCall != null) {
            return;
        }
        ArticleDto article = current.getArticle();
        boolean previousValue = article.isSaved();
        boolean targetValue = !previousValue;
        state.setValue(new ArticleDetailState(
                false,
                article.withSaved(targetValue),
                current.isLikePending(),
                true,
                null
        ));
        saveCall = actionRepository.setArticleSaved(
                article.getId(),
                targetValue,
                result -> finishSave(previousValue, result)
        );
    }

    private void requestArticle() {
        if (articleCall != null && !articleCall.isCanceled()) {
            return;
        }

        state.setValue(new ArticleDetailState(true, null, false, false, null));
        articleCall = articleRepository.loadArticle(articleId, this::handleArticleResult);
    }

    private void handleArticleResult(ApiResult<ArticleDto> result) {
        articleCall = null;
        ArticleDto article = result.getData();
        if (result.isSuccess() && article != null) {
            localCacheRepository.cacheArticleState(article);
            state.postValue(new ArticleDetailState(
                    false,
                    article,
                    false,
                    false,
                    null
            ));
            sendOpenEvent(article);
            return;
        }

        String message = UserMessageResolver.resolve(
                getApplication(),
                result,
                R.string.error_article_load
        );
        state.postValue(new ArticleDetailState(
                false,
                null,
                false,
                false,
                message
        ));
    }

    private void finishLike(boolean previousValue, ApiResult<StatusResponse> result) {
        likeCall = null;
        ArticleDetailState current = state.getValue();
        if (current == null || current.getArticle() == null) {
            return;
        }
        ArticleDto article = result.isSuccess()
                ? current.getArticle()
                : current.getArticle().withLiked(previousValue);
        if (result.isSuccess()) {
            localCacheRepository.updateArticleLiked(article.getId(), article.isLiked());
        }
        state.setValue(new ArticleDetailState(
                false,
                article,
                false,
                current.isSavePending(),
                null
        ));
        if (!result.isSuccess()) {
            notifyActionFailure(result, R.string.error_like_action);
        }
    }

    private void finishSave(boolean previousValue, ApiResult<StatusResponse> result) {
        saveCall = null;
        ArticleDetailState current = state.getValue();
        if (current == null || current.getArticle() == null) {
            return;
        }
        ArticleDto article = result.isSuccess()
                ? current.getArticle()
                : current.getArticle().withSaved(previousValue);
        if (result.isSuccess()) {
            localCacheRepository.updateArticleSaved(article);
        }
        state.setValue(new ArticleDetailState(
                false,
                article,
                current.isLikePending(),
                false,
                null
        ));
        if (!result.isSuccess()) {
            notifyActionFailure(result, R.string.error_save_action);
        }
    }

    private void notifyActionFailure(ApiResult<?> result, int fallbackMessage) {
        notifications.setValue(new OneTimeEvent<>(UserMessageResolver.resolve(
                getApplication(),
                result,
                fallbackMessage
        )));
    }

    private void sendOpenEvent(ArticleDto article) {
        if (openEventSent) {
            return;
        }
        openEventSent = true;

        eventRepository.enqueueEvent(
                EventFactory.articleOpened(
                        sessionManager,
                        article,
                        openedFromRecommendations
                ),
                this::handleEventEnqueueResult
        );
    }

    private void handleEventEnqueueResult(ApiResult<Void> result) {
        if (result.isSuccess()) {
            return;
        }

        String message = result.getMessage();
        if (result.getCause() == null) {
            Log.w(TAG, "Failed to queue article open event: " + message);
        } else {
            Log.w(TAG, "Failed to queue article open event", result.getCause());
        }
        notifications.postValue(new OneTimeEvent<>(
                getApplication().getString(R.string.event_send_failed)
        ));
    }

    @Override
    protected void onCleared() {
        if (articleCall != null && !articleCall.isCanceled()) {
            articleCall.cancel();
        }
        if (likeCall != null && !likeCall.isCanceled()) {
            likeCall.cancel();
        }
        if (saveCall != null && !saveCall.isCanceled()) {
            saveCall.cancel();
        }
        super.onCleared();
    }

    public static final class ArticleDetailState {

        private final boolean loading;
        private final ArticleDto article;
        private final boolean likePending;
        private final boolean savePending;
        private final String errorMessage;

        private ArticleDetailState(
                boolean loading,
                ArticleDto article,
                boolean likePending,
                boolean savePending,
                String errorMessage
        ) {
            this.loading = loading;
            this.article = article;
            this.likePending = likePending;
            this.savePending = savePending;
            this.errorMessage = errorMessage;
        }

        public boolean isLoading() {
            return loading;
        }

        public ArticleDto getArticle() {
            return article;
        }

        public boolean isLikePending() {
            return likePending;
        }

        public boolean isSavePending() {
            return savePending;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
