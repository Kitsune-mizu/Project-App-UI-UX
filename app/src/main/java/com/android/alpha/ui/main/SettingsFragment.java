package com.android.alpha.ui.main;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.*;
import android.app.Activity;

import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.auth.ForgotPasswordActivity;
import com.android.alpha.ui.auth.LoginActivity;
import com.android.alpha.utils.DialogUtils;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsFragment extends Fragment implements
        MainActivity.ToolbarTitleProvider {

    // === CONSTANTS ===
    // No constants defined here in the original code, except for system generated ones.

    // === UI COMPONENTS ===
    private SwitchMaterial switchNotifications;
    private TextView textCurrentLanguage;

    // === UTILITIES ===
    private SharedPreferences prefs;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    public SettingsFragment() {}

    // === LIFECYCLE ===
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        switchNotifications = view.findViewById(R.id.switchNotifications);
        textCurrentLanguage = view.findViewById(R.id.textCurrentLanguage);

        setupPermissionLauncher();
        loadSettings();
        setupClickListeners(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLanguageDisplay();
        checkSystemNotificationStatus();
    }

    @Override
    public int getToolbarTitleRes() {
        return R.string.menu_title_settings;
    }

    // === INITIALIZATION & SETUP ===
    private void loadSettings() {
        switchNotifications.setChecked(prefs.getBoolean("notifications_enabled", true));
    }

    private void setupClickListeners(View view) {
        view.findViewById(R.id.layoutProfile).setOnClickListener(v -> navigateToProfile());
        view.findViewById(R.id.layoutForgotPassword).setOnClickListener(v -> openForgotPassword());
        view.findViewById(R.id.layoutLogout).setOnClickListener(v -> showLogoutConfirmation());
        view.findViewById(R.id.layoutDeleteAccount).setOnClickListener(v -> showDeleteAccountWarnings());
        view.findViewById(R.id.layoutLanguage).setOnClickListener(v -> showLanguageDialog());

        switchNotifications.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) {
                disableNotifications();
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                enableNotifications();
            }
        });
    }

    // === UI DISPLAY ===
    private void updateLanguageDisplay() {
        String lang = UserSession.getInstance().getLanguage();
        int flagRes, textRes;

        switch (lang) {
            case "id": flagRes = R.drawable.flag_id; textRes = R.string.lang_indonesia; break;
            case "ja": flagRes = R.drawable.flag_ja; textRes = R.string.lang_japanese; break;
            case "ko": flagRes = R.drawable.flag_ko; textRes = R.string.lang_korean; break;
            default:   flagRes = R.drawable.flag_globe; textRes = R.string.lang_english; break;
        }

        Drawable flag = ContextCompat.getDrawable(requireContext(), flagRes);
        int size = (int) (textCurrentLanguage.getLineHeight() * 1.2f);
        if (flag != null) flag.setBounds(0, 0, size, size);

        textCurrentLanguage.setText(getString(textRes));
        textCurrentLanguage.setCompoundDrawables(null, null, flag, null);
        textCurrentLanguage.setCompoundDrawablePadding(12);
    }

    // === PERMISSION HANDLING ===
    private void setupPermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) enableNotifications();
                    else {
                        switchNotifications.setChecked(false);
                        DialogUtils.showConfirmDialog(
                                requireContext(),
                                getString(R.string.dialog_permission_title),
                                getString(R.string.dialog_notification_permission_msg),
                                getString(R.string.action_open_settings),
                                getString(R.string.action_cancel),
                                this::openAppSettings,
                                null
                        );
                    }
                }
        );
    }

    // === LANGUAGE DIALOG ===
    private void showLanguageDialog() {
        String[] names = getResources().getStringArray(R.array.language_names);
        String[] codes = {"en", "id", "ja", "ko"};
        int[] icons = {R.drawable.flag_globe, R.drawable.flag_id, R.drawable.flag_ja, R.drawable.flag_ko};

        var dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(
                requireContext(), R.style.ModernBottomSheetDialog);

        View sheet = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottomsheet_language_picker, new FrameLayout(requireContext()), false);

        ViewGroup container = sheet.findViewById(R.id.languageContainer);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        String current = UserSession.getInstance().getLanguage();

        for (int i = 0; i < names.length; i++) {
            View item = inflater.inflate(R.layout.item_language_option, container, false);
            ((TextView) item.findViewById(R.id.tvLanguageName)).setText(names[i]);
            ((ImageView) item.findViewById(R.id.imgFlag)).setImageResource(icons[i]);
            if (codes[i].equals(current)) item.findViewById(R.id.iconCheck).setVisibility(View.VISIBLE);

            int index = i;
            item.setOnClickListener(v -> onLanguageSelected(dialog, codes[index]));
            container.addView(item);
        }

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void onLanguageSelected(com.google.android.material.bottomsheet.BottomSheetDialog dialog, String code) {
        UserSession.getInstance().setLanguage(code);
        Activity activity = getActivity();
        dialog.dismiss();

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).getSupportFragmentManager()
                    .popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (activity != null && !activity.isFinishing()) activity.recreate();
        }, 250);
    }

    // === NAVIGATION & AUTHENTICATION ACTIONS ===
    private void navigateToProfile() {
        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).showFragment(new ProfileFragment(), "Profile", true);
    }

    private void openForgotPassword() {
        startActivity(new Intent(requireContext(), ForgotPasswordActivity.class));
    }

    private void showLogoutConfirmation() {
        DialogUtils.showConfirmDialog(
                requireContext(),
                getString(R.string.dialog_logout_title),
                getString(R.string.dialog_logout_msg),
                getString(R.string.action_logout),
                getString(R.string.action_cancel),
                () -> {
                    if (getActivity() instanceof MainActivity)
                        ((MainActivity) getActivity()).logout();
                },
                null
        );
    }

    // === DELETE ACCOUNT PROCESS ===
    private void showDeleteAccountWarnings() {
        showWarningStep(1);
    }

    private void showWarningStep(int step) {
        String title = "", msg = "";
        switch (step) {
            case 1: title = getString(R.string.warn_delete_title_1); msg = getString(R.string.warn_delete_msg_1); break;
            case 2: title = getString(R.string.warn_delete_title_2); msg = getString(R.string.warn_delete_msg_2); break;
            case 3: title = getString(R.string.warn_delete_title_3); msg = getString(R.string.warn_delete_msg_3); break;
        }

        DialogUtils.showCountdownDialog(
                requireContext(), title, msg,
                getString(R.string.action_next), getString(R.string.action_cancel),
                5,
                () -> { if (step < 3) showWarningStep(step + 1); else showFinalDeleteConfirmation(); }
        );
    }

    private void showFinalDeleteConfirmation() {
        DialogUtils.showConfirmDialog(
                requireContext(),
                getString(R.string.dialog_delete_title),
                getString(R.string.dialog_delete_msg),
                getString(R.string.action_delete),
                getString(R.string.action_cancel),
                () -> {
                    UserSession s = UserSession.getInstance();
                    if (s.deleteAccount(s.getUsername())) {
                        Toast.makeText(requireContext(), R.string.toast_account_deleted, Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(requireContext(), LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        requireActivity().finish();
                    } else {
                        Toast.makeText(requireContext(), R.string.toast_delete_failed, Toast.LENGTH_SHORT).show();
                    }
                },
                null
        );
    }

    // === NOTIFICATIONS SETTINGS ===
    private void checkSystemNotificationStatus() {
        NotificationManager manager = (NotificationManager)
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE);

        boolean enabled = manager != null && manager.areNotificationsEnabled();
        prefs.edit().putBoolean("notifications_enabled", enabled).apply();
        switchNotifications.setChecked(enabled);
    }

    private void enableNotifications() {
        prefs.edit().putBoolean("notifications_enabled", true).apply();
        Toast.makeText(requireContext(), R.string.toast_notifications_enabled, Toast.LENGTH_SHORT).show();
    }

    private void disableNotifications() {
        prefs.edit().putBoolean("notifications_enabled", false).apply();
        Toast.makeText(requireContext(), R.string.toast_notifications_disabled, Toast.LENGTH_SHORT).show();
    }

    private void openAppSettings() {
        Intent intent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName())
                : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + requireContext().getPackageName()));

        startActivity(intent);
    }
}