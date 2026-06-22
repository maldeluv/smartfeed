package com.smartfeed.ui.categories;

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

import java.util.Collections;

public final class CategoriesFragment extends Fragment {

    public CategoriesFragment() {
        super(R.layout.fragment_categories);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        SwipeRefreshLayout swipeRefresh = view.findViewById(R.id.categoriesSwipeRefresh);
        RecyclerView recyclerView = view.findViewById(R.id.categoriesRecyclerView);
        View progressBar = view.findViewById(R.id.categoriesProgress);
        View emptyState = view.findViewById(R.id.categoriesEmptyState);
        View errorState = view.findViewById(R.id.categoriesErrorState);
        TextView errorMessage = view.findViewById(R.id.categoriesErrorMessage);
        MaterialButton retryButton = view.findViewById(R.id.categoriesRetryButton);

        CategoriesViewModel viewModel = new ViewModelProvider(this)
                .get(CategoriesViewModel.class);
        CategoryAdapter adapter = new CategoryAdapter(viewModel::toggleSubscription);
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
            boolean hasCategories = !state.getCategories().isEmpty();
            adapter.setPendingIds(state.getPendingIds());
            adapter.submitList(state.getCategories());
            recyclerView.setVisibility(hasCategories ? View.VISIBLE : View.GONE);
            progressBar.setVisibility(
                    state.isLoading() && !hasCategories ? View.VISIBLE : View.GONE
            );
            swipeRefresh.setRefreshing(state.isLoading() && hasCategories);
            emptyState.setVisibility(
                    !state.isLoading() && !hasCategories && state.getErrorMessage() == null
                            ? View.VISIBLE
                            : View.GONE
            );
            errorState.setVisibility(
                    !state.isLoading() && !hasCategories && state.getErrorMessage() != null
                            ? View.VISIBLE
                            : View.GONE
            );
            if (state.getErrorMessage() != null) {
                errorMessage.setText(state.getErrorMessage());
                if (hasCategories) {
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
}
