package com.android.alpha.ui.notes;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.android.alpha.R;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "note_reminder_channel";

    @Override
    public void onReceive(Context context, Intent intent) {

        String title = intent.getStringExtra("title");
        String noteId = intent.getStringExtra("note_id");
        String userId = intent.getStringExtra("user_id");   // ← ambil userId

        if (title == null || title.trim().isEmpty()) {
            title = "Note Reminder";
        }

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        createNotificationChannel(manager);

        PendingIntent pendingIntent = createPendingIntent(context, noteId, userId);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notifications)
                        .setContentTitle("Reminder Alert")
                        .setContentText(title)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(title))
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setVibrate(new long[]{0, 300, 250, 300});

        int notificationId = (int) System.currentTimeMillis();
        manager.notify(notificationId, builder.build());
    }

    private void createNotificationChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Note Reminder Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Channel for note reminder notifications");
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }
    }

    private PendingIntent createPendingIntent(Context context, String noteId, String userId) {

        // requestCode unik
        int requestCode = (noteId != null)
                ? noteId.hashCode()
                : (int) System.currentTimeMillis();

        Intent intent = new Intent(context, EditNoteActivity.class);
        intent.putExtra("note_id", noteId);
        intent.putExtra("user_id", userId);

        androidx.core.app.TaskStackBuilder stackBuilder =
                androidx.core.app.TaskStackBuilder.create(context);

        stackBuilder.addParentStack(EditNoteActivity.class);

        stackBuilder.addNextIntent(intent);

        return stackBuilder.getPendingIntent(
                requestCode,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

}
