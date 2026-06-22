package com.smartfeed.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.smartfeed.MainActivity;
import com.smartfeed.R;
import com.smartfeed.data.api.ApiClient;
import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.local.SessionManager;
import com.smartfeed.data.model.AuthRequest;
import com.smartfeed.data.model.AuthResponse;
import com.smartfeed.data.repository.ApiResult;
import com.smartfeed.data.repository.NetworkRequestExecutor;
import com.smartfeed.util.UserMessageResolver;
import com.smartfeed.util.SystemBarInsets;

import retrofit2.Call;

public final class RegisterActivity extends AppCompatActivity {

    private View rootView;
    private TextInputLayout fullNameLayout;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputLayout confirmPasswordLayout;
    private TextInputEditText fullNameInput;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmPasswordInput;
    private MaterialButton registerButton;
    private MaterialButton loginButton;
    private View progressBar;
    private SessionManager sessionManager;
    private SmartFeedApi api;
    private Call<AuthResponse> activeCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);
        if (sessionManager.hasAccessToken()) {
            openMainActivity();
            return;
        }

        setContentView(R.layout.activity_register);
        SystemBarInsets.apply(findViewById(R.id.registerRoot), true, true);
        bindViews();
        api = ApiClient.create(sessionManager);

        registerButton.setOnClickListener(view -> submitRegistration());
        loginButton.setOnClickListener(view -> finish());
    }

    private void bindViews() {
        rootView = findViewById(R.id.registerRoot);
        fullNameLayout = findViewById(R.id.registerFullNameLayout);
        emailLayout = findViewById(R.id.registerEmailLayout);
        passwordLayout = findViewById(R.id.registerPasswordLayout);
        confirmPasswordLayout = findViewById(R.id.registerConfirmPasswordLayout);
        fullNameInput = findViewById(R.id.registerFullNameInput);
        emailInput = findViewById(R.id.registerEmailInput);
        passwordInput = findViewById(R.id.registerPasswordInput);
        confirmPasswordInput = findViewById(R.id.registerConfirmPasswordInput);
        registerButton = findViewById(R.id.registerButton);
        loginButton = findViewById(R.id.openLoginButton);
        progressBar = findViewById(R.id.registerProgress);
    }

    private void submitRegistration() {
        String fullName = textOf(fullNameInput).trim();
        String email = textOf(emailInput).trim();
        String password = textOf(passwordInput);
        String confirmPassword = textOf(confirmPasswordInput);
        if (!validate(fullName, email, password, confirmPassword)) {
            return;
        }

        setLoading(true);
        activeCall = api.register(AuthRequest.forRegistration(email, fullName, password));
        NetworkRequestExecutor.enqueue(activeCall, this::handleRegistrationResult);
    }

    private boolean validate(
            String fullName,
            String email,
            String password,
            String confirmPassword
    ) {
        fullNameLayout.setError(null);
        emailLayout.setError(null);
        passwordLayout.setError(null);
        confirmPasswordLayout.setError(null);
        boolean valid = true;

        if (fullName.length() < 2) {
            fullNameLayout.setError(getString(R.string.error_full_name));
            valid = false;
        }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError(getString(R.string.error_invalid_email));
            valid = false;
        }
        if (password.length() < 8) {
            passwordLayout.setError(getString(R.string.error_password_length));
            valid = false;
        } else if (password.length() > 72) {
            passwordLayout.setError(getString(R.string.error_password_too_long));
            valid = false;
        }
        if (!password.equals(confirmPassword)) {
            confirmPasswordLayout.setError(getString(R.string.error_password_mismatch));
            valid = false;
        }
        return valid;
    }

    private void handleRegistrationResult(ApiResult<AuthResponse> result) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            setLoading(false);

            AuthResponse response = result.getData();
            if (result.isSuccess()
                    && response != null
                    && response.getAccessToken() != null
                    && !response.getAccessToken().trim().isEmpty()) {
                sessionManager.saveAccessToken(response.getAccessToken());
                openMainActivity();
                return;
            }

            Snackbar.make(rootView, errorMessage(result), Snackbar.LENGTH_LONG).show();
        });
    }

    private String errorMessage(ApiResult<?> result) {
        return UserMessageResolver.resolveRegistration(this, result);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        registerButton.setEnabled(!loading);
        loginButton.setEnabled(!loading);
        fullNameInput.setEnabled(!loading);
        emailInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        confirmPasswordInput.setEnabled(!loading);
    }

    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    @Override
    protected void onDestroy() {
        if (activeCall != null && !activeCall.isCanceled()) {
            activeCall.cancel();
        }
        super.onDestroy();
    }
}
