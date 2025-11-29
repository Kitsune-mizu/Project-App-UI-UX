package com.android.alpha.ui.home;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.android.alpha.ui.main.MainActivity;
import com.android.alpha.R;
import com.android.alpha.ui.common.Refreshable;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.data.local.UserStorageManager;
import com.android.alpha.utils.ShimmerHelper;
import com.android.alpha.utils.DialogUtils;
import com.facebook.shimmer.ShimmerFrameLayout;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

public class HomeFragment extends Fragment implements
        UserSession.UserSessionListener,
        UserSession.ActivityListener,
        Refreshable {

    // --- UI Components ---
    private ShimmerFrameLayout shimmerLayout;
    private LottieAnimationView lottieWelcome;
    private TextView tvGreeting, tvUsername, tvDateTime, tvActiveDays, tvViewAll;
    private LinearLayout activityContainer, emptyActivity;
    private SwipeRefreshLayout swipeRefreshLayout;

    // --- Data and Utilities ---
    private final List<ActivityItem> activityList = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("EEEE, d MMMM yyyy • HH:mm", Locale.getDefault());

    private final Runnable timeUpdater = new Runnable() {
        @Override
        public void run() {
            updateDateTime();
            handler.postDelayed(this, 60000);
        }
    };

    public HomeFragment() {}

    // --- Lifecycle ---

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        initUIComponents();
        registerListeners();

        startShimmerInitialLoad(view.findViewById(R.id.scrollViewHome));
        handler.post(timeUpdater);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        unregisterListeners();
    }

    // --- Initialization ---

    private void initializeViews(View view) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        shimmerLayout = view.findViewById(R.id.shimmerLayout);
        lottieWelcome = view.findViewById(R.id.lottieWelcome);
        tvGreeting = view.findViewById(R.id.tvGreeting);
        tvUsername = view.findViewById(R.id.tvUsername);
        tvDateTime = view.findViewById(R.id.tvDateTime);
        tvActiveDays = view.findViewById(R.id.tvActiveDays);
        tvViewAll = view.findViewById(R.id.tvViewAll);
        activityContainer = view.findViewById(R.id.activityContainer);
        emptyActivity = view.findViewById(R.id.emptyActivity);
    }

    private void initUIComponents() {
        setupLottie();
        setupRefresh();
        setupBackPress();
        setupClickListeners();
    }

    private void setupLottie() {
        lottieWelcome.setAnimation(R.raw.welcome_animation);
        lottieWelcome.setRepeatCount(LottieDrawable.INFINITE);
        lottieWelcome.playAnimation();
    }

    private void setupRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::onRefreshRequested);
    }

    private void setupBackPress() {
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        DialogUtils.showConfirmDialog(
                                requireContext(),
                                getString(R.string.dialog_exit_title),
                                getString(R.string.dialog_exit_message),
                                getString(R.string.action_exit),
                                getString(R.string.action_cancel),
                                () -> requireActivity().finishAffinity(),
                                null
                        );
                    }
                }
        );
    }

    private void setupClickListeners() {
        tvViewAll.setOnClickListener(v -> openAllActivities());
    }

    private void registerListeners() {
        UserSession.getInstance().addListener(this);
        UserSession.getInstance().addActivityListener(this);
    }

    private void unregisterListeners() {
        // intentionally empty
    }

    // --- Data Operations ---

    private void startShimmerInitialLoad(View scrollView) {
        ShimmerHelper.show(shimmerLayout, scrollView);
        handler.postDelayed(() -> {
            loadAllData();
            ShimmerHelper.hide(shimmerLayout, scrollView);
        }, 1200);
    }

    private void loadAllData() {
        try {
            loadUserData();
            loadActivityHistory();
            updateDateTime();
        } catch (Exception e) {
            Log.e("HomeFragment", "Error loading data", e);
        }
    }

    public void loadUserData() {
        String username = Optional.ofNullable(UserSession.getInstance().getUsername())
                .orElse(getString(R.string.title_guest));

        try {
            UserSession.UserData userData = UserSession.getInstance().getUserData(username);
            if (userData != null && getContext() != null) {
                JSONObject profileJson = UserStorageManager.getInstance(getContext())
                        .loadUserProfile(userData.userId);
                if (profileJson != null) username = profileJson.optString("username", username);
            }
        } catch (Exception e) {
            Log.e("HomeFragment", "Error loading profile", e);
        }

        tvUsername.setText(username);
        updateGreeting();

        UserSession.getInstance().setFirstLoginIfNotExists();
        tvActiveDays.setText(getString(R.string.active_days_format,
                UserSession.getInstance().getActiveDays()));
    }

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res = (hour < 12) ? R.string.greeting_morning :
                (hour < 15) ? R.string.greeting_afternoon :
                        (hour < 19) ? R.string.greeting_evening :
                                R.string.greeting_night;
        tvGreeting.setText(getString(res));
    }

    private void updateDateTime() {
        tvDateTime.setText(dateFormat.format(new Date()));
    }

    private void loadActivityHistory() {
        activityList.clear();
        activityList.addAll(UserSession.getInstance().getActivities());

        if (activityList.isEmpty() && UserSession.getInstance().isLoggedIn())
            UserSession.getInstance().addLoginActivity();

        refreshActivityList();
    }

    private void refreshActivityList() {
        activityContainer.removeAllViews();

        if (activityList.isEmpty()) {
            emptyActivity.setVisibility(View.VISIBLE);
            return;
        }

        emptyActivity.setVisibility(View.GONE);
        int count = Math.min(activityList.size(), 5);

        for (int i = 0; i < count; i++)
            activityContainer.addView(createActivityView(activityList.get(i)));
    }

    private View createActivityView(ActivityItem item) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_activity, activityContainer, false);

        ((TextView) view.findViewById(R.id.tvActivityTitle))
                .setText(getSafeText(item.getTitleResId()));
        ((TextView) view.findViewById(R.id.tvActivityDesc))
                .setText(getSafeText(item.getDescriptionResId()));
        ((TextView) view.findViewById(R.id.tvActivityTime))
                .setText(getRelativeTime(item.getTimestamp()));

        return view;
    }

    private String getSafeText(int res) {
        try { return res != 0 ? getString(res) : ""; }
        catch (Resources.NotFoundException e) { return ""; }
    }

    private String getRelativeTime(long t) {
        long diff = System.currentTimeMillis() - t;
        long minutes = diff / 60000, hours = diff / 3600000, days = diff / 86400000;

        if (minutes < 1) return getString(R.string.time_just_now);
        if (minutes < 60) return getString(R.string.time_minutes_ago, minutes);
        if (hours < 24) return getString(R.string.time_hours_ago, hours);
        return getString(R.string.time_days_ago, days);
    }

    private void openAllActivities() {
        startActivity(new Intent(requireContext(), NotificationActivity.class));
        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).hideNotificationBadge();
    }

    // --- Listeners ---

    @Override
    public void onProfileUpdated() { loadUserData(); }

    @Override
    public void onNewActivity(ActivityItem item) {
        if (item == null) return;

        activityList.add(0, item);
        if (activityList.size() > 10) activityList.subList(10, activityList.size()).clear();
        refreshActivityList();

        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).showNotificationBadge();
    }

    // --- Refreshable ---

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            getActivity().setTitle(getString(R.string.menu_title_home));
        }
    }

    @Override
    public void onRefreshRequested() {
        View scroll = requireView().findViewById(R.id.scrollViewHome);
        ShimmerHelper.show(shimmerLayout, scroll);

        handler.postDelayed(() -> {
            try {
                loadAllData();
                Toast.makeText(requireContext(), R.string.toast_home_updated, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e("HomeFragment", "Refresh error", e);
            } finally {
                ShimmerHelper.hide(shimmerLayout, scroll);
                swipeRefreshLayout.setRefreshing(false);
            }
        }, 1200);
    }
}
