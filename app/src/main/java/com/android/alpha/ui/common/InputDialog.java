package com.android.alpha.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.alpha.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

@SuppressWarnings("unused")
public class InputDialog extends BottomSheetDialogFragment {

    // --- Interface ---
    public interface InputDialogListener {
        void onTextEntered(String newText);
    }

    // --- Instance Variables ---
    private InputDialogListener listener;

    // --- Factory Method ---
    public static InputDialog newInstance(String title, String initialValue, InputDialogListener listener) {
        InputDialog dialog = new InputDialog();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("value", initialValue);
        dialog.setArguments(args);
        dialog.setListener(listener);
        return dialog;
    }

    // --- Listener Setter ---
    public void setListener(InputDialogListener listener) {
        this.listener = listener;
    }

    // --- Fragment Lifecycle ---
    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.dialog_input, container, false);

        // Get arguments safely
        Bundle args = getArguments();
        String title = args == null ? "" : args.getString("title", "");
        String initialValue = args == null ? "" : args.getString("value", "");

        // Bind Views
        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        EditText etInput = view.findViewById(R.id.etInput);
        MaterialButton btnConfirm = view.findViewById(R.id.btnConfirm);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);

        // Apply Values
        tvTitle.setText(title);
        etInput.setText(initialValue);

        // Button Actions
        btnCancel.setOnClickListener(v -> dismiss());
        btnConfirm.setOnClickListener(v -> {
            if (listener != null) listener.onTextEntered(etInput.getText().toString());
            dismiss();
        });

        return view;
    }

    @Override
    public int getTheme() {
        return R.style.BottomSheetDialogTheme;
    }
}
