package com.android.alpha.ui.profile;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.airbnb.lottie.*;
import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.common.Refreshable;
import com.android.alpha.ui.home.ActivityItem;
import com.android.alpha.ui.main.MainActivity;
import com.android.alpha.ui.map.MapFragment;
import com.android.alpha.utils.DialogUtils;
import com.android.alpha.utils.ShimmerHelper;
import com.bumptech.glide.Glide;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

public class ProfileFragment extends Fragment implements Refreshable {

    private ImageView ivProfile, ivBackground;
    private LottieAnimationView lottieProfile;
    private FloatingActionButton fabEditProfile, fabEditBg;
    private ImageButton ibEditName, ibEditEmail, ibEditBirthday, ibEditLocation;
    private TextView tvProfileName, tvProfileEmail, tvFullName, tvEmail, tvBirthday, tvLocation;
    private ShimmerFrameLayout shimmerLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View scrollViewProfile;

    private ActivityResultLauncher<Intent> profilePicLauncher, bgPicLauncher;

    private static final String TAG = "ProfileFragment";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_profile, container, false);

        initializeViews(v);
        setupLaunchers();
        setupListeners();
        setupSwipeRefresh();

        ShimmerHelper.show(shimmerLayout, scrollViewProfile);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            refreshProfileData();
            ShimmerHelper.hide(shimmerLayout, scrollViewProfile);
        }, 1200);

        return v;
    }

    private void initializeViews(View v) {
        swipeRefreshLayout = v.findViewById(R.id.swipeRefreshLayout);
        shimmerLayout = v.findViewById(R.id.shimmerLayout);
        scrollViewProfile = v.findViewById(R.id.scrollViewProfile);

        ivProfile = v.findViewById(R.id.ivProfile);
        ivBackground = v.findViewById(R.id.ivBackground);
        lottieProfile = v.findViewById(R.id.lottieProfile);

        fabEditProfile = v.findViewById(R.id.fabEditProfile);
        fabEditBg = v.findViewById(R.id.fabEditBg);

        ibEditName = v.findViewById(R.id.ibEditName);
        ibEditEmail = v.findViewById(R.id.ibEditEmail);
        ibEditBirthday = v.findViewById(R.id.ibEditBirthday);
        ibEditLocation = v.findViewById(R.id.ibEditLocation);

        tvProfileName = v.findViewById(R.id.tvProfileName);
        tvProfileEmail = v.findViewById(R.id.tvProfileEmail);
        tvFullName = v.findViewById(R.id.tvFullName);
        tvEmail = v.findViewById(R.id.tvEmail);
        tvBirthday = v.findViewById(R.id.tvBirthday);
        tvLocation = v.findViewById(R.id.tvLocation);
    }

    private void setupLaunchers() {

        profilePicLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleImageSelection(result, "photoPath", true));

        bgPicLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleImageSelection(result, "backgroundPath", false));
    }

    private void handleImageSelection(ActivityResult result, String key, boolean reloadProfile) {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Uri img = result.getData().getData();
            if (img != null) {
                try {
                    UserSession.getInstance().saveProfileData(key, img.toString());
                    if (reloadProfile) refreshProfileData();
                    else Glide.with(this).load(img).into(ivBackground);

                    notifyChange();
                    Log.d(TAG, key + " updated successfully");

                } catch (Exception e) {
                    Log.e(TAG, "Error saving image: " + e.getMessage(), e);
                    Toast.makeText(requireContext(), R.string.error_saving_data, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void setupListeners() {
        fabEditProfile.setOnClickListener(v -> selectImage(profilePicLauncher));
        fabEditBg.setOnClickListener(v -> selectImage(bgPicLauncher));
        ibEditName.setOnClickListener(v -> editText("username", tvFullName));
        ibEditEmail.setOnClickListener(v -> editText("email", tvEmail));
        ibEditBirthday.setOnClickListener(v -> pickDate());
        ibEditLocation.setOnClickListener(v -> openMapPicker());
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::onRefreshRequested);
    }

    public void refreshProfileData() {
        JSONObject json = UserSession.getInstance().loadCurrentProfileJson();

        String username = UserSession.getInstance().getUsername();
        String defaultName = getString(R.string.default_username);
        String fullName = username != null ? username : defaultName;

        if (json != null) {
            fullName = json.optString("username", fullName);
            tvEmail.setText(json.optString("email", fullName + "@example.com"));
            tvBirthday.setText(json.optString("birthday", getString(R.string.default_birthday)));
            tvLocation.setText(json.optString("location", getString(R.string.default_location)));
            loadProfileImage(json.optString("photoPath"));
            loadBackgroundImage(json.optString("backgroundPath"));
        }

        tvProfileName.setText(fullName);
        tvFullName.setText(fullName);
        tvProfileEmail.setText(tvEmail.getText());
    }

    private void loadProfileImage(String pic) {
        if (pic != null && !pic.isEmpty()) {
            ivProfile.setVisibility(View.VISIBLE);
            lottieProfile.setVisibility(View.GONE);
            Glide.with(this).load(pic).circleCrop().into(ivProfile);
        } else {
            ivProfile.setVisibility(View.GONE);
            lottieProfile.setVisibility(View.VISIBLE);
            lottieProfile.setAnimation(R.raw.profile_animation);
            lottieProfile.setRepeatCount(LottieDrawable.INFINITE);
            lottieProfile.playAnimation();
        }
    }

    private void loadBackgroundImage(String bg) {
        if (bg != null && !bg.isEmpty()) Glide.with(this).load(bg).into(ivBackground);
        else ivBackground.setImageResource(R.color.md_theme_light_surface);
    }

    private void selectImage(ActivityResultLauncher<Intent> launcher) {
        launcher.launch(new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
    }

    private void editText(String key, TextView target) {
        String label = key.equals("username") ? getString(R.string.field_full_name)
                : key.equals("email") ? getString(R.string.field_email)
                : key;

        DialogUtils.showInputDialog(requireContext(),
                getString(R.string.edit_dialog_title, label),
                getString(R.string.edit_dialog_hint, label),
                target.getText().toString(),
                getString(R.string.action_save),
                getString(R.string.action_cancel),
                newText -> {
                    target.setText(newText);
                    try {
                        UserSession.getInstance().saveProfileData(key, newText);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    notifyChange();
                });
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        DatePickerDialog dpd = new DatePickerDialog(requireContext(),
                (view, y, m, d) -> {
                    String date = String.format(Locale.getDefault(), "%s %d, %d", getMonth(m), d, y);
                    tvBirthday.setText(date);
                    try {
                        UserSession.getInstance().saveProfileData("birthday", date);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    notifyChange();
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dpd.show();
    }

    private void openMapPicker() {
        MapFragment map = new MapFragment();
        map.setOnLocationSelectedListener(loc -> {
            tvLocation.setText(loc);
            try {
                UserSession.getInstance().saveProfileData("location", loc);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            notifyChange();
        });

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, map)
                .addToBackStack(null)
                .commit();
    }

    private String getMonth(int m) {
        return new java.text.DateFormatSymbols().getMonths()[m];
    }

    private void notifyChange() {
        String username = UserSession.getInstance().getUsername();
        if (username == null || username.isEmpty()) return;

        UserSession.UserData data = UserSession.getInstance().getUserData(username);
        if (data == null) return;

        UserSession.getInstance().addActivity(new ActivityItem(
                R.string.activity_profile_updated_title,
                R.string.activity_profile_updated_desc,
                System.currentTimeMillis(),
                R.drawable.ic_person,
                ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary),
                data.userId
        ));

        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).showNotificationBadge();
    }

    @Override
    public void onRefreshRequested() {
        ShimmerHelper.show(shimmerLayout, scrollViewProfile);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            refreshProfileData();
            Toast.makeText(requireContext(), R.string.toast_profile_updated, Toast.LENGTH_SHORT).show();
            swipeRefreshLayout.setRefreshing(false);
            ShimmerHelper.hide(shimmerLayout, scrollViewProfile);
        }, 1200);
    }
}
