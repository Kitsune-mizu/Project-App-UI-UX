package com.android.alpha.ui.main;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.airbnb.lottie.LottieAnimationView;
import com.android.alpha.R;
import com.android.alpha.data.local.UserStorageManager;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.auth.LoginActivity;
import com.android.alpha.ui.home.HomeFragment;
import com.android.alpha.ui.home.NotificationActivity;
import com.android.alpha.ui.map.MapFragment;
import com.android.alpha.ui.profile.ProfileFragment;
import com.android.alpha.ui.profile.SettingsFragment;
import com.android.alpha.utils.DialogUtils;
import com.android.alpha.utils.LoadingDialog;
import com.bumptech.glide.Glide;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONObject;

import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, UserSession.UserSessionListener {

    // UI
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;
    private ImageView ivProfile;
    private LottieAnimationView lottieProfileNav;
    private TextView tvUsername, tvUserEmail;
    private View notificationBadge;

    // Utilities
    private LoadingDialog loadingDialog;
    private final Handler handler = new Handler();
    private float startX;
    private boolean isSwiping = false;

    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final long FRAGMENT_LOAD_DELAY = 600;
    private static final long LOGOUT_DELAY = 1200;
    private int currentMenuId = R.id.nav_home;

    // ===== Lifecycle =====
    @Override
    protected void attachBaseContext(Context newBase) {
        String lang = UserSession.getInstance() != null
                ? UserSession.getInstance().getLanguage()
                : "en";

        super.attachBaseContext(com.android.alpha.utils.LocaleHelper.setLocale(newBase, lang));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        UserSession.init(this);
        UserSession session = getSession();
        session.addListener(mainSessionListener);
        session.removeOldActivities();

        initViews();
        setupToolbar();
        setupNavigationDrawer();
        setupGestureBack();
        setupFooter();
        setupBackStackListener();

        showFragment(new HomeFragment(), "Home", false);

        if (getIntent().getBooleanExtra("show_badge", false)) showNotificationBadge();
        requestNotificationPermission();
        session.setBadgeListener(badgeSessionListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyCurrentLanguage();
        updateToolbarTitleByFragment();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        UserSession session = getSession();
        if (session != null) session.removeListener();
    }

    private UserSession getSession() {
        return UserSession.getInstance();
    }

    // ===== Initialization =====

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        toolbar = findViewById(R.id.toolbar);
        loadingDialog = new LoadingDialog(this);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle("Home");
    }

    private void setupNavigationDrawer() {
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);

        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        toggle.getDrawerArrowDrawable().setColor(ContextCompat.getColor(this, R.color.md_theme_light_onSurface));
        navigationView.setNavigationItemSelectedListener(this);

        View header = navigationView.getHeaderView(0);
        ivProfile = header.findViewById(R.id.ivProfile);
        lottieProfileNav = header.findViewById(R.id.lottieProfileNav);
        tvUsername = header.findViewById(R.id.tvUsername);
        tvUserEmail = header.findViewById(R.id.tvUserEmail);
        updateNavHeaderProfile();

        colorLogoutItem();
        setupNotificationBadge();
    }

    private void colorLogoutItem() {
        MenuItem logoutItem = navigationView.getMenu().findItem(R.id.nav_logout);
        SpannableString s = new SpannableString(logoutItem.getTitle());
        s.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.md_theme_light_error)),
                0, s.length(), 0);
        logoutItem.setTitle(s);
    }

    private void setupFooter() {
        TextView footer = new TextView(this);
        footer.setText(getString(R.string.footer_text));
        footer.setTextSize(12);
        footer.setTextColor(ContextCompat.getColor(this, R.color.md_theme_light_onSurface));
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, 40, 0, 40);

        NavigationView.LayoutParams params = new NavigationView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM;

        footer.setLayoutParams(params);
        navigationView.addView(footer);
    }

    // ===== Back gesture =====

    private void setupGestureBack() {
        View container = findViewById(R.id.fragment_container);
        container.setOnTouchListener((v, e) -> {
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (current instanceof HomeFragment) return false;

            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = e.getX();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float delta = e.getX() - startX;
                    if (delta > 0) {
                        isSwiping = true;
                        v.setTranslationX(delta * 0.8f);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    float move = e.getX() - startX;

                    // geser untuk back
                    if (isSwiping && move > v.getWidth() / 3f) {
                        animateBack(v);
                    } else {
                        v.animate().translationX(0).setDuration(200).start();
                    }

                    if (Math.abs(move) < 10) {
                        v.performClick();
                    }

                    isSwiping = false;
                    return true;
            }
            return false;
        });
    }

    private void animateBack(View v) {
        v.animate().translationX(v.getWidth()).setDuration(200).withEndAction(() -> {
            v.setTranslationX(0);
            getOnBackPressedDispatcher().onBackPressed();
        }).start();
    }

    // ===== Fragment Navigation =====

    public void showFragment(Fragment fragment, String title, boolean addToBackStack) {
        showLoading();
        handler.postDelayed(() -> {
            if (addToBackStack)
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .replace(R.id.fragment_container, fragment, title)
                        .addToBackStack(title)
                        .commitAllowingStateLoss();
            else
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, fragment, title)
                        .commitAllowingStateLoss();

            highlightActiveMenu(fragment);
            loadingDialog.dismiss();
        }, FRAGMENT_LOAD_DELAY);
    }

    private void setupBackStackListener() {
        getSupportFragmentManager().addOnBackStackChangedListener(this::updateToolbarTitleByFragment);
    }

    private void updateToolbarTitleByFragment() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f == null || getSupportActionBar() == null) return;

        int titleId = R.string.menu_title_home;
        if (f instanceof ProfileFragment) titleId = R.string.menu_title_profile;
        if (f instanceof SettingsFragment) titleId = R.string.menu_title_settings;
        if (f instanceof MapFragment) titleId = R.string.map_title;

        applyCurrentLanguage();
        getSupportActionBar().setTitle(getString(titleId));
        highlightActiveMenu(f);
    }

    private void highlightActiveMenu(Fragment fragment) {
        int newId = (fragment instanceof ProfileFragment) ? R.id.nav_profile :
                (fragment instanceof SettingsFragment) ? R.id.nav_settings :
                        currentMenuId;

        if (newId != currentMenuId) currentMenuId = newId;
        navigationView.getMenu().findItem(newId).setChecked(true);
    }

    // ===== Drawer Menu =====

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_logout) showLogoutDialog();
        else if (id == R.id.nav_notifications) {
            currentMenuId = R.id.nav_notifications;
            startActivity(new Intent(this, NotificationActivity.class));
            hideNotificationBadge();
        } else {
            showFragment(getFragmentForMenu(id),
                    Objects.requireNonNull(item.getTitle()).toString(), id != R.id.nav_home);
        }

        drawerLayout.closeDrawers();
        return true;
    }

    private Fragment getFragmentForMenu(int id) {
        if (id == R.id.nav_home) return new HomeFragment();
        if (id == R.id.nav_profile) return new ProfileFragment();
        if (id == R.id.nav_settings) return new SettingsFragment();
        return null;
    }

    // ===== Profile Update =====

    public void updateNavHeaderProfile() {
        try {
            UserSession session = getSession();
            String user = session.getUsername();
            if (user == null || user.isEmpty()) {
                showGuestProfile();
                return;
            }

            UserSession.UserData data = session.getUserData(user);
            String full = user, email = user + "@example.com", photo = "";

            if (data != null) {
                JSONObject p = UserStorageManager.getInstance(this).loadUserProfile(data.userId);
                if (p != null) {
                    full = p.optString("username", user);
                    email = p.optString("email", email);
                    photo = p.optString("photoPath", "");
                }
            }

            tvUsername.setText(full);
            tvUserEmail.setText(email);

            if (!photo.isEmpty()) {
                lottieProfileNav.setVisibility(View.GONE);
                ivProfile.setVisibility(View.VISIBLE);
                Glide.with(this).load(photo).circleCrop().into(ivProfile);
            } else showGuestProfile();

        } catch (Exception e) {
            Log.e("MainActivity", "Profile update error: " + e.getMessage());
        }
    }

    private void showGuestProfile() {
        ivProfile.setVisibility(View.GONE);
        lottieProfileNav.setVisibility(View.VISIBLE);
        tvUsername.setText(R.string.title_guest);
        tvUserEmail.setText("");
        lottieProfileNav.setAnimation(R.raw.profile_animation);
        lottieProfileNav.playAnimation();
    }

    // ===== Notification Badge =====

    private void setupNotificationBadge() {
        MenuItem item = navigationView.getMenu().findItem(R.id.nav_notifications);
        FrameLayout frame = new FrameLayout(this);

        View badge = new View(this);
        badge.setBackgroundResource(R.drawable.badge_red_circle);

        int size = (int) (8 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.gravity = Gravity.TOP | Gravity.END;
        params.setMargins(0, (int) (17 * getResources().getDisplayMetrics().density),
                (int) (8 * getResources().getDisplayMetrics().density), 0);

        badge.setLayoutParams(params);
        badge.setVisibility(View.GONE);

        frame.addView(badge);
        item.setActionView(frame);
        notificationBadge = badge;
    }

    public void showNotificationBadge() { if (notificationBadge != null) notificationBadge.setVisibility(View.VISIBLE); }
    public void hideNotificationBadge() { if (notificationBadge != null) notificationBadge.setVisibility(View.GONE); }

    // ===== Logout =====

    private void showLogoutDialog() {
        DialogUtils.showConfirmDialog(
                this, getString(R.string.logout_title),
                getString(R.string.logout_message),
                getString(R.string.logout), getString(R.string.cancel),
                this::logout, null);
    }

    public void logout() {
        showLoading();
        handler.postDelayed(() -> {
            try {
                UserSession.getInstance().logout();
                Intent i = new Intent(this, LoginActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            } catch (Exception e) {
                Log.e("MainActivity", "Logout error: " + e.getMessage());
            }
            loadingDialog.dismiss();
        }, LOGOUT_DELAY);
    }

    // ===== Utilities =====

    public void applyCurrentLanguage() {
        String lang = UserSession.getInstance().getLanguage();
        Locale locale = new Locale(lang == null || lang.isEmpty() ? "en" : lang);
        Locale.setDefault(locale);

        android.content.res.Resources res = getResources();
        android.content.res.Configuration config = res.getConfiguration();
        config.setLocale(locale);
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
    }

    private void showLoading() { if (!loadingDialog.isShowing()) loadingDialog.show(); }

    // ===== Session Listeners =====

    private final UserSession.UserSessionListener mainSessionListener = new UserSession.UserSessionListener() {
        @Override public void onBadgeCleared() {
            UserSession.UserSessionListener.super.onBadgeCleared();
        }
        @Override public void onProfileUpdated() { runOnUiThread(MainActivity.this::updateNavHeaderProfile); }
    };

    private final UserSession.UserSessionListener badgeSessionListener = new UserSession.UserSessionListener() {
        @Override public void onBadgeCleared() { hideNotificationBadge(); }
        @Override public void onProfileUpdated() {
            UserSession.UserSessionListener.super.onProfileUpdated();
        }
    };

    @Override
    public void onProfileUpdated() { runOnUiThread(this::updateNavHeaderProfile); }
}
