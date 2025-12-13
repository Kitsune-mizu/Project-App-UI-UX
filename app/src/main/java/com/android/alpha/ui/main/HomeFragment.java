package com.android.alpha.ui.main;

import android.content.Intent;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.android.alpha.R;
import com.android.alpha.data.local.UserStorageManager;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.common.Refreshable;
import com.android.alpha.ui.notes.EditNoteActivity;
import com.android.alpha.ui.notes.Note;
import com.android.alpha.ui.notes.NoteActivity;
import com.android.alpha.ui.notes.NoteAdapter;
import com.android.alpha.ui.notes.NoteViewModel;
import com.android.alpha.ui.notifications.ActivityItem;
import com.android.alpha.ui.notifications.NotificationActivity;
import com.android.alpha.ui.notifications.NotificationAdapter;
import com.android.alpha.utils.DialogUtils;
import com.android.alpha.utils.ShimmerHelper;
import com.facebook.shimmer.ShimmerFrameLayout;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

public class HomeFragment extends Fragment implements
        MainActivity.ToolbarTitleProvider,
        UserSession.UserSessionListener,
        UserSession.ActivityListener,
        UserSession.ActivityClearedListener,
        Refreshable {

    // === CONSTANTS ===
    private final String TAG = "HomeFragment";

    // === VIEWS ===
    private ShimmerFrameLayout shimmerLayout;
    private LottieAnimationView lottieWelcome;
    private TextView tvGreeting, tvUsername, tvDateTime, tvActiveDays, tvViewAll;
    private RecyclerView recyclerViewActivities;
    private NotificationAdapter activityAdapter;
    private LinearLayout emptyActivity;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvViewAllNotes;
    private RecyclerView recyclerViewNotes;
    private LinearLayout emptyNotes;

    // === DATA & ADAPTERS ===
    private NoteViewModel noteViewModel;
    private NoteAdapter adapter;
    private final List<ActivityItem> activityList = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("EEEE, d MMMM yyyy • HH:mm", Locale.getDefault());

    // === LAUNCHERS ===
    private ActivityResultLauncher<Intent> noteLauncher;

    // === RUNNABLE ===
    private final Runnable timeUpdater = new Runnable() {
        @Override
        public void run() {
            updateDateTime();
            handler.postDelayed(this, 60000); // Updates every 1 minute
        }
    };

    // === FRAGMENT LIFECYCLE ===
    public HomeFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        initUIComponents();
        registerListeners();

        setupActivityRecycler();
        setupRecyclerView();
        setupNoteLauncher();
        startShimmerInitialLoad(view.findViewById(R.id.scrollViewHome));
        handler.post(timeUpdater);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            loadNotes();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        UserSession.getInstance().setActivityClearedListener(null);
        unregisterListeners();
    }

    @Override
    public int getToolbarTitleRes() {
        return R.string.menu_title_home;
    }

    // === INITIALIZATION ===
    private void initializeViews(View view) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        shimmerLayout = view.findViewById(R.id.shimmerLayout);
        lottieWelcome = view.findViewById(R.id.lottieWelcome);
        tvGreeting = view.findViewById(R.id.tvGreeting);
        tvUsername = view.findViewById(R.id.tvUsername);
        tvDateTime = view.findViewById(R.id.tvDateTime);
        tvActiveDays = view.findViewById(R.id.tvActiveDays);
        tvViewAll = view.findViewById(R.id.tvViewAll);
        recyclerViewActivities = view.findViewById(R.id.recyclerViewActivities);
        emptyActivity = view.findViewById(R.id.emptyActivity);
        tvViewAllNotes = view.findViewById(R.id.tvViewAllNotes);
        recyclerViewNotes = view.findViewById(R.id.recyclerViewNotes);
        emptyNotes = view.findViewById(R.id.emptyNotes);
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
        tvViewAllNotes.setOnClickListener(v -> openAllNotes());
    }

    private void registerListeners() {
        UserSession.getInstance().addListener(this);
        UserSession.getInstance().addActivityListener(this);
        UserSession.getInstance().setActivityClearedListener(this);
    }

    private void unregisterListeners() {
        // Placeholder for cleanup if needed
    }

    @Override
    public void onActivitiesCleared() {
        activityList.clear();
        refreshActivityList();
    }

    // === NOTES & RECYCLERVIEW MANAGEMENT ===
    private void setupRecyclerView() {
        adapter = new NoteAdapter(new ArrayList<>(), note -> openNoteDetail(note.getId()),
                new NoteAdapter.OnSelectionModeListener() {
                    @Override public void onSelectionModeChange(boolean active) {}
                    @Override public void onSelectionCountChange(int count) {}
                },
                true,
                false
        );

        LinearLayoutManager layoutManager =
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        recyclerViewNotes.setLayoutManager(layoutManager);
        recyclerViewNotes.setHasFixedSize(false);
        recyclerViewNotes.setAdapter(adapter);

        noteViewModel = new ViewModelProvider(requireActivity()).get(NoteViewModel.class);

        String userId = UserSession.getInstance()
                .getUserData(UserSession.getInstance().getUsername()).userId;

        noteViewModel.setUserId(userId);
        loadNotes();

        noteViewModel.getActiveNotes().observe(getViewLifecycleOwner(), notes -> {
            if (notes == null) return;

            List<Note> sorted = new ArrayList<>(notes);
            sorted.sort((n1, n2) -> Long.compare(n2.getTimestamp(), n1.getTimestamp()));

            adapter.updateNotes(sorted);

            if (sorted.isEmpty()) {
                emptyNotes.setVisibility(View.VISIBLE);
                recyclerViewNotes.setVisibility(View.GONE);
            } else {
                emptyNotes.setVisibility(View.GONE);
                recyclerViewNotes.setVisibility(View.VISIBLE);
            }
        });
    }

    public void refreshNotes() {
        if (noteViewModel != null) {
            noteViewModel.loadNotes(requireContext());
        }
    }

    private void loadNotes() {
        noteViewModel.loadNotes(requireContext());
        noteViewModel.refreshNotes(requireContext());
    }

    private void setupNoteLauncher() {
        noteLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> loadNotes()
        );
    }

    private void openNoteDetail(String noteId) {
        Note selectedNote = noteViewModel.getNoteById(requireContext(), noteId);
        Intent intent = new Intent(requireContext(), EditNoteActivity.class);
        intent.putExtra("note_id", selectedNote.getId());
        noteLauncher.launch(intent);
    }

    private void openAllNotes() {
        Intent intent = new Intent(requireContext(), NoteActivity.class);
        startActivity(intent);
    }

    // === DATA LOADING & SHIMMER ===
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
            Log.e(TAG, "Error loading data", e);
        }
    }

    // === USER DATA & GREETING ===
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
            Log.e(TAG, "Error loading profile", e);
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

    // === ACTIVITY HISTORY ===
    private void loadActivityHistory() {
        activityList.clear();
        activityList.addAll(UserSession.getInstance().getActivities());

        if (!UserSession.getInstance().hasAddedLoginActivity() && UserSession.getInstance().isLoggedIn()) {
            UserSession.getInstance().addLoginActivity();
            UserSession.getInstance().setAddedLoginActivity(true);
        }

        refreshActivityList();
    }

    private void setupActivityRecycler() {
        activityAdapter = new NotificationAdapter();

        recyclerViewActivities.setLayoutManager(
                new LinearLayoutManager(requireContext())
        );
        recyclerViewActivities.setNestedScrollingEnabled(false);
        recyclerViewActivities.setAdapter(activityAdapter);
    }

    private void refreshActivityList() {
        if (activityList.isEmpty()) {
            emptyActivity.setVisibility(View.VISIBLE);
            recyclerViewActivities.setVisibility(View.GONE);
            return;
        }

        emptyActivity.setVisibility(View.GONE);
        recyclerViewActivities.setVisibility(View.VISIBLE);

        List<ActivityItem> limited =
                activityList.subList(0, Math.min(activityList.size(), 5));

        activityAdapter.submitList(new ArrayList<>(limited));
    }

    private void openAllActivities() {
        startActivity(new Intent(requireContext(), NotificationActivity.class));
        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).hideNotificationBadge();
    }

    // === USER-SESSION LISTENERS ===
    @Override
    public void onProfileUpdated() {
        loadUserData();
    }

    @Override
    public void onNewActivity(ActivityItem item) {
        if (item == null) return;

        activityList.add(0, item);
        if (activityList.size() > 10) activityList.subList(10, activityList.size()).clear();
        refreshActivityList();

        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).showNotificationBadge();
    }

    // === REFRESHABLE ===
    @Override
    public void onRefreshRequested() {
        View scroll = requireView().findViewById(R.id.scrollViewHome);
        ShimmerHelper.show(shimmerLayout, scroll);

        handler.postDelayed(() -> {
            try {
                noteViewModel.refreshNotes(requireContext());
                loadAllData();
                Toast.makeText(requireContext(), R.string.toast_home_updated, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Refresh error", e);
            } finally {
                ShimmerHelper.hide(shimmerLayout, scroll);
                swipeRefreshLayout.setRefreshing(false);
            }
        }, 1200);
    }
}