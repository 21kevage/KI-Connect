package com.ki_connect.newsradar;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Network;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FeedJob extends JobService {
    private static final int ID = 30;
    private static final int HISTORY_LIMIT = 72;
    private final Handler main = new Handler(Looper.getMainLooper());
    private JobParameters active;
    private Thread worker;

    static boolean isScheduled(Context context) {
        return context.getSystemService(JobScheduler.class).getPendingJob(ID) != null;
    }

    static boolean schedule(Context context) {
        JobInfo info = new JobInfo.Builder(ID, new ComponentName(context, FeedJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(30 * 60 * 1000L)
                .setPersisted(true)
                .build();
        return context.getSystemService(JobScheduler.class).schedule(info) == JobScheduler.RESULT_SUCCESS;
    }

    static void cancel(Context context) {
        context.getSystemService(JobScheduler.class).cancel(ID);
        history(context).edit().clear().apply();
    }

    private static SharedPreferences history(Context context) {
        return context.getSharedPreferences("feed-history", MODE_PRIVATE);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        active = params;
        Network network = Build.VERSION.SDK_INT >= 28 ? params.getNetwork() : null;
        worker = new Thread(() -> {
            Map<String, List<RadarData.Article>> fetched = new LinkedHashMap<>();
            try {
                JSONArray feeds = RadarData.feeds(this);
                for (int i = 0; i < feeds.length() && !Thread.currentThread().isInterrupted(); i++) {
                    JSONObject feed = feeds.getJSONObject(i);
                    try {
                        List<RadarData.Article> articles = RadarData.readFeed(feed, network);
                        if (!articles.isEmpty()) fetched.put(feed.getString("name"), articles);
                    } catch (Exception ignored) {
                        // Keep this source's previous baseline when it is unavailable.
                    }
                }
            } catch (Exception ignored) {
                // A broken asset should leave the previous baseline intact.
            }
            main.post(() -> finish(params, fetched));
        }, "newsradar-feed-check");
        worker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (active == params) {
            active = null;
            worker.interrupt();
            worker = null;
        }
        return true;
    }

    private void finish(JobParameters params, Map<String, List<RadarData.Article>> fetched) {
        if (active != params) return;
        active = null;
        worker = null;
        SharedPreferences history = history(this);
        SharedPreferences.Editor changes = history.edit();
        List<RadarData.Article> fresh = new ArrayList<>();
        for (var source : fetched.entrySet()) {
            Set<String> previous = history.getStringSet(source.getKey(), null);
            Set<String> seen = new LinkedHashSet<>();
            for (RadarData.Article article : source.getValue()) {
                if (seen.add(article.key()) && previous != null && !previous.contains(article.key())) {
                    fresh.add(article);
                }
            }
            if (previous != null) {
                for (String key : previous) {
                    if (seen.size() >= HISTORY_LIMIT) break;
                    seen.add(key);
                }
            }
            changes.putStringSet(source.getKey(), seen);
        }
        changes.apply();
        if (!fresh.isEmpty()) RadarNotifications.showFeeds(this, RadarData.group(fresh));
        jobFinished(params, false);
    }
}
