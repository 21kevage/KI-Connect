package com.ki_connect.newsradar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.List;

final class RadarNotifications {
    static final String OPEN_DEMO = "com.ki_connect.newsradar.OPEN_DEMO";

    static boolean showDemo(Context context) {
        return show(context, "newsradar.demo", "Bühnendemo", 1, OPEN_DEMO,
                "Demo · Neues KI-Pilotprojekt in Niederbayern", "3 fiktive Quellen · 2 unterschiedliche Starttermine");
    }

    static void showFeeds(Context context, List<RadarData.Story> stories) {
        String title = stories.size() == 1 ? "Neue Meldung in NewsRadar" : stories.size() + " neue Meldungen in NewsRadar";
        show(context, "newsradar.feeds", "Neue Meldungen", 2, Intent.ACTION_MAIN, title, stories.get(0).title());
    }

    private static boolean show(Context context, String channel, String name, int id, String action,
                                String title, String text) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(channel, name,
                id == 1 ? NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_DEFAULT));
        if (!manager.areNotificationsEnabled()
                || manager.getNotificationChannel(channel).getImportance() == NotificationManager.IMPORTANCE_NONE) {
            return false;
        }
        Intent open = new Intent(context, MainActivity.class).setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.notify(id, new Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_radar).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(tap).setAutoCancel(true).build());
        return true;
    }
}
