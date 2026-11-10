package com.ki_connect.newsradar;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(234, 243, 249);
    private static final int MUTED = Color.rgb(169, 189, 204);
    private static final int MINT = Color.rgb(115, 237, 201);
    private static final int AMBER = Color.rgb(255, 204, 120);
    private static final int BACKGROUND = Color.rgb(11, 21, 32);
    private static final int PANEL = Color.rgb(21, 39, 53);
    private static final int PERMISSION = 10;

    private final ExecutorService feeds = Executors.newFixedThreadPool(3);
    private LinearLayout body;
    private ScrollView scroll;
    private List<RadarData.Story> stories = java.util.Collections.emptyList();
    private final Set<String> missing = new LinkedHashSet<>();
    private boolean loading;
    private boolean home;
    private boolean demoVisible;
    private boolean notifyAfterPermission;

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        body = column();
        body.setPadding(dp(22), dp(24), dp(22), dp(28));
        scroll.addView(body);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            body.setPadding(dp(22) + insets.getSystemWindowInsetLeft(), dp(24) + insets.getSystemWindowInsetTop(),
                    dp(22) + insets.getSystemWindowInsetRight(), dp(28) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        setContentView(scroll);
        if (saved != null && saved.getBoolean("demo")) showDemo();
        else showFromIntent(getIntent());
        loadFeeds();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("demo", demoVisible);
        state.putBoolean("notifyAfterPermission", notifyAfterPermission);
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        notifyAfterPermission = state.getBoolean("notifyAfterPermission");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showFromIntent(intent);
    }

    @Override
    protected void onDestroy() {
        feeds.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (home) super.onBackPressed();
        else showHome();
    }

    private void showFromIntent(Intent intent) {
        if (RadarMessagingService.OPEN_DEMO.equals(intent.getAction())) showDemo();
        else showHome();
    }

    private void loadFeeds() {
        if (loading) return;
        loading = true;
        if (home) showHome();
        List<CompletableFuture<List<RadarData.Article>>> tasks = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try {
            JSONArray sources = new JSONArray(RadarData.asset(this, "feeds.json"));
            for (int i = 0; i < sources.length(); i++) {
                JSONObject source = sources.getJSONObject(i);
                names.add(source.getString("name"));
                tasks.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return RadarData.readFeed(source);
                    } catch (Exception failure) {
                        return null;
                    }
                }, feeds));
            }
        } catch (Exception failure) {
            loading = false;
            missing.add("Quellenkonfiguration");
            if (home) showHome();
            return;
        }
        CompletableFuture.allOf(tasks.toArray(new CompletableFuture<?>[0])).thenRun(() -> {
            List<RadarData.Article> articles = new ArrayList<>();
            Set<String> unavailable = new LinkedHashSet<>();
            for (int i = 0; i < tasks.size(); i++) {
                List<RadarData.Article> result = tasks.get(i).join();
                if (result == null) unavailable.add(names.get(i));
                else articles.addAll(result);
            }
            List<RadarData.Story> grouped = RadarData.group(articles);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                loading = false;
                stories = grouped;
                missing.clear();
                missing.addAll(unavailable);
                if (home) showHome();
            });
        });
    }

    private void showHome() {
        begin(true, false);
        label(body, "●   NEWSRADAR / 01", 13, MINT, true);
        label(body, "NewsRadar", 42, INK, true);
        label(body, "Eine Meldung. Mehrere Perspektiven.", 17, MUTED, false);
        LinearLayout stage = panel(body);
        label(stage, "BÜHNENDEMO", 13, MINT, true);
        label(stage, "KI-Labor für Niederbayern", 22, INK, true);
        label(stage, "Drei fiktive Quellen, zwei Starttermine.", 16, MUTED, false);
        button(stage, "Quellenvergleich ansehen", this::showDemo, true);
        button(stage, "Testbenachrichtigung auslösen", this::requestDemoNotification, false);
        button(stage, "Push-Einrichtung", this::showPushSettings, false);

        label(body, "LIVE  /  3 QUELLEN", 13, MINT, true);
        label(body, "Aktuelle Meldungen", 26, INK, true);
        label(body, "Tagesschau · Deutschlandfunk · heise online", 15, MUTED, false);
        button(body, loading ? "Feeds werden geladen …" : "Feeds aktualisieren", this::loadFeeds, false).setEnabled(!loading);
        if (!missing.isEmpty()) label(body, "Nicht verfügbar: " + String.join(", ", missing), 15, AMBER, false);
        if (stories.isEmpty() && !loading) label(body, "Keine Meldungen geladen.", 16, MUTED, false);
        for (RadarData.Story story : stories) {
            LinearLayout card = panel(body);
            int count = story.sourceCount();
            label(card, count == 1 ? story.articles.get(0).source() : count + " Quellen · ähnliche Titel",
                    13, MINT, true);
            label(card, story.title(), 20, INK, true);
            button(card, count == 1 ? "Meldung öffnen" : "Quellen vergleichen", () -> showStory(story), false);
        }
    }

    private void showStory(RadarData.Story story) {
        begin(false, false);
        backButton();
        label(body, "LIVE / QUELLENANSICHT", 13, MINT, true);
        label(body, story.title(), 31, INK, true);
        if (story.sourceCount() > 1) {
            label(body, "Diese Meldungen haben ähnliche Überschriften. Die Originalquellen zeigen die Details.",
                    16, MUTED, false);
        }
        for (RadarData.Article item : story.articles) {
            LinearLayout card = sourceCard(item.source(), item.title(), item.detail());
            Uri link = Uri.parse(item.link());
            if ("https".equalsIgnoreCase(link.getScheme()) && link.getHost() != null) {
                button(card, "Original öffnen", () -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, link));
                    } catch (android.content.ActivityNotFoundException failure) {
                        toast("Kein Browser verfügbar.");
                    }
                }, false);
            }
        }
    }

    private void showDemo() {
        begin(false, true);
        backButton();
        try {
            JSONObject demo = RadarData.demo(this);
            JSONArray sources = demo.getJSONArray("sources");
            label(body, "DEMO / " + sources.length() + " FIKTIVE QUELLEN", 13, MINT, true);
            label(body, demo.getString("title"), 33, INK, true);
            LinearLayout overlap = panel(body);
            label(overlap, "WORIN SICH DIE QUELLEN EINIG SIND", 13, MINT, true);
            label(overlap, demo.getString("topic"), 20, INK, false);

            Set<String> dates = new LinkedHashSet<>();
            for (int i = 0; i < sources.length(); i++) {
                String start = sources.getJSONObject(i).optString("start");
                if (!start.isEmpty()) dates.add(start);
            }
            if (dates.size() > 1) {
                LinearLayout disagreement = panel(body);
                label(disagreement, "ABWEICHENDE STARTTERMINE", 13, AMBER, true);
                label(disagreement, String.join(" oder ", dates) + "? Der Starttermin ist offen.", 21, INK, true);
            }
            for (int i = 0; i < sources.length(); i++) {
                JSONObject item = sources.getJSONObject(i);
                sourceCard(item.getString("name"), item.getString("title"), item.getString("detail"));
            }
        } catch (Exception failure) {
            label(body, "Demodaten konnten nicht geladen werden.", 17, AMBER, false);
        }
    }

    private void showPushSettings() {
        begin(false, false);
        backButton();
        label(body, "PUSH / EINRICHTUNG", 13, MINT, true);
        label(body, "Bühnensteuerung", 33, INK, true);
        label(body, "USB-Demo: Android mit dem Laptop verbinden, USB-Debugging bestätigen und das Demo-Dashboard öffnen.", 17, INK, false);
        button(body, "Benachrichtigungen testen", this::requestDemoNotification, true);
        if (FirebaseApp.getApps(this).isEmpty()) {
            label(body, "Firebase ist in dieser APK noch nicht eingerichtet. Die USB-Demo ist verfügbar.",
                    17, MUTED, false);
            return;
        }
        label(body, "Für Internet-Push den Gerätetoken kopieren und im lokalen Dashboard einfügen.", 17, MUTED, false);
        button(body, "Firebase-Gerätetoken kopieren", () -> FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        toast("Gerätetoken konnte nicht geladen werden.");
                        return;
                    }
                    ClipData clip = ClipData.newPlainText("NewsRadar Firebase-Gerätetoken", task.getResult());
                    if (Build.VERSION.SDK_INT >= 33) {
                        android.os.PersistableBundle extras = new android.os.PersistableBundle();
                        extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
                        clip.getDescription().setExtras(extras);
                    }
                    getSystemService(ClipboardManager.class).setPrimaryClip(clip);
                    toast("Gerätetoken kopiert.");
                }), false);
    }

    private void requestDemoNotification() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifyAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION);
        } else {
            postNotification();
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != PERMISSION || !notifyAfterPermission) return;
        notifyAfterPermission = false;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) postNotification();
        else toast("Bitte Benachrichtigungen in den Android-Einstellungen erlauben.");
    }

    private void postNotification() {
        if (RadarMessagingService.showDemo(this)) toast("Testbenachrichtigung ausgelöst.");
        else toast("Bitte Benachrichtigungen in den Android-Einstellungen erlauben.");
    }

    private void begin(boolean isHome, boolean isDemo) {
        home = isHome;
        demoVisible = isDemo;
        body.removeAllViews();
        scroll.scrollTo(0, 0);
    }

    private void backButton() {
        button(body, "‹  Zur Übersicht", this::showHome, false);
    }

    private LinearLayout sourceCard(String name, String title, String detail) {
        LinearLayout card = panel(body);
        label(card, name, 14, MINT, true);
        label(card, title, 21, INK, true);
        if (!detail.isEmpty()) label(card, detail, 16, MUTED, false);
        return card;
    }

    private LinearLayout panel(LinearLayout parent) {
        LinearLayout panel = column();
        GradientDrawable background = new GradientDrawable();
        background.setColor(PANEL);
        background.setCornerRadius(dp(18));
        panel.setBackground(background);
        panel.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(13), 0, dp(5));
        parent.addView(panel, params);
        return panel;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private void label(LinearLayout parent, String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(4), 0, dp(5));
        parent.addView(view);
    }

    private Button button(LinearLayout parent, String text, Runnable action, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        button.setTextSize(15);
        button.setTextColor(primary ? BACKGROUND : INK);
        button.setBackgroundTintList(ColorStateList.valueOf(primary ? MINT : Color.rgb(39, 63, 78)));
        button.setOnClickListener(view -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
