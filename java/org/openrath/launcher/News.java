package org.openrath.launcher;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The top five headlines for the news box, from Google News (RSS, no account or key). Refreshed every
 * 30 minutes while there is internet; the last five are kept so the box still shows them offline.
 * read() turns them into one passage for the car's voice. parse() and read() have no Android types,
 * so they are unit-tested.
 */
final class News {

    static final String[] TOPICS = {"top", "NATION", "WORLD", "BUSINESS", "SPORTS", "TECHNOLOGY", "ENTERTAINMENT"};
    static final String[] TOPIC_NAMES = {"Top stories", "India", "World", "Business", "Sports", "Technology", "Entertainment"};
    static final int COUNT = 5;
    static final long FRESH_MS = 30L * 60 * 1000, RETRY_MS = 3L * 60 * 1000;

    static final class Item {
        final String title, source;

        Item(String title, String source) {
            this.title = title;
            this.source = source;
        }
    }

    /** The headlines on screen, newest fetch; at = when they were fetched (0 = never). */
    static volatile List<Item> items = new ArrayList<>();
    static volatile long at;
    /** Bumped on every change, so boxes redraw only then. */
    static volatile int version;
    private static volatile boolean busy;
    private static long tried;
    private static boolean loaded;

    static String url(String topic) {
        String tail = "hl=en-IN&gl=IN&ceid=IN:en";
        return "top".equals(topic) || topic == null || topic.isEmpty() ? "https://news.google.com/rss?" + tail
                : "https://news.google.com/rss/headlines/section/topic/" + topic + "?" + tail;
    }

    static String topicName(String topic) {
        for (int i = 0; i < TOPICS.length; i++) if (TOPICS[i].equals(topic)) return TOPIC_NAMES[i];
        return TOPIC_NAMES[0];
    }

    /** The first `max` items of an RSS feed. Google News titles end in " - Source", which is split off. */
    static List<Item> parse(String xml, int max) {
        List<Item> out = new ArrayList<>();
        if (xml == null) return out;
        Matcher m = Pattern.compile("<item\\b[^>]*>(.*?)</item>", Pattern.DOTALL).matcher(xml);
        while (m.find() && out.size() < max) {
            String body = m.group(1);
            String title = clean(tag(body, "title"));
            String source = clean(tag(body, "source"));
            if (title.isEmpty()) continue;
            if (!source.isEmpty() && title.endsWith(" - " + source)) title = title.substring(0, title.length() - source.length() - 3).trim();
            else if (source.isEmpty()) {
                int d = title.lastIndexOf(" - ");
                if (d > 20 && title.length() - d < 40) {
                    source = title.substring(d + 3).trim();
                    title = title.substring(0, d).trim();
                }
            }
            out.add(new Item(title, source));
        }
        return out;
    }

    private static String tag(String body, String name) {
        Matcher m = Pattern.compile("<" + name + "\\b[^>]*>(.*?)</" + name + ">", Pattern.DOTALL).matcher(body);
        return m.find() ? m.group(1) : "";
    }

    static String clean(String s) {
        s = s.trim();
        if (s.startsWith("<![CDATA[") && s.endsWith("]]>")) s = s.substring(9, s.length() - 3);
        s = s.replaceAll("<[^>]+>", "");
        Matcher m = Pattern.compile("&#(x?)([0-9a-fA-F]+);").matcher(s);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            int cp;
            try {
                cp = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            } catch (NumberFormatException e) {
                cp = '?';
            }
            m.appendReplacement(b, Matcher.quoteReplacement(new String(Character.toChars(cp))));
        }
        m.appendTail(b);
        return b.toString().replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&nbsp;", " ").replace("&amp;", "&").replaceAll("\\s+", " ").trim();
    }

    /** What the voice says: "Here are the top 5 stories. One. … Two. …". */
    static String read(List<Item> list, String topicName) {
        if (list == null || list.isEmpty()) return "There's no news to read yet.";
        String[] n = {"One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight"};
        StringBuilder b = new StringBuilder("Here are the " + ("Top stories".equals(topicName) ? "top" : topicName) + " headlines. ");
        for (int i = 0; i < list.size() && i < n.length; i++) {
            Item it = list.get(i);
            b.append(n[i]).append(". ").append(it.title);
            if (!it.title.endsWith(".") && !it.title.endsWith("?") && !it.title.endsWith("!")) b.append('.');
            if (!it.source.isEmpty()) b.append(" From ").append(it.source).append('.');
            b.append(' ');
        }
        return b.toString().trim();
    }

    /** One headline, for tapping it in the box. */
    static String read(Item it) {
        return it.title + (it.source.isEmpty() ? "" : ". From " + it.source) + ".";
    }

    /** Load the kept headlines once, then fetch new ones if they are old and there is internet. */
    static void refresh(final Context c, final Prefs p, boolean force) {
        if (!loaded) {
            loaded = true;
            List<Item> l = new ArrayList<>();
            for (String line : p.str("newsCache", "").split("\n")) {
                int t = line.indexOf('\t');
                if (t > 0) l.add(new Item(line.substring(0, t), line.substring(t + 1)));
            }
            if (!l.isEmpty()) {
                items = l;
                at = p.longNum("newsAt", 0);
                version++;
            }
        }
        long now = System.currentTimeMillis();
        if (busy || !Features.on(p, "news")) return;
        if (!force && (now - at < FRESH_MS || now - tried < RETRY_MS)) return;
        if (!Player.online(c)) return;
        tried = now;
        busy = true;
        final String topic = p.str("newsTopic", "top");
        Bg.run(new Runnable() {
            @Override
            public void run() {
                try {
                    List<Item> l = parse(get(url(topic)), COUNT);
                    if (!l.isEmpty()) {
                        StringBuilder b = new StringBuilder();
                        for (Item it : l) b.append(it.title.replace('\t', ' ')).append('\t').append(it.source.replace('\n', ' ')).append('\n');
                        items = l;
                        at = System.currentTimeMillis();
                        p.put("newsCache", b.toString());
                        p.putLong("newsAt", at);
                        version++;
                    }
                } catch (Exception ignored) {
                    // offline or blocked: keep the last headlines
                } finally {
                    busy = false;
                }
            }
        });
    }

    /** "Updated 12 min ago", for the box. */
    static String age(long now) {
        if (at <= 0) return "";
        long m = (now - at) / 60000;
        if (m < 1) return Hindi.t("just now");
        if (m < 60) return Hindi.f("%1$s min ago", m);
        long h = m / 60;
        return h < 48 ? Hindi.f("%1$s h ago", h) : Hindi.f("%1$s days ago", h / 24);
    }

    private static String get(String url) throws java.io.IOException {
        HttpURLConnection h = Compat.https(url);
        h.setConnectTimeout(10000);
        h.setReadTimeout(10000);
        h.setRequestProperty("User-Agent", "OpenRath (car head unit; open source home screen)");
        InputStream in = h.getInputStream();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
        in.close();
        return o.toString("UTF-8");
    }

    private News() {
    }
}
