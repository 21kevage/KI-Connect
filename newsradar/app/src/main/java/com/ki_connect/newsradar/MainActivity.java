package com.ki_connect.newsradar;

import android.Manifest;
import android.app.Activity;
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
    private int permissionAction;
    private static final int DEMO_PERMISSION = 1;
    private static final int FEEDS_PERMISSION = 2;

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
        state.putInt("permissionAction", permissionAction);
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        permissionAction = state.getInt("permissionAction");
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
        if (RadarNotifications.OPEN_DEMO.equals(intent.getAction())) showDemo();
        else showHome();
    }

    private void loadFeeds() {
        if (loading) return;
        loading = true;
        if (home) showHome();
        List<CompletableFuture<List<RadarData.Article>>> tasks = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try {
            JSONArray sources = RadarData.feeds(this);
            for (int i = 0; i < sources.length(); i++) {
                JSONObject source = sources.getJSONObject(i);
                names.add(source.getString("name"));
                tasks.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return RadarData.readFeed(source, null);
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
        button(stage, "Bühnensteuerung", this::showStageSettings, false);

        label(body, "LIVE  /  3 QUELLEN", 13, MINT, true);
        label(body, "Aktuelle Meldungen", 26, INK, true);
        label(body, "Tagesschau · Deutschlandfunk · heise online", 15, MUTED, false);
        button(body, loading ? "Feeds werden geladen …" : "Feeds aktualisieren", this::loadFeeds, false).setEnabled(!loading);
        boolean alerts = FeedJob.isScheduled(this);
        button(body, alerts ? "Feed-Hinweise ausschalten" : "Feed-Hinweise einschalten", this::toggleFeedAlerts, false);
        if (alerts) label(body, "Android prüft die Feeds etwa alle 30 Minuten.", 14, MUTED, false);
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
                if (RadarData.isSupportedArticle(link)) {
                    button(card, "HTML auswerten", () -> showHtml(item, story), false);
                }
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

    private void showHtml(RadarData.Article article, RadarData.Story story) {
        begin(false, false);
        button(body, "‹  Zurück zur Meldung", () -> showStory(story), false);
        label(body, "HTML / " + article.source(), 13, MINT, true);
        label(body, article.title(), 29, INK, true);
        LinearLayout content = panel(body);
        label(content, "Webseite wird ausgewertet …", 16, MUTED, false);
        CompletableFuture.supplyAsync(() -> RadarData.readPage(article.link()), feeds).thenAccept(page ->
            runOnUiThread(() -> {
                if (isDestroyed() || content.getParent() != body) return;
                content.removeAllViews();
                if (page == null) {
                    label(content, "Die Seite lieferte keine auswertbare Beschreibung.", 16, AMBER, false);
                    return;
                }
                label(content, "VON DER WEBSEITE", 13, MINT, true);
                label(content, page.title(), 21, INK, true);
                label(content, page.detail(), 16, MUTED, false);
            }));
    }

    private void showStageSettings() {
        begin(false, false);
        backButton();
        label(body, "DEMO / LAPTOP + ANDROID", 13, MINT, true);
        label(body, "Bühnensteuerung", 33, INK, true);
        label(body, "Android per USB mit dem Laptop verbinden, USB-Debugging bestätigen und das lokale Demo-Dashboard öffnen.", 17, INK, false);
        button(body, "Benachrichtigungen testen", this::requestDemoNotification, true);
    }

    private void requestDemoNotification() {
        if (allowNotifications(DEMO_PERMISSION)) postNotification();
    }

    private void toggleFeedAlerts() {
        if (FeedJob.isScheduled(this)) {
            FeedJob.cancel(this);
            showHome();
        } else if (allowNotifications(FEEDS_PERMISSION)) {
            enableFeedAlerts();
        }
    }

    private void enableFeedAlerts() {
        if (!getSystemService(android.app.NotificationManager.class).areNotificationsEnabled()) {
            toast("Bitte Benachrichtigungen in den Android-Einstellungen erlauben.");
            return;
        }
        if (!FeedJob.schedule(this)) toast("Feed-Hinweise konnten nicht aktiviert werden.");
        showHome();
    }

    private boolean allowNotifications(int action) {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return true;
        permissionAction = action;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION);
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != PERMISSION) return;
        int action = permissionAction;
        permissionAction = 0;
        if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
            toast("Bitte Benachrichtigungen in den Android-Einstellungen erlauben.");
        } else if (action == DEMO_PERMISSION) {
            postNotification();
        } else if (action == FEEDS_PERMISSION) {
            enableFeedAlerts();
        }
    }

    private void postNotification() {
        if (RadarNotifications.showDemo(this)) toast("Testbenachrichtigung ausgelöst.");
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
