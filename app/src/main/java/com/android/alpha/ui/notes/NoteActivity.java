package com.android.alpha.ui.notes;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.utils.DialogUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class NoteActivity extends AppCompatActivity
        implements NoteAdapter.OnNoteClickListener, NoteAdapter.OnSelectionModeListener {

    private NoteAdapter adapter;
    private NoteViewModel viewModel;

    private RecyclerView recyclerView;
    private FloatingActionButton fabAddNote;
    private LinearLayout selectionModeActionBar;
    private RelativeLayout selectionModeHeader;
    private TextView tvSelectionCount;
    private SearchView searchView;

    private List<Note> allNotes = new ArrayList<>();

    private enum MultiAction { PIN, DELETE }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note);

        initViewModel();
        initViews();
        setupSearchView();
        setupRecyclerView();
        setupListeners();

        viewModel.getActiveNotes().observe(this, notes -> {
            allNotes = notes;
            applyFilter();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.loadNotes(this);
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(NoteViewModel.class);

        String userId = UserSession.getInstance()
                .getUserData(UserSession.getInstance().getUsername()).userId;

        viewModel.setUserId(userId);
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_view_notes);
        fabAddNote = findViewById(R.id.fab_add_note);
        selectionModeActionBar = findViewById(R.id.selection_mode_action_bar);
        selectionModeHeader = findViewById(R.id.selection_mode_header);
        tvSelectionCount = findViewById(R.id.tv_selection_count);
        searchView = findViewById(R.id.search_view);
    }

    private void setupSearchView() {
        searchView.post(() -> {
            View searchPlate = searchView.findViewById(androidx.appcompat.R.id.search_plate);
            if (searchPlate != null)
                searchPlate.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_search_rounded));

            android.widget.EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
            if (searchEditText != null) {
                searchEditText.setBackground(null);
                searchEditText.setHint("Search notes...");
                searchEditText.setHintTextColor(ContextCompat.getColor(this, R.color.md_theme_light_onSurface));
                searchEditText.setTextColor(ContextCompat.getColor(this, R.color.md_theme_light_onBackground));
            }

            ImageView searchIcon = searchView.findViewById(androidx.appcompat.R.id.search_mag_icon);
            if (searchIcon != null) {
                searchIcon.setImageResource(R.drawable.ic_search_note);
                searchIcon.setColorFilter(ContextCompat.getColor(this, R.color.md_theme_light_onSurface), PorterDuff.Mode.SRC_IN);
                int sizeInPx = (int) (40 * getResources().getDisplayMetrics().density + 0.5f);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizeInPx, sizeInPx);
                params.gravity = Gravity.CENTER_VERTICAL;
                searchIcon.setLayoutParams(params);
            }

            ImageView closeIcon = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
            if (closeIcon != null)
                closeIcon.setImageResource(R.drawable.transparent_icon);
        });
    }

    private void setupRecyclerView() {
        // NoteActivity: long press aktif, item height WRAP_CONTENT
        adapter = new NoteAdapter(
                new ArrayList<>(),
                this,   // OnNoteClickListener
                this,   // OnSelectionModeListener
                false,  // isHome = false, supaya item WRAP_CONTENT
                true    // selectionEnabled = true, supaya long press aktif
        );

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners() {
        fabAddNote.setOnClickListener(v ->
                startActivity(new Intent(this, EditNoteActivity.class))
        );

        findViewById(R.id.action_cancel_selection).setOnClickListener(v -> adapter.exitSelectionMode());
        findViewById(R.id.action_select_all).setOnClickListener(v -> adapter.selectAll());
        findViewById(R.id.action_pin).setOnClickListener(v -> handleMultiAction(MultiAction.PIN));
        findViewById(R.id.action_delete).setOnClickListener(v -> handleMultiAction(MultiAction.DELETE));

        searchView.clearFocus();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { applyFilter(query); return true; }
            @Override public boolean onQueryTextChange(String newText) { applyFilter(newText); return true; }
        });

        recyclerView.setOnTouchListener((v, event) -> {
            if (!searchView.isIconified()) {
                searchView.setIconified(true);
                searchView.clearFocus();
            }
            if (event.getAction() == MotionEvent.ACTION_UP) v.performClick();
            return false;
        });
    }

    private void applyFilter(String query) {
        String search = query != null ? query.toLowerCase(Locale.ROOT) : "";
        List<Note> filtered = allNotes.stream()
                .filter(n -> n.getTitle().toLowerCase().contains(search) || n.getContent().toLowerCase().contains(search))
                .collect(Collectors.toList());
        adapter.updateNotes(filtered);
    }

    private void applyFilter() {
        applyFilter(searchView.getQuery() != null ? searchView.getQuery().toString() : "");
    }

    @Override
    public void onNoteClick(Note note) {
        Intent intent = new Intent(this, EditNoteActivity.class);
        intent.putExtra("note_id", note.getId());
        startActivity(intent);
    }

    @Override
    public void onSelectionModeChange(boolean active) {
        selectionModeHeader.setVisibility(active ? View.VISIBLE : View.GONE);
        selectionModeActionBar.setVisibility(active ? View.VISIBLE : View.GONE);
        fabAddNote.setVisibility(active ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onSelectionCountChange(int count) {
        tvSelectionCount.setText(String.format(Locale.getDefault(), "%d Selected", count));
    }

    private void handleMultiAction(MultiAction action) {
        Set<String> selectedIds = adapter.getSelectedNoteIds();
        if (selectedIds.isEmpty()) return;

        List<Note> notes = allNotes.stream()
                .filter(n -> selectedIds.contains(n.getId()))
                .collect(Collectors.toList());

        switch (action) {
            case PIN:
                boolean targetPin = !notes.stream().allMatch(Note::isPinned);
                notes.forEach(n -> { n.setPinned(targetPin); viewModel.saveNote(this, n); });
                Toast.makeText(this, targetPin ? "Pinned" : "Unpinned", Toast.LENGTH_SHORT).show();
                break;

            case DELETE:
                DialogUtils.showConfirmDialog(
                        this,
                        "Delete Notes",
                        "Delete selected notes?",
                        "Delete",
                        "Cancel",
                        () -> {
                            notes.forEach(n -> viewModel.deleteNote(this, n.getId()));
                            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                            adapter.exitSelectionMode();
                            viewModel.loadNotes(this);
                        },
                        null);
                return;
        }

        adapter.exitSelectionMode();
        viewModel.loadNotes(this);
    }
}
