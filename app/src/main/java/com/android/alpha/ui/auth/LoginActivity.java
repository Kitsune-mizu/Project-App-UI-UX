package com.android.alpha.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.main.MainActivity;
import com.android.alpha.utils.LoadingDialog;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity {

    // === CONSTANTS ===
    private static final String PREFS_NAME = "login_prefs";
    private static final String KEY_REMEMBER = "remember";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";

    // === UI COMPONENTS ===
    private EditText etUsername, etPassword;
    private TextInputLayout tilUsername, tilPassword;
    private CheckBox cbRememberMe;
    private Button btnLogin;
    private TextView tvSignUp, tvForgotPassword;

    // === DEPENDENCIES ===
    private LoadingDialog loadingDialog;

    // === INTERFACES ===
    private interface TextValidation { void onValidated(String text); }

    // === ACTIVITY LIFECYCLE ===
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initializeComponents();
        loadSavedCredentials();
        setupInputListeners();
        setupClickListeners();
    }

    // === INITIALIZATION ===
    private void initializeComponents() {
        tilUsername = findViewById(R.id.tilUsername);
        tilPassword = findViewById(R.id.tilPassword);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvSignUp = findViewById(R.id.tvSignUp);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        cbRememberMe = findViewById(R.id.cbRememberMe);
        loadingDialog = new LoadingDialog(this);

        LottieAnimationView lottie = findViewById(R.id.lottieAnimationView);
        lottie.setAnimation(R.raw.login_animation);
        lottie.playAnimation();
    }

    // === INPUT LISTENERS ===
    private void setupInputListeners() {
        etUsername.addTextChangedListener(createWatcher(this::validateUsername));
        etPassword.addTextChangedListener(createWatcher(this::validatePasswordFormat));
    }

    private TextWatcher createWatcher(TextValidation validation) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) { validation.onValidated(s.toString()); }
        };
    }

    // === CLICK LISTENERS ===
    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());

        tvSignUp.setOnClickListener(v -> {
            showLoading();
            new Handler().postDelayed(() -> {
                loadingDialog.dismiss();
                startActivity(new Intent(this, SignUpActivity.class));
            }, 1500);
        });

        tvForgotPassword.setOnClickListener(v -> {
            hideKeyboard(v);
            startActivity(new Intent(this, ForgotPasswordActivity.class));
        });
    }

    // === CORE LOGIC - LOGIN ===
    private void attemptLogin() {
        String username = etUsername.getText() == null ? "" : etUsername.getText().toString().trim();
        String password = etPassword.getText() == null ? "" : etPassword.getText().toString().trim();

        hideKeyboard(etPassword);

        if (validateInputFields(username, password)) return;

        showLoading();
        new Handler().postDelayed(() -> {
            loadingDialog.dismiss();
            UserSession session = UserSession.getInstance();

            // Check if username exists (session.isUsername returns true if it DOES NOT contain username)
            if (session.getUserData(username) == null) {
                tilUsername.setError(getString(R.string.username_not_found));
                tilPassword.setError(null);
                return;
            }

            if (session.login(username, password)) {
                saveCredentials(username, password);
                startActivity(new Intent(this, MainActivity.class));
                finish();
            } else {
                // Login failed, assumed incorrect password if username was found
                tilPassword.setError(getString(R.string.incorrect_password));
                tilUsername.setError(null);
            }
        }, 1500);
    }

    // === INPUT VALIDATION ===
    private boolean validateInputFields(String username, String password) {
        boolean hasError = false;

        if (username.isEmpty()) {
            tilUsername.setError(getString(R.string.username_required));
            hasError = true;
        } else if (UserSession.isUsernameInvalid(username)) {
            tilUsername.setError(getString(R.string.username_format_error));
            hasError = true;
        } else tilUsername.setError(null);

        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.password_required));
            hasError = true;
        } else if (UserSession.isPasswordInvalid(password)) {
            tilPassword.setError(getString(R.string.password_format_error));
            hasError = true;
        } else tilPassword.setError(null);

        return hasError;
    }

    private void validateUsername(String username) {
        if (username.isEmpty()) {
            tilUsername.setError(null);
            return;
        }
        // session.isUsername returns true if user DOES NOT exist
        if (UserSession.getInstance().getUserData(username) == null) {
            tilUsername.setError(getString(R.string.username_not_found));
        } else {
            tilUsername.setError(null);
        }
    }

    private void validatePasswordFormat(String password) {
        if (password.isEmpty()) {
            tilPassword.setError(null);
            return;
        }
        tilPassword.setError(UserSession.isPasswordInvalid(password)
                ? getString(R.string.password_format_error) : null);
    }

    // === PERSISTENCE (REMEMBER ME) ===
    private void saveCredentials(String username, String password) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (cbRememberMe.isChecked()) {
            editor.putBoolean(KEY_REMEMBER, true);
            editor.putString(KEY_USERNAME, username);
            editor.putString(KEY_PASSWORD, password);
        } else editor.clear();

        editor.apply();
    }

    private void loadSavedCredentials() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_REMEMBER, false)) {
            etUsername.setText(prefs.getString(KEY_USERNAME, ""));
            etPassword.setText(prefs.getString(KEY_PASSWORD, ""));
            cbRememberMe.setChecked(true);
        }
    }

    // === UTILITIES ===
    private void showLoading() {
        if (!loadingDialog.isShowing()) loadingDialog.show();
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}