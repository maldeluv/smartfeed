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

public final class LoginActivity extends AppCompatActivity {

    private View rootView;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private MaterialButton loginButton;
    private MaterialButton registerButton;
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

        setContentView(R.layout.activity_login);
        SystemBarInsets.apply(findViewById(R.id.loginRoot), true, true);
        bindViews();
        api = ApiClient.create(sessionManager);

        loginButton.setOnClickListener(view -> submitLogin());
        registerButton.setOnClickListener(view ->
                startActivity(new Intent(this, RegisterActivity.class))
        );
    }

    private void bindViews() {
        rootView = findViewById(R.id.loginRoot);
        emailLayout = findViewById(R.id.loginEmailLayout);
        passwordLayout = findViewById(R.id.loginPasswordLayout);
        emailInput = findViewById(R.id.loginEmailInput);
        passwordInput = findViewById(R.id.loginPasswordInput);
        loginButton = findViewById(R.id.loginButton);
        registerButton = findViewById(R.id.openRegisterButton);
        progressBar = findViewById(R.id.loginProgress);
    }

    private void submitLogin() {
        String email = textOf(emailInput).trim();
        String password = textOf(passwordInput);
        if (!validate(email, password)) {
            return;
        }

        setLoading(true);
        activeCall = api.login(AuthRequest.forLogin(email, password));
        NetworkRequestExecutor.enqueue(activeCall, this::handleLoginResult);
    }

    private boolean validate(String email, String password) {
        emailLayout.setError(null);
        passwordLayout.setError(null);
        boolean valid = true;

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError(getString(R.string.error_invalid_email));
            valid = false;
        }
        if (password.isEmpty()) {
            passwordLayout.setError(getString(R.string.error_password_required));
            valid = false;
        }
        return valid;
    }

    private void handleLoginResult(ApiResult<AuthResponse> result) {
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
        return UserMessageResolver.resolveLogin(this, result);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
        registerButton.setEnabled(!loading);
        emailInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
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
