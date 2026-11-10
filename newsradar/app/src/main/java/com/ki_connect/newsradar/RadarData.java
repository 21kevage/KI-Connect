package com.ki_connect.newsradar;

import android.content.Context;
import android.net.Network;
import android.net.Uri;
import android.text.Html;
import android.util.Xml;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.xmlpull.v1.XmlPullParser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;

final class RadarData {
    record Article(String source, String title, String detail, String link) {
        String key() {
            return link.isEmpty() ? title : link;
        }
    }

    record Page(String title, String detail) {}

    static final class Story {
        final List<Article> articles = new ArrayList<>();

        String title() {
            return articles.get(0).title();
        }

        int sourceCount() {
            return (int) articles.stream().map(Article::source).distinct().count();
        }
    }

    static String asset(Context context, String name) throws Exception {
        try (InputStream input = context.getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            for (int size; (size = input.read(buffer)) != -1;) {
                output.write(buffer, 0, size);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static JSONObject demo(Context context) throws Exception {
        return new JSONObject(asset(context, "demo.json"));
    }

    static JSONArray feeds(Context context) throws Exception {
        return new JSONArray(asset(context, "feeds.json"));
    }

    static List<Article> readFeed(JSONObject feed, Network network) throws Exception {
        URL url = new URL(feed.getString("url"));
        HttpsURLConnection connection = (HttpsURLConnection) (network == null
                ? url.openConnection() : network.openConnection(url));
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "NewsRadar/0.1 (RSS reader)");
        try {
            if (connection.getResponseCode() != 200) {
                throw new java.io.IOException("HTTP " + connection.getResponseCode());
            }
            try (InputStream input = connection.getInputStream()) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
                parser.setInput(input, null);
                List<Article> articles = new ArrayList<>();
                for (int event = parser.next(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                    if (event == XmlPullParser.START_TAG
                            && ("item".equals(parser.getName()) || "entry".equals(parser.getName()))) {
                        Article article = readArticle(parser, feed.getString("name"));
                        if (!article.title().isEmpty()) articles.add(article);
                        if (articles.size() == 12) break;
                    }
                }
                return articles;
            }
        } finally {
            connection.disconnect();
        }
    }

    private static Article readArticle(XmlPullParser parser, String source) throws Exception {
        int depth = parser.getDepth();
        String title = "";
        String detail = "";
        String link = "";
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.END_TAG && parser.getDepth() == depth) break;
            if (parser.getEventType() != XmlPullParser.START_TAG || parser.getDepth() != depth + 1) continue;
            switch (parser.getName()) {
                case "title" -> title = plain(readText(parser));
                case "description", "summary" -> detail = plain(readText(parser));
                case "link" -> {
                    String href = parser.getAttributeValue(null, "href");
                    String rel = parser.getAttributeValue(null, "rel");
                    if (href == null) link = readText(parser).trim();
                    else if (rel == null || "alternate".equals(rel)) link = href;
                }
            }
        }
        if (detail.length() > 320) detail = detail.substring(0, 320).trim() + "…";
        return new Article(source, title, detail, link);
    }

    private static String readText(XmlPullParser parser) throws Exception {
        int depth = parser.getDepth();
        StringBuilder text = new StringBuilder();
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.END_TAG && parser.getDepth() == depth) break;
            if (parser.getEventType() == XmlPullParser.TEXT && text.length() < 8192) {
                text.append(parser.getText());
            }
        }
        return text.toString();
    }

    private static String plain(String text) {
        return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+", " ").trim();
    }

    static boolean isSupportedArticle(Uri link) {
        String host = link.getHost();
        if (!"https".equalsIgnoreCase(link.getScheme()) || host == null || link.getUserInfo() != null
                || (link.getPort() != -1 && link.getPort() != 443)) return false;
        return "www.tagesschau.de".equalsIgnoreCase(host)
                || "www.deutschlandfunk.de".equalsIgnoreCase(host)
                || "www.heise.de".equalsIgnoreCase(host);
    }

    static Page readPage(String link) {
        if (!isSupportedArticle(Uri.parse(link))) return null;
        try {
            Document html = Jsoup.connect(link).userAgent("NewsRadar/0.1 (article reader)")
                    .followRedirects(false).timeout(7000).maxBodySize(512 * 1024).get();
            String title = html.select("meta[property=og:title]").attr("content").trim();
            if (title.isEmpty()) title = html.title();
            String detail = html.select("meta[property=og:description]").attr("content").trim();
            if (detail.isEmpty()) detail = html.select("meta[name=description]").attr("content").trim();
            if (detail.isEmpty()) {
                for (Element paragraph : html.select("article p, main p")) {
                    if (paragraph.text().length() >= 80) {
                        detail = paragraph.text();
                        break;
                    }
                }
            }
            if (detail.isEmpty()) return null;
            if (detail.length() > 480) detail = detail.substring(0, 480).trim() + "…";
            return new Page(title, detail);
        } catch (Exception failure) {
            return null;
        }
    }

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Set<String> STOP = new HashSet<>(java.util.Arrays.asList("der", "die", "das", "den", "dem", "des", "ein", "eine",
        "und", "oder", "mit", "auf", "aus", "bei", "für", "von", "zum", "zur", "nach", "sich",
        "über", "unter", "wird", "sind", "war", "hat", "haben", "soll"));

    private static Set<String> keywords(String title) {
        Set<String> words = new HashSet<>();
        var match = WORD.matcher(title.toLowerCase(Locale.GERMAN));
        while (match.find()) {
            String word = match.group();
            if (word.length() >= 3 && !STOP.contains(word)) words.add(word);
        }
        return words;
    }

    private static boolean similar(String a, String b) {
        Set<String> left = keywords(a);
        Set<String> right = keywords(b);
        long shared = left.stream().filter(right::contains).count();
        return shared >= 3 && shared * 2 >= left.size() + right.size() - shared;
    }

    static List<Story> group(List<Article> articles) {
        List<Story> stories = new ArrayList<>();
        for (Article article : articles) {
            Story story = stories.stream()
                    .filter(candidate -> candidate.articles.stream().noneMatch(item -> item.source().equals(article.source())))
                    .filter(candidate -> candidate.articles.stream().allMatch(item -> similar(item.title(), article.title())))
                    .findFirst().orElse(null);
            if (story == null) {
                story = new Story();
                stories.add(story);
            }
            story.articles.add(article);
        }
        stories.sort((left, right) -> Integer.compare(right.sourceCount(), left.sourceCount()));
        return stories;
    }
}
