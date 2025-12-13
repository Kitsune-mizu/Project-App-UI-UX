package com.android.alpha.ui.notifications;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.utils.DialogUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends ListAdapter<ActivityItem, NotificationAdapter.ViewHolder> {

    // === CONSTANTS ===
    private static final String TAG = "NotificationAdapter";

    // === CONSTRUCTOR ===
    public NotificationAdapter() {
        super(DIFF_CALLBACK);
        setHasStableIds(true);
    }

    // === RECYCLERVIEW ADAPTER IMPLEMENTATION ===
    @Override
    public long getItemId(int position) {
        return getItem(position).getTimestamp();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_activity, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ActivityItem item = getItem(position);
        Context context = holder.itemView.getContext();

        try {
            bindTextAndIcon(holder, context, item);
        } catch (Exception e) {
            // Log error
            Log.e(TAG, "onBindViewHolder error: " + e.getMessage(), e);
            // Fallback for UI integrity
            applyFallbackUI(holder);
        }

        // Long click to delete
        holder.itemView.setOnLongClickListener(v -> {
            DialogUtils.showConfirmDialog(
                    context,
                    context.getString(R.string.dialog_delete_notif_title),
                    context.getString(R.string.dialog_delete_notif_message),
                    context.getString(R.string.action_delete),
                    context.getString(R.string.action_cancel),
                    () -> deleteNotification(item),
                    null
            );
            return true;
        });
    }

    // === BINDING HELPERS ===
    private void bindTextAndIcon(@NonNull ViewHolder holder, Context context, ActivityItem item) {
        // Set time according to the active language
        holder.tvTime.setText(formatTimestamp(item.getTimestamp()));

        // Set title & description
        holder.tvTitle.setText(getSafeString(context, item.getTitleResId()));
        holder.tvDesc.setText(getSafeString(context, item.getDescriptionResId()));

        // Set icon and color
        int iconRes = safeIcon(context, item.getIconRes());
        holder.ivIcon.setImageResource(iconRes);

        try {
            holder.ivIcon.setColorFilter(item.getColor());
        } catch (Exception ignore) {
            // Ignore if color is invalid
        }
    }

    private void applyFallbackUI(ViewHolder holder) {
        holder.tvTitle.setText("");
        holder.tvDesc.setText("");
        holder.tvTime.setText("");
        holder.ivIcon.setImageResource(R.drawable.ic_notification_default);
    }

    // === UTILITY METHODS ===
    private int safeIcon(Context context, int resId) {
        if (resId == 0) return R.drawable.ic_notification_default;
        try {
            // Check if resource ID is a valid drawable
            AppCompatResources.getDrawable(context, resId);
            return resId;
        } catch (Exception e) {
            return R.drawable.ic_notification_default;
        }
    }

    private String formatTimestamp(long timestamp) {
        String langCode = UserSession.getInstance().getLanguage();
        Locale locale = new Locale(langCode);
        return new SimpleDateFormat("d MMM yyyy • HH:mm", locale)
                .format(new Date(timestamp));
    }

    private String getSafeString(Context context, int resId) {
        if (resId == 0) return "";
        try {
            return context.getString(resId);
        } catch (Resources.NotFoundException e) {
            return "";
        }
    }

    private void deleteNotification(ActivityItem item) {
        List<ActivityItem> current = new ArrayList<>(getCurrentList());
        current.remove(item);
        submitList(current);
        UserSession.getInstance().saveActivities(current);
    }

    // === DIFF CALLBACK ===
    private static final DiffUtil.ItemCallback<ActivityItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull ActivityItem oldItem, @NonNull ActivityItem newItem) {
                    // Unique ID is timestamp
                    return oldItem.getTimestamp() == newItem.getTimestamp();
                }

                @Override
                public boolean areContentsTheSame(@NonNull ActivityItem oldItem, @NonNull ActivityItem newItem) {
                    // Check if content is the same (relies on ActivityItem.equals())
                    return oldItem.equals(newItem);
                }
            };

    // === VIEW-HOLDER ===
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDesc, tvTime;
        ImageView ivIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvActivityTitle);
            tvDesc = itemView.findViewById(R.id.tvActivityDesc);
            tvTime = itemView.findViewById(R.id.tvActivityTime);
            ivIcon = itemView.findViewById(R.id.iconActivity);
        }
    }
}