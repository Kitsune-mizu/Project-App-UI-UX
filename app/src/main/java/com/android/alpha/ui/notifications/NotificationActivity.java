package com.android.alpha.ui.notifications;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NotificationActivity extends AppCompatActivity {

    // === FIELDS ===
    private NotificationAdapter adapter;
    private UserSession.ActivityListener activityListener;
    private RecyclerView rvActivities;
    private View emptyActivity;

    // === LIFECYCLE METHODS ===
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        setupToolbar();
        setupRecyclerView();
        loadInitialActivities();
        setupActivityListener();
    }

    @Override
    protected void onResume() {
        super.onResume();

        adapter.submitList(
                new ArrayList<>(adapter.getCurrentList()),
                this::updateEmptyState
        );

        UserSession.getInstance().notifyBadgeCleared();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activityListener != null) {
            UserSession.getInstance().removeActivityListener();
        }
    }

    // === UI SETUP ===
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            // Already using @string
            getSupportActionBar().setTitle(R.string.notifications_title);
        }

        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.md_theme_light_onPrimary));
        Objects.requireNonNull(toolbar.getNavigationIcon())
                .setTint(ContextCompat.getColor(this, R.color.md_theme_light_onSurface));
    }

    private void setupRecyclerView() {
        rvActivities = findViewById(R.id.rvActivities);
        emptyActivity = findViewById(R.id.emptyActivity);

        rvActivities.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter();
        rvActivities.setAdapter(adapter);
    }

    private void updateEmptyState() {
        boolean isEmpty = adapter.getCurrentList().isEmpty();

        emptyActivity.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvActivities.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    // === DATA MANAGEMENT ===
    private void loadInitialActivities() {
        adapter.submitList(
                new ArrayList<>(UserSession.getInstance().getActivities()),
                this::updateEmptyState
        );
    }

    private void setupActivityListener() {
        activityListener = item -> runOnUiThread(() -> {
            List<ActivityItem> current = new ArrayList<>(adapter.getCurrentList());
            current.add(0, item);

            adapter.submitList(current, this::updateEmptyState);
        });

        UserSession.getInstance().addActivityListener(activityListener);
    }

    private void clearAllNotifications() {
        UserSession.getInstance().clearActivities();
        adapter.submitList(new ArrayList<>(), this::updateEmptyState);
    }

    // === MENU & ACTION HANDLING ===
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_notifications, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        }

        if (id == R.id.action_clear_all) {
            clearAllNotifications();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}