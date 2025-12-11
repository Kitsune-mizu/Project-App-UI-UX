package com.android.alpha.ui.notes;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notes")
public class Note implements Serializable {

    @PrimaryKey
    @NonNull
    private String id;  // Hapus final

    private String title = "";
    private String content = "";
    private long timestamp;
    private boolean pinned = false;

    public Note() {
        this.id = String.valueOf(System.currentTimeMillis());
        this.timestamp = System.currentTimeMillis();
    }

    public Note(@NonNull String id) {
        this.id = id;
        this.timestamp = System.currentTimeMillis();
    }

    @NonNull
    public String getId() { return id; }

    // ➕ Tambahkan setter ID
    public void setId(@NonNull String id) { this.id = id; }

    public String getTitle() { return title; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
    public boolean isPinned() { return pinned; }

    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public String getSubtitle() {
        String cleaned = android.text.Html
                .fromHtml(content, android.text.Html.FROM_HTML_MODE_LEGACY)
                .toString()
                .trim();
        return cleaned.length() > 100
                ? cleaned.substring(0, 100).replace("\n", " ") + "..."
                : cleaned.replace("\n", " ");
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}

