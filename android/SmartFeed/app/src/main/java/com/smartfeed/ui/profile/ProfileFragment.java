package com.smartfeed.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.smartfeed.R;
import com.smartfeed.data.model.UserDto;
import com.smartfeed.ui.auth.LoginActivity;

public final class ProfileFragment extends Fragment {

    private ProfileViewModel viewModel;

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        View accountProgress = view.findViewById(R.id.profileAccountProgress);
        View accountContent = view.findViewById(R.id.profileAccountContent);
        View accountError = view.findViewById(R.id.profileAccountError);
        TextView fullNameView = view.findViewById(R.id.profileFullName);
        TextView emailView = view.findViewById(R.id.profileEmail);
        TextView accountErrorMessage = view.findViewById(R.id.profileAccountErrorMessage);
        MaterialButton accountRetryButton = view.findViewById(
                R.id.profileAccountRetryButton
        );
        TextView syncStatusView = view.findViewById(R.id.profileSyncStatus);
        TextView pendingCountView = view.findViewById(R.id.profilePendingEventCount);
        MaterialButton syncButton = view.findViewById(R.id.profileSyncButton);
        MaterialButton logoutButton = view.findViewById(R.id.profileLogoutButton);
        viewModel = new ViewModelProvider(this)
                .get(ProfileViewModel.class);

        accountRetryButton.setOnClickListener(ignored -> viewModel.refreshUser());
        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            UserDto user = state.getUser();
            boolean hasUser = user != null;
            accountProgress.setVisibility(
                    state.isLoading() && !hasUser ? View.VISIBLE : View.GONE
            );
            accountContent.setVisibility(hasUser ? View.VISIBLE : View.GONE);
            accountError.setVisibility(
                    !state.isLoading() && !hasUser && state.getErrorMessage() != null
                            ? View.VISIBLE
                            : View.GONE
            );
            if (user != null) {
                emailView.setText(user.getEmail());
                String fullName = user.getFullName();
                fullNameView.setVisibility(
                        fullName == null || fullName.trim().isEmpty()
                                ? View.GONE
                                : View.VISIBLE
                );
                fullNameView.setText(fullName);
            }
            if (state.getErrorMessage() != null) {
                accountErrorMessage.setText(state.getErrorMessage());
                if (hasUser) {
                    Snackbar.make(view, state.getErrorMessage(), Snackbar.LENGTH_LONG).show();
                }
            }
        });

        viewModel.getPendingEventCount().observe(getViewLifecycleOwner(), count -> {
            int pendingCount = count == null ? 0 : count;
            pendingCountView.setText(getString(
                    R.string.profile_pending_events_count,
                    pendingCount
            ));
            syncStatusView.setText(
                    pendingCount == 0
                            ? R.string.profile_sync_status_complete
                            : R.string.profile_sync_status_pending
            );
            syncButton.setEnabled(pendingCount > 0);
        });

        syncButton.setOnClickListener(button -> {
            viewModel.syncNow();
            Snackbar.make(
                    view,
                    R.string.profile_sync_requested,
                    Snackbar.LENGTH_SHORT
            ).show();
        });

        logoutButton.setOnClickListener(ignored -> new MaterialAlertDialogBuilder(
                requireContext()
        )
                .setTitle(R.string.profile_logout_confirm_title)
                .setMessage(R.string.profile_logout_confirm_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.profile_logout_action, (dialog, which) -> {
                    viewModel.logout();
                    Intent intent = new Intent(requireContext(), LoginActivity.class)
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
                            );
                    startActivity(intent);
                    requireActivity().finish();
                })
                .show());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.refreshUser();
        }
    }
}
