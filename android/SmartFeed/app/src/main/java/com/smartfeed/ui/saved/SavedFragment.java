package com.smartfeed.ui.saved;

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
import com.smartfeed.ui.article.ArticleDetailActivity;
import com.smartfeed.ui.feed.ArticleAdapter;

import java.util.Collections;

public final class SavedFragment extends Fragment {

    private SavedViewModel viewModel;

    public SavedFragment() {
        super(R.layout.fragment_saved);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        SwipeRefreshLayout swipeRefresh = view.findViewById(R.id.savedSwipeRefresh);
        RecyclerView recyclerView = view.findViewById(R.id.savedRecyclerView);
        View progressBar = view.findViewById(R.id.savedProgress);
        View emptyState = view.findViewById(R.id.savedEmptyState);
        View errorState = view.findViewById(R.id.savedErrorState);
        TextView errorMessage = view.findViewById(R.id.savedErrorMessage);
        MaterialButton retryButton = view.findViewById(R.id.savedRetryButton);

        viewModel = new ViewModelProvider(this).get(SavedViewModel.class);
        ArticleAdapter adapter = new ArticleAdapter(
                new ArticleAdapter.OnArticleActionListener() {
                    @Override
                    public void onArticleClicked(ArticleDto article) {
                        startActivity(ArticleDetailActivity.newIntent(
                                requireContext(),
                                article
                        ));
                    }

                    @Override
                    public void onLikeClicked(ArticleDto article) {
                        viewModel.toggleLike(article);
                    }

                    @Override
                    public void onSaveClicked(ArticleDto article) {
                        viewModel.toggleSave(article);
                    }
                }
        );
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

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
            boolean hasArticles = !state.getArticles().isEmpty();
            adapter.setPendingActions(
                    state.getPendingLikes(),
                    state.getPendingSaves()
            );
            adapter.submitList(state.getArticles());
            recyclerView.setVisibility(hasArticles ? View.VISIBLE : View.GONE);
            progressBar.setVisibility(
                    state.isLoading() && !hasArticles ? View.VISIBLE : View.GONE
            );
            swipeRefresh.setRefreshing(state.isLoading() && hasArticles);
            emptyState.setVisibility(
                    !state.isLoading() && !hasArticles && state.getErrorMessage() == null
                            ? View.VISIBLE
                            : View.GONE
            );
            errorState.setVisibility(
                    !state.isLoading() && !hasArticles && state.getErrorMessage() != null
                            ? View.VISIBLE
                            : View.GONE
            );
            if (state.getErrorMessage() != null) {
                errorMessage.setText(state.getErrorMessage());
                if (hasArticles) {
                    Snackbar.make(view, state.getErrorMessage(), Snackbar.LENGTH_LONG).show();
                    viewModel.clearError();
                }
            }
        });

        viewModel.getNotifications().observe(getViewLifecycleOwner(), event -> {
            if (event == null) {
                return;
            }
            String message = event.getContentIfNotHandled();
            if (message != null) {
                Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
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
