package com.android.alpha.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.utils.LoadingDialog;

public class SignUpActivity extends AppCompatActivity {

    private EditText etUsername, etPassword, etConfirmPassword;
    private Button btnSignUp;
    private TextView tvLoginLink, tvUsernameError, tvPasswordError, tvConfirmPasswordError;
    private LoadingDialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        initializeViews();
        setupAnimations();
        setupListeners();
    }

    private void initializeViews() {
        etUsername = findViewById(R.id.etUsernameSignUp);
        etPassword = findViewById(R.id.etPasswordSignUp);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnSignUp = findViewById(R.id.btnSignUp);
        tvLoginLink = findViewById(R.id.tvLoginLink);

        tvUsernameError = findViewById(R.id.tvUsernameError);
        tvPasswordError = findViewById(R.id.tvPasswordError);
        tvConfirmPasswordError = findViewById(R.id.tvConfirmPasswordError);

        loadingDialog = new LoadingDialog(this);
    }

    private void setupAnimations() {
        LottieAnimationView lottie = findViewById(R.id.lottieAnimationViewSignUp);
        lottie.setAnimation(R.raw.signup_animation);
        lottie.playAnimation();
    }

    private void setupListeners() {
        btnSignUp.setOnClickListener(v -> attemptSignUp());
        tvLoginLink.setOnClickListener(v -> navigateToLogin());

        etUsername.addTextChangedListener(simpleWatcher(this::validateUsername));
        etPassword.addTextChangedListener(simpleWatcher(s -> {
            validatePassword(s);
            validateConfirmPassword();
        }));
        etConfirmPassword.addTextChangedListener(simpleWatcher(s -> validateConfirmPassword()));
    }

    private TextWatcher simpleWatcher(TextChangeHandler handler) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { handler.onTextChanged(s.toString()); }
        };
    }

    private void validateUsername(String username) {
        toggleError(tvUsernameError,
                username.isEmpty() ? null :
                        (UserSession.isUsernameInvalid(username) ? getString(R.string.username_error_message) : ""));
    }

    private void validatePassword(String password) {
        toggleError(tvPasswordError,
                password.isEmpty() ? null :
                        (UserSession.isPasswordInvalid(password) ? getString(R.string.password_error_message) : ""));
    }

    private void validateConfirmPassword() {
        String password = etPassword.getText().toString().trim();
        String confirm = etConfirmPassword.getText().toString().trim();

        toggleError(tvConfirmPasswordError,
                confirm.isEmpty() ? null :
                        (!password.equals(confirm) ? getString(R.string.confirm_password_error_message) : ""));
    }

    private void toggleError(TextView errorView, String message) {
        if (message == null || message.isEmpty()) {
            errorView.setVisibility(View.GONE);
        } else {
            errorView.setText(message);
            errorView.setVisibility(View.VISIBLE);
        }
    }

    private void attemptSignUp() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (performFinalValidation(username, password, confirmPassword)) {
            Toast.makeText(this, "Please fix the errors above.", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading();

        new Handler().postDelayed(() -> {
            loadingDialog.dismiss();
            boolean success = UserSession.getInstance().registerUser(username, password);

            if (success) {
                Toast.makeText(this, "Sign Up Successful! Please login.", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            } else {
                tvUsernameError.setText(R.string.username_already_exists);
                tvUsernameError.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Username already in use, please choose another.", Toast.LENGTH_LONG).show();
            }

        }, 1500);
    }

    private boolean performFinalValidation(String username, String password, String confirmPassword) {
        boolean hasError = false;

        toggleError(tvUsernameError, "");
        toggleError(tvPasswordError, "");
        toggleError(tvConfirmPasswordError, "");

        if (username.isEmpty()) {
            toggleError(tvUsernameError, getString(R.string.field_required)); hasError = true;
        } else if (UserSession.isUsernameInvalid(username)) {
            toggleError(tvUsernameError, getString(R.string.username_error_message)); hasError = true;
        }

        if (password.isEmpty()) {
            toggleError(tvPasswordError, getString(R.string.field_required)); hasError = true;
        } else if (UserSession.isPasswordInvalid(password)) {
            toggleError(tvPasswordError, getString(R.string.password_error_message)); hasError = true;
        }

        if (confirmPassword.isEmpty()) {
            toggleError(tvConfirmPasswordError, getString(R.string.field_required)); hasError = true;
        } else if (!password.equals(confirmPassword)) {
            toggleError(tvConfirmPasswordError, getString(R.string.confirm_password_error_message)); hasError = true;
        }

        return hasError;
    }

    private void navigateToLogin() {
        showLoading();
        new Handler().postDelayed(() -> {
            loadingDialog.dismiss();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }, 1200);
    }

    private void showLoading() {
        if (!loadingDialog.isShowing()) loadingDialog.show();
    }

    private interface TextChangeHandler {
        void onTextChanged(String text);
    }
}
