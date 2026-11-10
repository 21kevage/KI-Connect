package com.ki_connect.newsradar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public final class RadarMessagingService extends FirebaseMessagingService {
    static final String OPEN_DEMO = "com.ki_connect.newsradar.OPEN_DEMO";
    private static final String CHANNEL = "newsradar.demo";

    @Override
    public void onMessageReceived(RemoteMessage message) {
        if ("demo".equals(message.getData().get("action"))) showDemo(this);
    }

    static boolean showDemo(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "NewsRadar Demo", NotificationManager.IMPORTANCE_HIGH));
        if (!manager.areNotificationsEnabled()
                || manager.getNotificationChannel(CHANNEL).getImportance() == NotificationManager.IMPORTANCE_NONE) {
            return false;
        }
        Intent open = new Intent(context, MainActivity.class).setAction(OPEN_DEMO)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.notify(1, new Notification.Builder(context, CHANNEL)
                .setSmallIcon(com.ki_connect.newsradar.R.drawable.ic_radar)
                .setContentTitle("Demo · Neues KI-Pilotprojekt in Niederbayern")
                .setContentText("3 fiktive Quellen · 2 unterschiedliche Starttermine")
                .setContentIntent(tap).setAutoCancel(true).build());
        return true;
    }
}
