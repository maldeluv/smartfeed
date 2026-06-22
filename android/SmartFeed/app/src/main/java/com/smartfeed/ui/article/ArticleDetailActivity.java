package com.smartfeed.ui.article;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.smartfeed.R;
import com.smartfeed.data.model.ArticleDto;
import com.smartfeed.util.DateFormatter;
import com.smartfeed.util.SystemBarInsets;

public final class ArticleDetailActivity extends AppCompatActivity {

    private static final String EXTRA_ARTICLE_ID = "article_id";
    private static final String EXTRA_FROM_RECOMMENDATIONS = "from_recommendations";

    public static Intent newIntent(Context context, ArticleDto article) {
        return newIntent(context, article.getId(), false);
    }

    public static Intent newIntentFromRecommendation(Context context, ArticleDto article) {
        return newIntent(context, article.getId(), true);
    }

    public static Intent newIntent(
            Context context,
            long articleId,
            boolean fromRecommendations
    ) {
        return new Intent(context, ArticleDetailActivity.class)
                .putExtra(EXTRA_ARTICLE_ID, articleId)
                .putExtra(EXTRA_FROM_RECOMMENDATIONS, fromRecommendations);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_detail);

        View rootView = findViewById(R.id.articleDetailRoot);
        SystemBarInsets.apply(rootView, true, true);
        MaterialToolbar toolbar = findViewById(R.id.articleDetailToolbar);
        View progressBar = findViewById(R.id.articleDetailProgress);
        View contentView = findViewById(R.id.articleDetailContent);
        View errorView = findViewById(R.id.articleDetailErrorState);
        TextView errorMessageView = findViewById(R.id.articleDetailErrorMessage);
        TextView categoryView = findViewById(R.id.articleDetailCategory);
        TextView titleView = findViewById(R.id.articleDetailTitle);
        TextView metaView = findViewById(R.id.articleDetailMeta);
        TextView contentTextView = findViewById(R.id.articleDetailText);
        MaterialButton likeButton = findViewById(R.id.articleDetailLikeButton);
        MaterialButton saveButton = findViewById(R.id.articleDetailSaveButton);
        MaterialButton retryButton = findViewById(R.id.articleDetailRetryButton);

        toolbar.setNavigationOnClickListener(view -> finish());

        ArticleDetailViewModel viewModel = new ViewModelProvider(this)
                .get(ArticleDetailViewModel.class);
        retryButton.setOnClickListener(view -> viewModel.retry());
        likeButton.setOnClickListener(view -> viewModel.toggleLike());
        saveButton.setOnClickListener(view -> viewModel.toggleSave());

        viewModel.getState().observe(this, state -> {
            if (state == null) {
                return;
            }

            ArticleDto article = state.getArticle();
            boolean hasArticle = article != null;
            progressBar.setVisibility(
                    state.isLoading() && !hasArticle ? View.VISIBLE : View.GONE
            );
            contentView.setVisibility(hasArticle ? View.VISIBLE : View.GONE);
            errorView.setVisibility(
                    !state.isLoading() && !hasArticle && state.getErrorMessage() != null
                            ? View.VISIBLE
                            : View.GONE
            );

            if (state.getErrorMessage() != null) {
                errorMessageView.setText(state.getErrorMessage());
            }
            if (article != null) {
                bindArticle(
                        article,
                        categoryView,
                        titleView,
                        metaView,
                        contentTextView
                );
                bindActions(
                        article,
                        state.isLikePending(),
                        state.isSavePending(),
                        likeButton,
                        saveButton
                );
            }
        });

        viewModel.getNotifications().observe(this, event -> {
            if (event == null) {
                return;
            }
            String message = event.getContentIfNotHandled();
            if (message != null) {
                Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show();
            }
        });

        Intent intent = getIntent();
        viewModel.loadArticle(
                intent.getLongExtra(EXTRA_ARTICLE_ID, -1L),
                intent.getBooleanExtra(EXTRA_FROM_RECOMMENDATIONS, false)
        );
    }

    private void bindArticle(
            ArticleDto article,
            TextView categoryView,
            TextView titleView,
            TextView metaView,
            TextView contentTextView
    ) {
        String category = article.getCategoryName();
        categoryView.setText(
                category == null || category.trim().isEmpty()
                        ? getString(R.string.category_unknown)
                        : category
        );
        titleView.setText(article.getTitle());
        metaView.setText(buildMeta(
                article.getAuthor(),
                DateFormatter.formatArticleDate(article.getPublishedAt())
        ));
        contentTextView.setText(article.getContent());
    }

    private String buildMeta(String author, String date) {
        boolean hasAuthor = author != null && !author.trim().isEmpty();
        boolean hasDate = date != null && !date.trim().isEmpty();
        if (hasAuthor && hasDate) {
            return getString(R.string.article_meta, author, date);
        }
        if (hasAuthor) {
            return author;
        }
        return hasDate ? date : getString(R.string.article_meta_unknown);
    }

    private void bindActions(
            ArticleDto article,
            boolean likePending,
            boolean savePending,
            MaterialButton likeButton,
            MaterialButton saveButton
    ) {
        likeButton.setEnabled(!likePending);
        saveButton.setEnabled(!savePending);
        likeButton.setSelected(article.isLiked());
        saveButton.setSelected(article.isSaved());
        likeButton.setText(article.isLiked() ? R.string.action_unlike : R.string.action_like);
        saveButton.setText(article.isSaved() ? R.string.action_unsave : R.string.action_save);
        int likeColor = getColor(
                article.isLiked()
                        ? R.color.smartfeed_primary
                        : R.color.smartfeed_on_surface_variant
        );
        int saveColor = getColor(
                article.isSaved()
                        ? R.color.smartfeed_primary
                        : R.color.smartfeed_on_surface_variant
        );
        likeButton.setIconTint(android.content.res.ColorStateList.valueOf(likeColor));
        saveButton.setIconTint(android.content.res.ColorStateList.valueOf(saveColor));
    }
}
