package com.android.alpha.ui.main;

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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.airbnb.lottie.LottieAnimationView;
import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.common.Refreshable;
import com.android.alpha.ui.notifications.ActivityItem;
import com.android.alpha.ui.map.MapFragment;
import com.android.alpha.utils.DialogUtils;
import com.android.alpha.utils.ShimmerHelper;
import com.bumptech.glide.Glide;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.yalantis.ucrop.UCrop;

import org.json.JSONObject;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;

public class ProfileFragment extends Fragment implements
        MainActivity.ToolbarTitleProvider,
        Refreshable {

    // === CONSTANTS ===
    private static final String TAG = "ProfileFragment";

    // === UI COMPONENTS ===
    private ImageView ivProfile, ivBackground;
    private LottieAnimationView lottieProfile;
    private FloatingActionButton fabEditProfile, fabEditBg;
    private ImageButton ibEditName, ibEditEmail, ibEditBirthday, ibEditLocation;
    private TextView tvProfileName, tvProfileEmail, tvFullName, tvEmail, tvBirthday, tvLocation;
    private ShimmerFrameLayout shimmerLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View scrollViewProfile;

    // === STATE & LAUNCHERS ===
    private boolean currentCropIsProfile;
    private ActivityResultLauncher<Intent> profilePicLauncher, bgPicLauncher;
    private final ActivityResultLauncher<Intent> cropLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> handleCropResult(result.getResultCode(), result.getData())
    );

    // === FRAGMENT LIFECYCLE ===
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

    @Override
    public int getToolbarTitleRes() {
        return R.string.menu_title_profile;
    }

    // === INITIALIZATION ===
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
        profilePicLauncher = createImageLauncher(true);
        bgPicLauncher = createImageLauncher(false);
    }

    private ActivityResultLauncher<Intent> createImageLauncher(boolean isProfile) {
        return registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri selectedUri = result.getData().getData();
                if (selectedUri != null) startCrop(selectedUri, isProfile);
            }
        });
    }

    private void setupListeners() {
        fabEditProfile.setOnClickListener(v -> selectPhoto(profilePicLauncher));
        fabEditBg.setOnClickListener(v -> selectPhoto(bgPicLauncher));
        ibEditName.setOnClickListener(v -> editText("username", tvFullName));
        ibEditEmail.setOnClickListener(v -> editText("email", tvEmail));
        ibEditBirthday.setOnClickListener(v -> pickDate());
        ibEditLocation.setOnClickListener(v -> openMapPicker());
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::onRefreshRequested);
    }

    // === PROFILE DATA LOADING ===
    public void refreshProfileData() {
        JSONObject json = UserSession.getInstance().loadCurrentProfileJson();
        String username = UserSession.getInstance().getUsername();
        String fullName = username != null ? username : getString(R.string.default_username);

        if (json != null) {
            fullName = json.optString("username", fullName);
            tvEmail.setText(json.optString("email", fullName + "@example.com"));
            tvBirthday.setText(json.optString("birthday", getString(R.string.default_birthday)));
            tvLocation.setText(json.optString("location", getString(R.string.default_location)));

            loadImage(json.optString("photoPath"), ivProfile, true);
            loadImage(json.optString("backgroundPath"), ivBackground, false);
        }

        tvProfileName.setText(fullName);
        tvFullName.setText(fullName);
        tvProfileEmail.setText(tvEmail.getText());
    }

    private void loadImage(String path, ImageView target, boolean isProfile) {
        if (path == null || path.trim().isEmpty()) {
            if (isProfile) {
                showLottieProfile();
            } else {
                target.setVisibility(View.GONE); // ⬅️ biar gradient kelihatan
            }
            return;
        }

        Uri uri = Uri.parse(path);
        String realPath = uri.getPath();
        if (realPath == null) {
            Log.w(TAG, "Invalid path: " + path);
            return;
        }

        File file = new File(realPath);
        if (!file.exists()) {
            Log.w(TAG, "Profile image file missing: " + file.getAbsolutePath());
            if (isProfile) {
                target.setImageResource(R.drawable.ic_person);
            } else {
                target.setVisibility(View.GONE);
            }
            return;
        }

        if (isProfile) {
            Glide.with(this).load(file).circleCrop().into(target);
            hideLottieProfile();
        } else {
            target.setVisibility(View.VISIBLE);
            Glide.with(this).load(file).into(target);
        }
    }

    private void showLottieProfile() {
        ivProfile.setVisibility(View.GONE);
        lottieProfile.setVisibility(View.VISIBLE);
        lottieProfile.setAnimation(R.raw.profile_animation);
        lottieProfile.playAnimation();
    }

    private void hideLottieProfile() {
        ivProfile.setVisibility(View.VISIBLE);
        lottieProfile.setVisibility(View.GONE);
    }

    // === PHOTO SELECTION & CROP ===
    private void selectPhoto(ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        launcher.launch(intent);
    }

    private void startCrop(Uri sourceUri, boolean isProfile) {
        currentCropIsProfile = isProfile;

        File directory = new File(requireContext().getFilesDir(), "profile");
        if (!directory.exists() && !directory.mkdirs()) {
            Log.e(TAG, "Failed to create profile directory");
            return;
        }

        File outFile = new File(directory, isProfile ? "profile_cropped.jpg" : "bg_cropped.jpg");
        Uri destinationUri = Uri.fromFile(outFile);

        UCrop.Options options = new UCrop.Options();
        options.setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG);
        options.setCompressionQuality(90);

        if (isProfile) options.setCircleDimmedLayer(true);

        UCrop uCrop = UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(isProfile ? 1 : 16, isProfile ? 1 : 9)
                .withOptions(options);

        cropLauncher.launch(uCrop.getIntent(requireContext()));
    }

    private void handleCropResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null) {
                String key = currentCropIsProfile ? "photoPath" : "backgroundPath";

                try {
                    saveProfileSafely(key, resultUri.toString());

                    if (currentCropIsProfile) {
                        Glide.with(this).load(resultUri).circleCrop().into(ivProfile);
                        hideLottieProfile();
                    } else {
                        Glide.with(this).load(resultUri).into(ivBackground);
                    }

                    notifyChange();

                } catch (Exception e) {
                    Log.e(TAG, "Crop image error", e);
                    Toast.makeText(requireContext(), R.string.error_saving_data, Toast.LENGTH_SHORT).show();
                }

            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            Throwable cropError = (data != null) ? UCrop.getError(data) : new Exception("Unknown crop error, data is null");
            Log.e(TAG, "Crop error", cropError);
            Toast.makeText(requireContext(), R.string.error_crop_image, Toast.LENGTH_SHORT).show();
        }
    }

    // === PROFILE EDITING ===
    private void saveProfileSafely(String key, String value) {
        try {
            UserSession.getInstance().saveProfileData(key, value);
        } catch (Exception e) {
            Log.e(TAG, "Error saving profile data: " + key, e);
            Toast.makeText(requireContext(), R.string.error_saving_data, Toast.LENGTH_SHORT).show();
        }
    }

    private void editText(String key, TextView target) {
        String label;
        switch (key) {
            case "username":
                label = getString(R.string.field_full_name);
                break;
            case "email":
                label = getString(R.string.field_email);
                break;
            case "birthday":
                label = getString(R.string.field_birthday);
                break;
            case "location":
                label = getString(R.string.field_location);
                break;
            default:
                label = key; // Fallback, though ideally shouldn't happen

                break;
        }

        DialogUtils.showInputDialog(requireContext(),
                getString(R.string.edit_dialog_title, label),
                getString(R.string.edit_dialog_hint, label),
                target.getText().toString(),
                getString(R.string.action_save),
                getString(R.string.action_cancel),
                newText -> {
                    target.setText(newText);
                    saveProfileSafely(key, newText);
                    notifyChange();
                });
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (view, y, m, d) -> {
            String date = String.format(Locale.getDefault(), "%s %d, %d", getMonth(m), d, y);
            tvBirthday.setText(date);
            saveProfileSafely("birthday", date);
            notifyChange();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void openMapPicker() {
        MapFragment map = new MapFragment();
        map.setOnLocationSelectedListener(loc -> {
            tvLocation.setText(loc);
            saveProfileSafely("location", loc);
            notifyChange();
        });

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, map)
                .addToBackStack(null)
                .commit();
    }

    // === UTILITIES & NOTIFICATION ===
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

        UserSession.getInstance().notifyProfileUpdated();

        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).showNotificationBadge();
    }

    // === REFRESHABLE ===
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