package com.smartfeed;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.smartfeed.data.local.SessionManager;
import com.smartfeed.ui.auth.LoginActivity;
import com.smartfeed.ui.analytics.AnalyticsFragment;
import com.smartfeed.ui.categories.CategoriesFragment;
import com.smartfeed.ui.feed.FeedFragment;
import com.smartfeed.ui.recommendations.RecommendationsFragment;
import com.smartfeed.ui.saved.SavedFragment;
import com.smartfeed.ui.profile.ProfileFragment;
import com.smartfeed.util.SystemBarInsets;

public class MainActivity extends AppCompatActivity {

    private static final String STATE_SELECTED_NAV_ITEM = "selected_nav_item";

    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!new SessionManager(this).hasAccessToken()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        SystemBarInsets.apply(findViewById(R.id.mainRoot), true, false);
        toolbar = findViewById(R.id.mainToolbar);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        toolbar.inflateMenu(R.menu.main_toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.actionProfile) {
                showProfile();
                return true;
            }
            return false;
        });
        toolbar.setNavigationOnClickListener(ignored ->
                getOnBackPressedDispatcher().onBackPressed()
        );
        getSupportFragmentManager().addOnBackStackChangedListener(
                this::updateToolbarForVisibleFragment
        );

        bottomNavigation.setOnItemSelectedListener(item ->
                showDestination(item.getItemId())
        );
        bottomNavigation.setOnItemReselectedListener(item ->
                showDestination(item.getItemId())
        );

        if (savedInstanceState == null) {
            bottomNavigation.getMenu().findItem(R.id.navFeed).setChecked(true);
            showFragment(new FeedFragment(), R.string.nav_feed);
        } else {
            int selectedItemId = savedInstanceState.getInt(
                    STATE_SELECTED_NAV_ITEM,
                    R.id.navFeed
            );
            MenuItem selectedItem = bottomNavigation.getMenu().findItem(selectedItemId);
            if (selectedItem == null) {
                selectedItemId = R.id.navFeed;
                selectedItem = bottomNavigation.getMenu().findItem(selectedItemId);
            }
            selectedItem.setChecked(true);

            if (getSupportFragmentManager().findFragmentById(R.id.mainContainer) == null) {
                showDestination(selectedItemId);
            } else {
                updateToolbarForVisibleFragment();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (bottomNavigation != null) {
            outState.putInt(STATE_SELECTED_NAV_ITEM, bottomNavigation.getSelectedItemId());
        }
        super.onSaveInstanceState(outState);
    }

    private boolean showDestination(int itemId) {
        getSupportFragmentManager().popBackStackImmediate(
                null,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
        );
        if (itemId == R.id.navFeed) {
            showFragment(new FeedFragment(), R.string.nav_feed);
            return true;
        }
        if (itemId == R.id.navRecommendations) {
            showFragment(new RecommendationsFragment(), R.string.nav_recommendations);
            return true;
        }
        if (itemId == R.id.navSaved) {
            showFragment(new SavedFragment(), R.string.nav_saved);
            return true;
        }
        if (itemId == R.id.navCategories) {
            showFragment(new CategoriesFragment(), R.string.nav_categories);
            return true;
        }
        if (itemId == R.id.navAnalytics) {
            showFragment(new AnalyticsFragment(), R.string.nav_analytics);
            return true;
        }
        return false;
    }

    private void showFragment(Fragment fragment, int titleResId) {
        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.mainContainer, fragment)
                .commit();
        toolbar.setTitle(titleResId);
        updateToolbarActions(false);
    }

    private void showProfile() {
        Fragment current = getSupportFragmentManager().findFragmentById(
                R.id.mainContainer
        );
        if (current instanceof ProfileFragment) {
            return;
        }
        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.mainContainer, new ProfileFragment())
                .addToBackStack("profile")
                .commit();
        toolbar.setTitle(R.string.nav_profile);
        updateToolbarActions(true);
    }

    private void updateToolbarForVisibleFragment() {
        Fragment current = getSupportFragmentManager().findFragmentById(
                R.id.mainContainer
        );
        if (current instanceof ProfileFragment) {
            toolbar.setTitle(R.string.nav_profile);
            updateToolbarActions(true);
            return;
        }
        updateToolbarTitle(bottomNavigation.getSelectedItemId());
        updateToolbarActions(false);
    }

    private void updateToolbarActions(boolean profileVisible) {
        if (profileVisible) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            toolbar.setNavigationContentDescription(R.string.action_back);
        } else {
            toolbar.setNavigationIcon(null);
            toolbar.setNavigationContentDescription(null);
        }
        MenuItem profileItem = toolbar.getMenu().findItem(R.id.actionProfile);
        if (profileItem != null) {
            profileItem.setVisible(!profileVisible);
        }
    }

    private void updateToolbarTitle(int itemId) {
        if (itemId == R.id.navRecommendations) {
            toolbar.setTitle(R.string.nav_recommendations);
        } else if (itemId == R.id.navSaved) {
            toolbar.setTitle(R.string.nav_saved);
        } else if (itemId == R.id.navCategories) {
            toolbar.setTitle(R.string.nav_categories);
        } else if (itemId == R.id.navAnalytics) {
            toolbar.setTitle(R.string.nav_analytics);
        } else {
            toolbar.setTitle(R.string.nav_feed);
        }
    }
}
