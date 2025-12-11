package com.android.alpha.ui.notes;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.*;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.auth.LoginActivity;
import com.android.alpha.utils.DialogUtils;
import com.google.gson.Gson;
import yuku.ambilwarna.AmbilWarnaDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class EditNoteActivity extends AppCompatActivity {

    private static final String TAG = "EditNoteActivity";

    // ViewModel & Model
    private NoteViewModel viewModel;
    private Note currentNote;

    // UI elements
    private EditText etTitle, etContent;
    private TextView tvMetadata;

    private LinearLayout layoutDefaultActions, layoutInputActions;
    private ImageButton btnDelete, btnShare;

    // Undo/Redo
    private ImageButton btnUndo, btnRedo;
    private ImageButton btnSaveManual;
    private final Stack<String> undoStack = new Stack<>();
    private final Stack<String> redoStack = new Stack<>();
    private boolean isUpdatingText = false;

    // Formatting toolbar
    private ImageButton btnBold, btnItalic, btnUnderline, btnHighlight, btnStrike, btnTextColor;
    private ImageButton btnAlignLeft, btnAlignCenter, btnAlignRight;
    private ImageButton btnBullet;
    private ImageButton btnSizeUp, btnSizeDown;
    private boolean bulletMode = false;
    private boolean isUpdating = false;

    // Original content snapshot
    private String originalTitle = "";
    private String originalContent = "";

    // Utilities
    private final Gson gson = new Gson();
    private int currentTextColor = Color.BLACK;
    private int currentHighlightColor = Color.YELLOW;

    // --------------------------------------------------
    // Lifecycle
    // --------------------------------------------------
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_note);

        initViews();
        setupEditNote();
        initViewModel();
        loadNoteData();
        setupEditorWatcher();
        setupButtons();
        setupKeyboardToolbar();
        setupBackPressed();
        detectKeyboardVisibility();
    }

    // --------------------------------------------------
    // Initialization helpers
    // --------------------------------------------------
    private void initViews() {
        etTitle = findViewById(R.id.et_title);
        etContent = findViewById(R.id.et_content);
        tvMetadata = findViewById(R.id.tv_metadata);

        layoutDefaultActions = findViewById(R.id.layout_default_actions);
        layoutInputActions = findViewById(R.id.layout_input_actions);

        btnDelete = findViewById(R.id.btn_delete);
        btnShare = findViewById(R.id.btn_share);

        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);
        btnSaveManual = findViewById(R.id.btn_save_manual);

        btnBold = findViewById(R.id.action_bold);
        btnItalic = findViewById(R.id.action_italic);
        btnUnderline = findViewById(R.id.action_underline);
        btnHighlight = findViewById(R.id.action_highlight);
        btnStrike = findViewById(R.id.action_strike);
        btnTextColor = findViewById(R.id.action_text_color);
        btnBullet = findViewById(R.id.action_bullet_list);
        btnSizeUp = findViewById(R.id.action_size_up);
        btnSizeDown = findViewById(R.id.action_size_down);
        btnAlignLeft = findViewById(R.id.action_align_left);
        btnAlignCenter = findViewById(R.id.action_align_center);
        btnAlignRight = findViewById(R.id.action_align_right);

        btnUndo.setEnabled(false);
        btnRedo.setEnabled(false);

        // Basic linkify behavior and caret management
        etContent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable editable) {
                Linkify.addLinks(editable, Linkify.WEB_URLS);
                etContent.setMovementMethod(LinkMovementMethod.getInstance());
            }
        });
        etContent.setLinkTextColor(Color.BLUE);

        etContent.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) etContent.setCursorVisible(true);
        });

        etContent.setOnClickListener(v -> {
            etContent.setCursorVisible(true);
            etContent.requestFocus();

            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etContent, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(NoteViewModel.class);

        String userId = null;
        if (getIntent() != null) {
            userId = getIntent().getStringExtra("user_id");
        }

        if (userId == null) {
            if (UserSession.getInstance().isInitialized()) {
                userId = UserSession.getInstance()
                        .getUserData(UserSession.getInstance().getUsername()).userId;
            }
        }

        if (userId == null) {
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return;
        }

        viewModel.setUserId(userId);
    }

    private void loadNoteData() {
        String noteId = getIntent().getStringExtra("note_id");
        if (noteId != null) {
            currentNote = viewModel.getNoteById(this, noteId);
        }

        if (currentNote != null) {
            etTitle.setText(currentNote.getTitle());
            etContent.setText(Html.fromHtml(currentNote.getContent(), Html.FROM_HTML_MODE_COMPACT));

            originalTitle = etTitle.getText().toString();
            originalContent = Html.toHtml(etContent.getText(), Html.FROM_HTML_MODE_COMPACT);
        } else {
            currentNote = new Note(UUID.randomUUID().toString());
            currentNote.setTimestamp(System.currentTimeMillis());
        }
        updateMetadata();
    }

    // --------------------------------------------------
    // Editor watchers & input handling
    // --------------------------------------------------
    private void setupEditorWatcher() {
        etContent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateToolbarState(); }
            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingText) return;
                undoStack.push(s.toString());
                btnUndo.setEnabled(true);
                redoStack.clear();
                btnRedo.setEnabled(false);
                updateMetadata();

                // Inner watcher for detecting Enter bullet and bullet removal
                etContent.addTextChangedListener(new TextWatcher() {
                    private int beforeLength = 0;

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        beforeLength = s.length();
                    }

                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (isUpdating) return;

                        int cursor = etContent.getSelectionStart();

                        // 1. Detect Enter → insert bullet if in bullet mode
                        if (s.length() > beforeLength &&
                                cursor > 0 &&
                                s.charAt(cursor - 1) == '\n' &&
                                bulletMode) {

                            isUpdating = true;
                            s.insert(cursor, "• ");
                            isUpdating = false;
                            return;
                        }

                        // 2. Detect bullet removed → turn off bullet mode
                        int lineStart = s.toString().lastIndexOf('\n', cursor - 1) + 1;

                        if (bulletMode && !s.toString().startsWith("• ", lineStart)) {
                            bulletMode = false;
                        }
                    }
                });
            }
        });

        etContent.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN &&
                    keyCode == android.view.KeyEvent.KEYCODE_ENTER) {

                handleEnterWithBullet();
                return false;
            }
            return false;
        });

        etContent.setOnClickListener(v -> updateToolbarState());
        etContent.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) updateToolbarState(); });

        etContent.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
            @Override public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) { return true; }
            @Override public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) { updateToolbarState(); return true; }
            @Override public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) { return false; }
            @Override public void onDestroyActionMode(android.view.ActionMode mode) {}
        });
    }

    // --------------------------------------------------
    // Buttons & toolbar setup
    // --------------------------------------------------
    private void setupButtons() {
        findViewById(R.id.btn_back).setOnClickListener(v -> saveAndExit());
        btnShare.setVisibility(View.GONE);
        btnDelete.setVisibility(View.GONE);
        findViewById(R.id.btn_menu).setOnClickListener(this::showMenuPopup);
        btnUndo.setOnClickListener(v -> undo());
        btnRedo.setOnClickListener(v -> redo());
        btnSaveManual.setOnClickListener(v -> manualSave());
    }

    private void setupKeyboardToolbar() {
        btnBold.setOnClickListener(v -> toggleStyle(Typeface.BOLD));
        btnItalic.setOnClickListener(v -> toggleStyle(Typeface.ITALIC));
        btnUnderline.setOnClickListener(v -> toggleSpan(UnderlineSpan.class));
        btnStrike.setOnClickListener(v -> toggleSpan(StrikethroughSpan.class));

        btnHighlight.setOnClickListener(v -> openHighlightColorPicker());
        btnTextColor.setOnClickListener(v -> openTextColorPicker());

        btnAlignLeft.setOnClickListener(v -> setAlignment(Layout.Alignment.ALIGN_NORMAL));
        btnAlignCenter.setOnClickListener(v -> setAlignment(Layout.Alignment.ALIGN_CENTER));
        btnAlignRight.setOnClickListener(v -> setAlignment(Layout.Alignment.ALIGN_OPPOSITE));

        btnBullet.setOnClickListener(v -> toggleBullet());

        btnSizeUp.setOnClickListener(v -> changeTextSize(1.2f));
        btnSizeDown.setOnClickListener(v -> changeTextSize(0.8f));
    }

    // --------------------------------------------------
    // Keyboard visibility handling
    // --------------------------------------------------
    private void detectKeyboardVisibility() {
        final View contentView = findViewById(android.R.id.content);
        contentView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int heightDiff = contentView.getRootView().getHeight() - contentView.getHeight();
            boolean isKeyboardVisible = heightDiff > dpToPx();

            if (isKeyboardVisible) {
                etContent.post(() -> { etContent.requestFocus(); etContent.setCursorVisible(true); });
                hideView(layoutDefaultActions);
                showView(layoutInputActions);

            } else {
                showView(layoutDefaultActions);
                hideView(layoutInputActions);
            }

            if (isKeyboardVisible) {
                layoutDefaultActions.setVisibility(View.GONE);
                layoutInputActions.setVisibility(View.VISIBLE);
            } else {
                layoutDefaultActions.setVisibility(View.VISIBLE);
                layoutInputActions.setVisibility(View.GONE);

                btnUndo.setEnabled(!undoStack.isEmpty());
                btnRedo.setEnabled(!redoStack.isEmpty());
            }
        });
    }

    private int dpToPx() {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(200 * density);
    }

    private void showView(View v) {
        v.setAlpha(0f);
        v.setVisibility(View.VISIBLE);
        v.animate().alpha(1f).setDuration(180).start();
    }

    private void hideView(View v) {
        v.animate().alpha(0f).setDuration(180).withEndAction(() -> v.setVisibility(View.GONE)).start();
    }

    private void setupBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { saveAndExit(); }
        });
    }

    // --------------------------------------------------
    // Metadata & saving
    // --------------------------------------------------
    private void updateMetadata() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy, HH:mm", Locale.getDefault());
        long ts = currentNote.getTimestamp() == 0 ? System.currentTimeMillis() : currentNote.getTimestamp();
        tvMetadata.setText(getString(R.string.metadata_format, sdf.format(new Date(ts)), etContent.length()));
    }

    private void manualSave() {
        // Tutup keyboard
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etContent.getWindowToken(), 0);

        // Sembunyikan toolbar input
        layoutInputActions.setVisibility(View.GONE);
        layoutDefaultActions.setVisibility(View.VISIBLE);

        // Simpan seperti saveAndExit, tapi tanpa exit
        String title = etTitle.getText().toString().trim();
        String contentHtml = Html.toHtml(etContent.getText(), Html.FROM_HTML_MODE_COMPACT);
        String plainText = Html.fromHtml(contentHtml, Html.FROM_HTML_MODE_LEGACY).toString().trim();

        // Note dihapus jika kosong
        if (title.isEmpty() && plainText.isEmpty()) {
            if (currentNote != null) {
                getSharedPreferences("notes_" + viewModel.getUserId(), MODE_PRIVATE)
                        .edit()
                        .remove(currentNote.getId())
                        .apply();

                viewModel.deleteNote(this, currentNote.getId());
            }

            Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show();
            return;
        }

        // Simpan bila ada perubahan
        if (!title.equals(originalTitle) || !contentHtml.equals(originalContent)) {
            currentNote.setTitle(title);
            currentNote.setContent(contentHtml);
            currentNote.setTimestamp(System.currentTimeMillis());

            String json = gson.toJson(currentNote);
            getSharedPreferences("notes_" + viewModel.getUserId(), MODE_PRIVATE)
                    .edit()
                    .putString(currentNote.getId(), json)
                    .apply();

            viewModel.saveNote(this, currentNote);

            originalTitle = title;
            originalContent = contentHtml;

            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAndExit() {
        String title = etTitle.getText().toString().trim();
        String contentHtml = Html.toHtml(etContent.getText(), Html.FROM_HTML_MODE_COMPACT);

        String plainText = Html.fromHtml(contentHtml, Html.FROM_HTML_MODE_LEGACY).toString().trim();

        // ====== 1️⃣ AUTO DELETE NOTE JIKA ISINYA DIKOSONGKAN ======
        boolean isExistingNote = originalContent != null && currentNote != null;

        if (isExistingNote) {
            if (title.isEmpty() && plainText.isEmpty()) {
                // Hapus note dari SharedPreferences
                getSharedPreferences("notes_" + viewModel.getUserId(), MODE_PRIVATE)
                        .edit()
                        .remove(currentNote.getId())
                        .apply();

                // Hapus dari database / ViewModel
                viewModel.deleteNote(this, currentNote.getId());

                setResult(RESULT_OK);
                finish();
                return;
            }
        }

        // ====== 2️⃣ JIKA NOTE BARU DAN KOSONG → JANGAN SIMPAN ======
        if (title.isEmpty() && plainText.isEmpty()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        // ====== 3️⃣ SIMPAN NOTE JIKA ADA PERUBAHAN ======
        if (!title.equals(originalTitle) || !contentHtml.equals(originalContent)) {
            assert currentNote != null;
            currentNote.setTitle(title);
            currentNote.setContent(contentHtml);
            currentNote.setTimestamp(System.currentTimeMillis());

            String json = gson.toJson(currentNote);
            getSharedPreferences("notes_" + viewModel.getUserId(), MODE_PRIVATE)
                    .edit()
                    .putString(currentNote.getId(), json)
                    .apply();

            viewModel.saveNote(this, currentNote);
            setResult(RESULT_OK);
            finish();
            return;
        }

        setResult(RESULT_CANCELED);
        finish();
    }

    // --------------------------------------------------
    // Sharing helpers
    // --------------------------------------------------
    private Bitmap createBitmapFromView(View view) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(view.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );

        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());

        Bitmap bitmap = Bitmap.createBitmap(
                view.getMeasuredWidth(),
                view.getMeasuredHeight(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);

        return bitmap;
    }

    private Uri saveBitmapToCache(Bitmap bitmap) {
        FileOutputStream stream = null;

        try {
            File cachePath = new File(getCacheDir(), "shared_notes");

            if (!cachePath.exists()) {
                boolean created = cachePath.mkdirs();
                if (!created) {
                    Log.e("ShareNote", "Failed to create cache directory");
                    return null;
                }
            }

            String fileName = "note_share_" + System.currentTimeMillis() + ".png";
            File file = new File(cachePath, fileName);

            stream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);

            return FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    file
            );

        } catch (Exception e) {
            Log.e("ShareNote", "Error saving bitmap to cache: " + e.getMessage(), e);
            return null;

        } finally {
            if (stream != null) {
                try { stream.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void shareNoteAsImage() {

        LinearLayout container = findViewById(R.id.share_container);

        TextView sTitle = findViewById(R.id.share_title);
        TextView sContent = findViewById(R.id.share_content);
        TextView sDate = findViewById(R.id.share_date);

// Set content
        sTitle.setText(etTitle.getText().toString());
        sContent.setText(Html.fromHtml(currentNote.getContent(), Html.FROM_HTML_MODE_COMPACT));

// Tambahkan tanggal
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy, HH:mm", Locale.getDefault());
        String dateText = sdf.format(new Date(currentNote.getTimestamp()));
        sDate.setText(dateText);

// Styling
        container.setPadding(48, 48, 48, 48);
        int bgColor = ContextCompat.getColor(this, R.color.md_theme_light_onPrimary);
        int textColor = ContextCompat.getColor(this, R.color.md_theme_light_onBackground);

        container.setBackgroundColor(bgColor);
        sTitle.setTextColor(textColor);
        sContent.setTextColor(textColor);
        sDate.setTextColor(textColor);

        // Pastikan container punya ukuran valid
        container.setVisibility(View.VISIBLE);

        int targetWidth = container.getRootView().getWidth();
        container.measure(
                View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        container.layout(0, 0, container.getMeasuredWidth(), container.getMeasuredHeight());

        // Bitmap final
        Bitmap bitmap = createBitmapFromView(container);

        container.setVisibility(View.GONE);

        Uri uri = saveBitmapToCache(bitmap);
        if (uri == null) {
            Toast.makeText(this, "Failed to generate image", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share Note Image"));
    }

    // --------------------------------------------------
    // Menu / reminder / delete
    // --------------------------------------------------
    private void showMenuPopup(View anchor) {
        ViewGroup root = (ViewGroup) anchor.getRootView();
        View popupView = getLayoutInflater().inflate(R.layout.layout_menu_note, root, false);

        PopupWindow popup = new PopupWindow(popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true);

        popup.setElevation(10f);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        popupView.findViewById(R.id.action_reminder).setOnClickListener(v -> {
            setReminder();
            popup.dismiss();
        });
        popupView.findViewById(R.id.action_share).setOnClickListener(v -> {
            shareNoteAsImage();
            popup.dismiss();
        });
        popupView.findViewById(R.id.action_delete).setOnClickListener(v -> {
            confirmDelete();
            popup.dismiss();
        });

        popup.showAsDropDown(anchor, 0, 0);
    }

    private void setReminder() {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this, (v, y, m, d) -> {
            calendar.set(y, m, d);
            new TimePickerDialog(this, (v1, h, min) -> {
                calendar.set(Calendar.HOUR_OF_DAY, h);
                calendar.set(Calendar.MINUTE, min);
                calendar.set(Calendar.SECOND, 0);

                currentNote.setTimestamp(calendar.getTimeInMillis());
                viewModel.saveNote(this, currentNote);
                scheduleNotification(calendar.getTimeInMillis());
                Toast.makeText(this, "Reminder set", Toast.LENGTH_SHORT).show();

            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void scheduleNotification(long triggerTime) {
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("title", etTitle.getText().toString());
        intent.putExtra("note_id", currentNote.getId());
        intent.putExtra("user_id", viewModel.getUserId());

        int requestCode = currentNote.getId().hashCode();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(this, "Allow exact alarm permission in settings", Toast.LENGTH_LONG).show();
                startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
                return;
            }

            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } catch (SecurityException e) {
            Log.e(TAG, "Exact alarm permission denied", e);
            Toast.makeText(this, "Unable to schedule reminder", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete() {
        DialogUtils.showConfirmDialog(
                this,
                "Delete Note",
                "Delete permanently?",
                "Delete",
                "Cancel",
                () -> {
                    getSharedPreferences("notes_user123", MODE_PRIVATE)
                            .edit()
                            .remove(currentNote.getId())
                            .apply();
                    setResult(RESULT_OK);
                    finish();
                },
                null);
    }

    // --------------------------------------------------
    // Spans & text styling
    // --------------------------------------------------
    private void toggleStyle(int style) {
        int start = etContent.getSelectionStart();
        int end = etContent.getSelectionEnd();
        if (start == end) return;

        Spannable str = etContent.getText();
        StyleSpan[] spans = str.getSpans(start, end, StyleSpan.class);
        boolean exists = false;
        for (StyleSpan span : spans) {
            if (span.getStyle() == style) { str.removeSpan(span); exists = true; break; }
        }
        if (!exists) str.setSpan(new StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        updateToolbarState();
    }

    private void toggleSpan(Class<? extends CharacterStyle> spanClass) {
        int start = etContent.getSelectionStart();
        int end = etContent.getSelectionEnd();
        if (start == end) return;

        Spannable str = etContent.getText();
        Object[] spans = str.getSpans(start, end, spanClass);
        if (spans.length > 0) { for (Object span : spans) str.removeSpan(span); }
        else {
            if (spanClass.equals(StrikethroughSpan.class)) str.setSpan(new StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            else if (spanClass.equals(UnderlineSpan.class)) str.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        updateToolbarState();
    }

    private void openTextColorPicker() { openColorPicker(currentTextColor, this::setTextColor, color -> currentTextColor = color); }
    private void openHighlightColorPicker() { openColorPicker(currentHighlightColor, this::toggleBackgroundColor, color -> currentHighlightColor = color); }

    private void openColorPicker(int initialColor, ColorCallback onOk, ColorCallback onSave) {
        new AmbilWarnaDialog(this, initialColor, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override public void onOk(AmbilWarnaDialog dialog, int color) { onOk.onColorSelected(color); onSave.onColorSelected(color); }
            @Override public void onCancel(AmbilWarnaDialog dialog) {}
        }).show();
    }

    private interface ColorCallback { void onColorSelected(int color); }

    private void setTextColor(int color) { applySpan(ForegroundColorSpan.class, new ForegroundColorSpan(color)); }
    private void toggleBackgroundColor(int color) { toggleSpanWithRemove(new BackgroundColorSpan(color)); }

    private <T> void applySpan(Class<T> spanClass, T span) {
        int start = etContent.getSelectionStart();
        int end = etContent.getSelectionEnd();
        if (start == end) return;
        Spannable str = etContent.getText();
        T[] spans = str.getSpans(start, end, spanClass);
        for (T s : spans) str.removeSpan(s);
        str.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        updateToolbarState();
    }

    @SuppressWarnings("unchecked")
    private <T> void toggleSpanWithRemove(T newSpan) {
        int start = etContent.getSelectionStart();
        int end = etContent.getSelectionEnd();
        if (start == end) return;
        Spannable str = etContent.getText();
        T[] spans = str.getSpans(start, end, (Class<T>) BackgroundColorSpan.class);
        if (spans.length > 0) for (T s : spans) str.removeSpan(s);
        else str.setSpan(newSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        updateToolbarState();
    }

    private void setAlignment(Layout.Alignment alignment) {
        int start = etContent.getSelectionStart();
        int end = etContent.getSelectionEnd();

        Editable editable = etContent.getText();

        // Tentukan line tempat kursor berada
        int lineStart = editable.toString().lastIndexOf('\n', start - 1) + 1;
        int lineEnd = editable.toString().indexOf('\n', end);
        if (lineEnd == -1) lineEnd = editable.length();

        // Hapus span alignment sebelumnya
        AlignmentSpan.Standard[] spans = editable.getSpans(lineStart, lineEnd, AlignmentSpan.Standard.class);
        for (AlignmentSpan.Standard span : spans) {
            editable.removeSpan(span);
        }

        // Tambahkan span baru
        editable.setSpan(
                new AlignmentSpan.Standard(alignment),
                lineStart,
                lineEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        updateToolbarState();
    }

    private void toggleBullet() {
        Editable text = etContent.getText();
        int start = etContent.getSelectionStart();

        int lineStart = text.toString().lastIndexOf('\n', start - 1) + 1;

        isUpdating = true;

        if (bulletMode) {
            if (text.toString().startsWith("• ", lineStart)) {
                text.delete(lineStart, lineStart + 2);
            }
            bulletMode = false;

        } else {
            text.insert(lineStart, "• ");
            bulletMode = true;
        }

        isUpdating = false;
    }

    private void handleEnterWithBullet() {
        Editable text = etContent.getText();
        int cursor = etContent.getSelectionStart();

        int prevLineStart = text.toString().lastIndexOf('\n', cursor - 2) + 1;
        int prevLineEnd = cursor - 1;
        if (prevLineEnd < prevLineStart) return;

        String prevLine = text.subSequence(prevLineStart, prevLineEnd).toString();

        if (!prevLine.trim().startsWith("•")) return;

        isUpdatingText = true;

        if (prevLine.trim().equals("•")) {
            text.delete(prevLineStart, prevLineStart + 2);
            isUpdatingText = false;
            return;
        }

        text.insert(cursor, "• ");
        text.setSpan(new BulletSpan(20),
                cursor,
                cursor + 2,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        isUpdatingText = false;
    }

    private void changeTextSize(float factor) { applySpan(RelativeSizeSpan.class, new RelativeSizeSpan(factor)); }

    // --------------------------------------------------
    // Toolbar state & undo/redo
    // --------------------------------------------------
    private void updateToolbarState() {
        int start = etContent.getSelectionStart();
        int end = etContent.getSelectionEnd();
        if (start < 0 || end < 0) return;

        Spannable span = etContent.getText();

        // BOLD
        boolean bold = false;
        for (StyleSpan s : span.getSpans(start, end, StyleSpan.class)) {
            if (s.getStyle() == Typeface.BOLD) bold = true;
        }
        btnBold.setAlpha(bold ? 1f : 0.4f);

        // ITALIC
        boolean italic = false;
        for (StyleSpan s : span.getSpans(start, end, StyleSpan.class)) {
            if (s.getStyle() == Typeface.ITALIC) italic = true;
        }
        btnItalic.setAlpha(italic ? 1f : 0.4f);

        // UNDERLINE
        boolean underline = span.getSpans(start, end, UnderlineSpan.class).length > 0;
        btnUnderline.setAlpha(underline ? 1f : 0.4f);

        // STRIKETHROUGH
        boolean strike = span.getSpans(start, end, StrikethroughSpan.class).length > 0;
        btnStrike.setAlpha(strike ? 1f : 0.4f);

        // ALIGNMENT
        AlignmentSpan.Standard[] aligns =
                span.getSpans(start, end, AlignmentSpan.Standard.class);

        if (aligns.length > 0) {
            Layout.Alignment a = aligns[0].getAlignment();

            btnAlignLeft.setAlpha(a == Layout.Alignment.ALIGN_NORMAL ? 1f : 0.4f);
            btnAlignCenter.setAlpha(a == Layout.Alignment.ALIGN_CENTER ? 1f : 0.4f);
            btnAlignRight.setAlpha(a == Layout.Alignment.ALIGN_OPPOSITE ? 1f : 0.4f);

        } else {
            btnAlignLeft.setAlpha(0.4f);
            btnAlignCenter.setAlpha(0.4f);
            btnAlignRight.setAlpha(0.4f);
        }
    }

    private void undo() { performUndoRedo(true); }
    private void redo() { performUndoRedo(false); }

    private void performUndoRedo(boolean isUndo) {
        Stack<String> source = isUndo ? undoStack : redoStack;
        Stack<String> target = isUndo ? redoStack : undoStack;
        ImageButton sourceBtn = isUndo ? btnUndo : btnRedo;
        ImageButton targetBtn = isUndo ? btnRedo : btnUndo;

        if (!source.isEmpty()) {
            target.push(etContent.getText().toString());
            String text = source.pop();
            isUpdatingText = true;
            etContent.setText(text);
            isUpdatingText = false;
            sourceBtn.setEnabled(!source.isEmpty());
            targetBtn.setEnabled(true);
            updateToolbarState();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupEditNote() {
        EditText editNote = etContent;

        editNote.setFocusable(true);
        editNote.setFocusableInTouchMode(true);

        // caret langsung terlihat saat user tap
        editNote.setCursorVisible(true);

        editNote.setOnTouchListener((v, event) -> {
            editNote.setCursorVisible(true);
            editNote.requestFocus();

            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(etContent, InputMethodManager.SHOW_IMPLICIT);

            return false;
        });

        editNote.post(() -> {
            editNote.requestFocus();
            editNote.setCursorVisible(true);
        });
    }
}
