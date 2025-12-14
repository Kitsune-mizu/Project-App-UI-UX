package com.android.alpha.ui.map;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class LocationSuggestionAdapter extends RecyclerView.Adapter<LocationSuggestionAdapter.ViewHolder> {

    // === INTERFACES ===
    public interface OnItemClickListener {
        void onItemClick(LocationSuggestion suggestion);
    }

    // === INSTANCE VARIABLES ===
    private final List<LocationSuggestion> suggestions = new ArrayList<>();
    private final OnItemClickListener listener;

    // === CONSTRUCTOR ===
    public LocationSuggestionAdapter(List<LocationSuggestion> initialSuggestions, OnItemClickListener listener) {
        if (initialSuggestions != null) {
            this.suggestions.addAll(initialSuggestions);
        }
        this.listener = listener;
    }

    // === RECYCLERVIEW ADAPTER OVERRIDES ===
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocationSuggestion suggestion = suggestions.get(position);
        holder.textView.setText(suggestion.displayName);
        holder.itemView.setOnClickListener(v -> listener.onItemClick(suggestion));
    }

    @Override
    public int getItemCount() {
        return suggestions.size();
    }

    // === DATA MANAGEMENT ===
    public void updateData(List<LocationSuggestion> newSuggestions) {
        if (newSuggestions == null) return;

        this.suggestions.clear();
        this.suggestions.addAll(newSuggestions);
        notifyDataSetChanged(); // langsung refresh RecyclerView
    }

    // === VIEW-HOLDER CLASS ===
    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
}
