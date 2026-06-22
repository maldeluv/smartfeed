package com.smartfeed.ui.analytics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.smartfeed.R;
import com.smartfeed.data.model.UserAnalyticsDto;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class AnalyticsFragment extends Fragment {

    private AnalyticsViewModel viewModel;

    public AnalyticsFragment() {
        super(R.layout.fragment_analytics);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        SwipeRefreshLayout swipeRefresh = view.findViewById(R.id.analyticsSwipeRefresh);
        View content = view.findViewById(R.id.analyticsContent);
        View progress = view.findViewById(R.id.analyticsProgress);
        View errorState = view.findViewById(R.id.analyticsErrorState);
        TextView errorMessage = view.findViewById(R.id.analyticsErrorMessage);
        MaterialButton retryButton = view.findViewById(R.id.analyticsRetryButton);
        TextView viewsCount = view.findViewById(R.id.analyticsViewsCount);
        TextView likesCount = view.findViewById(R.id.analyticsLikesCount);
        TextView savesCount = view.findViewById(R.id.analyticsSavesCount);
        TextView recommendationsOpened = view.findViewById(
                R.id.analyticsRecommendationsOpened
        );
        TextView ctrValue = view.findViewById(R.id.analyticsCtrValue);
        TextView ctrDetails = view.findViewById(R.id.analyticsCtrDetails);
        LinearProgressIndicator ctrProgress = view.findViewById(
                R.id.analyticsCtrProgress
        );
        LinearLayout categoriesContainer = view.findViewById(
                R.id.analyticsCategoriesContainer
        );
        TextView categoriesEmpty = view.findViewById(R.id.analyticsCategoriesEmpty);

        viewModel = new ViewModelProvider(this)
                .get(AnalyticsViewModel.class);
        swipeRefresh.setColorSchemeResources(
                R.color.smartfeed_primary,
                R.color.smartfeed_secondary
        );
        swipeRefresh.setOnRefreshListener(viewModel::refresh);
        retryButton.setOnClickListener(ignored -> viewModel.refresh());

        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            UserAnalyticsDto analytics = state.getAnalytics();
            boolean hasAnalytics = analytics != null;
            content.setVisibility(hasAnalytics ? View.VISIBLE : View.GONE);
            progress.setVisibility(
                    state.isLoading() && !hasAnalytics ? View.VISIBLE : View.GONE
            );
            swipeRefresh.setRefreshing(state.isLoading() && hasAnalytics);
            errorState.setVisibility(
                    !state.isLoading() && !hasAnalytics && state.getErrorMessage() != null
                            ? View.VISIBLE
                            : View.GONE
            );

            if (analytics != null) {
                bindAnalytics(
                        analytics,
                        viewsCount,
                        likesCount,
                        savesCount,
                        recommendationsOpened,
                        ctrValue,
                        ctrDetails,
                        ctrProgress,
                        categoriesContainer,
                        categoriesEmpty
                );
            }
            if (state.getErrorMessage() != null) {
                errorMessage.setText(state.getErrorMessage());
                if (hasAnalytics) {
                    Snackbar.make(view, state.getErrorMessage(), Snackbar.LENGTH_LONG).show();
                    viewModel.clearError();
                }
            }
        });
    }

    private void bindAnalytics(
            UserAnalyticsDto analytics,
            TextView viewsCount,
            TextView likesCount,
            TextView savesCount,
            TextView recommendationsOpened,
            TextView ctrValue,
            TextView ctrDetails,
            LinearProgressIndicator ctrProgress,
            LinearLayout categoriesContainer,
            TextView categoriesEmpty
    ) {
        viewsCount.setText(String.valueOf(analytics.getViewsCount()));
        likesCount.setText(String.valueOf(analytics.getLikesCount()));
        savesCount.setText(String.valueOf(analytics.getSavesCount()));
        recommendationsOpened.setText(String.valueOf(
                analytics.getRecommendationsOpened()
        ));
        ctrValue.setText(AnalyticsTextFormatter.formatCtr(
                analytics.getRecommendationCtr(),
                Locale.getDefault()
        ));
        ctrDetails.setText(getString(
                R.string.analytics_ctr_details,
                analytics.getRecommendedArticlesOpened(),
                analytics.getRecommendationsOpened()
        ));
        ctrProgress.setProgressCompat(
                AnalyticsTextFormatter.ctrProgress(analytics.getRecommendationCtr()),
                true
        );
        bindCategories(
                analytics.getFavoriteCategories(),
                categoriesContainer,
                categoriesEmpty
        );
    }

    private void bindCategories(
            List<UserAnalyticsDto.FavoriteCategoryDto> categories,
            LinearLayout container,
            TextView emptyView
    ) {
        List<UserAnalyticsDto.FavoriteCategoryDto> safeCategories = categories == null
                ? Collections.emptyList()
                : categories;
        container.removeAllViews();
        emptyView.setVisibility(safeCategories.isEmpty() ? View.VISIBLE : View.GONE);
        if (safeCategories.isEmpty()) {
            return;
        }

        double maxScore = 0.0;
        for (UserAnalyticsDto.FavoriteCategoryDto category : safeCategories) {
            maxScore = Math.max(maxScore, category.getScore());
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (UserAnalyticsDto.FavoriteCategoryDto category : safeCategories) {
            View item = inflater.inflate(
                    R.layout.item_analytics_category,
                    container,
                    false
            );
            TextView name = item.findViewById(R.id.analyticsCategoryName);
            TextView activity = item.findViewById(R.id.analyticsCategoryActivity);
            LinearProgressIndicator score = item.findViewById(
                    R.id.analyticsCategoryProgress
            );
            String categoryName = category.getCategoryName();
            name.setText(
                    categoryName == null || categoryName.trim().isEmpty()
                            ? getString(
                                    R.string.analytics_category_unknown,
                                    category.getCategoryId()
                            )
                            : categoryName
            );
            activity.setText(getString(
                    R.string.analytics_category_activity,
                    category.getEventsCount()
            ));
            int progress = maxScore <= 0.0
                    ? 0
                    : (int) Math.round(category.getScore() / maxScore * 100.0);
            score.setProgressCompat(Math.max(0, Math.min(100, progress)), false);
            container.addView(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.refresh();
        }
    }
}
