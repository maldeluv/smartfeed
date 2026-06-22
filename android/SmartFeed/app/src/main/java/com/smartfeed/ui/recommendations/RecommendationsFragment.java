package com.smartfeed.ui.recommendations;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.smartfeed.R;
import com.smartfeed.data.model.ArticleDto;
import com.smartfeed.data.model.RecommendationDto;
import com.smartfeed.ui.article.ArticleDetailActivity;

import java.util.Collections;

public final class RecommendationsFragment extends Fragment {

    private RecommendationsViewModel viewModel;

    public RecommendationsFragment() {
        super(R.layout.fragment_recommendations);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        SwipeRefreshLayout swipeRefresh = view.findViewById(
                R.id.recommendationsSwipeRefresh
        );
        RecyclerView recyclerView = view.findViewById(R.id.recommendationsRecyclerView);
        View progressBar = view.findViewById(R.id.recommendationsProgress);
        View emptyState = view.findViewById(R.id.recommendationsEmptyState);
        View errorState = view.findViewById(R.id.recommendationsErrorState);
        TextView errorMessage = view.findViewById(R.id.recommendationsErrorMessage);
        MaterialButton retryButton = view.findViewById(R.id.recommendationsRetryButton);

        RecommendationAdapter adapter = new RecommendationAdapter(recommendation -> {
            ArticleDto article = recommendation.getArticle();
            if (article != null) {
                startActivity(ArticleDetailActivity.newIntentFromRecommendation(
                        requireContext(),
                        article
                ));
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(RecommendationsViewModel.class);
        swipeRefresh.setColorSchemeResources(
                R.color.smartfeed_primary,
                R.color.smartfeed_secondary
        );
        swipeRefresh.setOnRefreshListener(viewModel::refresh);
        retryButton.setOnClickListener(ignored -> viewModel.refresh());

        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                adapter.submitList(Collections.emptyList());
                return;
            }

            boolean hasRecommendations = !state.getRecommendations().isEmpty();
            adapter.submitList(state.getRecommendations());
            recyclerView.setVisibility(hasRecommendations ? View.VISIBLE : View.GONE);
            progressBar.setVisibility(
                    state.isLoading() && !hasRecommendations ? View.VISIBLE : View.GONE
            );
            swipeRefresh.setRefreshing(state.isLoading() && hasRecommendations);
            emptyState.setVisibility(
                    !state.isLoading()
                            && !hasRecommendations
                            && state.getErrorMessage() == null
                            ? View.VISIBLE
                            : View.GONE
            );
            errorState.setVisibility(
                    !state.isLoading()
                            && !hasRecommendations
                            && state.getErrorMessage() != null
                            ? View.VISIBLE
                            : View.GONE
            );
            if (state.getErrorMessage() != null) {
                errorMessage.setText(state.getErrorMessage());
                if (hasRecommendations) {
                    Snackbar.make(view, state.getErrorMessage(), Snackbar.LENGTH_LONG).show();
                    viewModel.clearError();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.refresh();
        }
    }
}
