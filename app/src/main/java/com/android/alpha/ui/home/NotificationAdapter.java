package com.android.alpha.ui.home;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
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

    public NotificationAdapter() {
        super(DIFF_CALLBACK);
        setHasStableIds(true);
    }

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

        // Atur waktu sesuai bahasa aktif
        holder.tvTime.setText(formatTimestamp(item.getTimestamp()));

        // Atur title & description
        holder.tvTitle.setText(getSafeString(context, item.getTitleResId()));
        holder.tvDesc.setText(getSafeString(context, item.getDescriptionResId()));

        // Icon dan warna
        holder.ivIcon.setImageResource(item.getIconRes());
        holder.ivIcon.setColorFilter(item.getColor());

        // Long click delete
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

    // ==================== Helper Method ====================

    private String formatTimestamp(long timestamp) {
        String langCode = UserSession.getInstance().getLanguage();
        Locale locale = new Locale(langCode);
        return new SimpleDateFormat("EEEE, d MMM yyyy • HH:mm", locale)
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

    // ==================== Diff Callback ====================

    private static final DiffUtil.ItemCallback<ActivityItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull ActivityItem oldItem, @NonNull ActivityItem newItem) {
                    return oldItem.getTimestamp() == newItem.getTimestamp();
                }

                @Override
                public boolean areContentsTheSame(@NonNull ActivityItem oldItem, @NonNull ActivityItem newItem) {
                    return oldItem.equals(newItem);
                }
            };

    // ==================== ViewHolder ====================

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
