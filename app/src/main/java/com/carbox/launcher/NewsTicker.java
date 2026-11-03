package com.carbox.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/** News / traffic headlines scrolling above the dock. Tap to switch feeds. */
final class NewsTicker {
    private final Context c;
    final LinearLayout view;
    private final TextView tag;
    private final Marquee marquee;
    private final Ui.Ring ring;
    private final List<String> news = new ArrayList<>(), traffic = new ArrayList<>();
    private final List<Item> newsItems = new ArrayList<>(), trafficItems = new ArrayList<>();
    private long newsAt, trafficAt; // wall clock of last successful load

    static final class Item {
        String title, link, desc, source;
        long date;
    }

    // ---- what you've done with each headline: read (grey, at the bottom), deleted (gone), archived (kept)
    private static final String READ = "news_read", GONE = "news_deleted", ARCH = "news_archived";

    private static String key(Item it) {
        return it.link != null && !it.link.isEmpty() ? it.link : it.title;
    }

    private static boolean in(String set, Item it) {
        return Prefs.list(set).contains(key(it));
    }

    private static void add(String set, Item it) {
        List<String> l = new ArrayList<>(Prefs.list(set));
        if (l.contains(key(it))) return;
        l.add(0, key(it));
        while (l.size() > 400) l.remove(l.size() - 1); // old ones drop off (they're past their day anyway)
        Prefs.putList(set, l);
    }

    static boolean read(Item it) {
        return in(READ, it);
    }

    static boolean deleted(Item it) {
        return in(GONE, it);
    }

    static boolean archived(Item it) {
        for (Item a : archivedItems()) if (key(a).equals(key(it))) return true;
        return false;
    }

    /** Published today (an undated headline counts from the day it was first fetched). */
    static boolean today(Item it) {
        java.util.Calendar k = java.util.Calendar.getInstance();
        k.set(java.util.Calendar.HOUR_OF_DAY, 0);
        k.set(java.util.Calendar.MINUTE, 0);
        k.set(java.util.Calendar.SECOND, 0);
        k.set(java.util.Calendar.MILLISECOND, 0);
        return it.date >= k.getTimeInMillis();
    }

    /** Headlines without a publish date: dated the day they were first fetched (kept from the last poll). */
    private static void dateUndated(List<Item> fresh, List<Item> old) {
        long now = System.currentTimeMillis();
        for (Item it : fresh) {
            if (it.date > 0) continue;
            it.date = now;
            for (Item o : old) if (key(o).equals(key(it)) && o.date > 0) it.date = o.date;
        }
    }

    /** Archived headlines (kept until you delete them). */
    static List<Item> archivedItems() {
        List<Item> l = new ArrayList<>();
        org.json.JSONArray a = Prefs.arr(ARCH);
        for (int i = 0; i < a.length(); i++) {
            org.json.JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Item it = new Item();
            it.title = o.optString("t", "");
            it.link = o.optString("l", null);
            it.source = o.optString("s", null);
            it.date = o.optLong("d", 0);
            it.desc = o.optString("x", null);
            l.add(it);
        }
        return l;
    }

    private static void saveArchive(List<Item> l) {
        org.json.JSONArray a = new org.json.JSONArray();
        try {
            for (Item it : l) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("t", it.title);
                o.put("l", it.link);
                o.put("s", it.source);
                o.put("d", it.date);
                if (it.desc != null) o.put("x", it.desc);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        Prefs.put(ARCH, a);
    }

    static void archive(Item it) {
        List<Item> l = archivedItems();
        for (Item a : l) if (key(a).equals(key(it))) return;
        l.add(0, it);
        saveArchive(l);
    }

    static void delete(Item it) {
        List<Item> l = archivedItems();
        boolean was = l.removeIf(a -> key(a).equals(key(it)));
        if (was) saveArchive(l);
        else add(GONE, it);
    }

    /** Out of the bin: back in today's list. */
    static void restore(Item it) {
        List<String> l = new ArrayList<>(Prefs.list(GONE));
        if (l.remove(key(it))) Prefs.putList(GONE, l);
    }

    private static final String READ_AT = "news_read_at"; // when each headline was read (for the 24 h rule)
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;

    static void markRead(Item it) {
        add(READ, it);
        try { // remember when, the first time; forget entries older than a few days
            org.json.JSONObject o = new org.json.JSONObject(Prefs.str(READ_AT, "{}"));
            long now = System.currentTimeMillis();
            if (!o.has(key(it))) o.put(key(it), now);
            java.util.Iterator<String> k = o.keys();
            List<String> old = new ArrayList<>();
            while (k.hasNext()) {
                String x = k.next();
                if (now - o.optLong(x, now) > 3 * DAY_MS) old.add(x);
            }
            for (String x : old) o.remove(x);
            Prefs.put(READ_AT, o.toString());
        } catch (Exception ignored) {
        }
    }

    /** When it was read (0: unread, or read before this was kept). */
    static long readAt(Item it) {
        try {
            return new org.json.JSONObject(Prefs.str(READ_AT, "{}")).optLong(key(it), 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /** In the normal list: unread headlines are today's; read ones stay (greyed) for 24 hours after reading. */
    static boolean inList(Item it) {
        if (deleted(it) || archived(it)) return false;
        if (!read(it)) return today(it);
        long at = readAt(it);
        return at > 0 ? System.currentTimeMillis() - at < DAY_MS : today(it);
    }
    private boolean showingNews = true;
    private long manualUntil;     // elapsedRealtime; 0 = automatic
    private long manualStart;
    private long switchedAt;
    private long fetchedAt;
    private boolean running;

    NewsTicker(Context c) {
        this.c = c;
        view = Ui.row(c);
        view.setBackground(Themes.card(Ui.CARD, 12));
        Ui.pad(view, 8, 0, 12, 0);
        tag = Ui.text(c, "NEWS", 12, Ui.TEXT, true);
        tag.setLetterSpacing(0.04f);
        Ui.pad(tag, 7, 3, 7, 3);
        view.addView(tag);
        marquee = new Marquee(c);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, Ui.MATCH, 1);
        mlp.leftMargin = Ui.u(10);
        view.addView(marquee, mlp);
        // playback and text-size live in the expanded window (open the ticker), so the bar stays clear
        ring = new Ui.Ring(c);
        ring.setVisibility(View.GONE);
        LinearLayout.LayoutParams rlp = Ui.lpu(18, 18);
        rlp.leftMargin = Ui.u(8);
        view.addView(ring, rlp);
        // the NEWS / TRAFFIC label switches feeds; the headlines open the feed for more detail
        tag.setOnClickListener(v -> tapped());
        view.setOnClickListener(v -> { // the headlines open the feed; in lyrics mode, the lyrics
            if (lyrics) LyricsView.show();
            else openFeed();
        });
        String mode = Prefs.str(Prefs.TICKER_MODE, "alt");
        showingNews = !"traffic".equals(mode);
        lyrics = !newsOn() && !trafficOn() && lyricsOn(); // only lyrics chosen
        applyTag();
        // the headlines from the last poll, straight away (new ones replace them when they arrive)
        newsAt = load("ticker_news", newsItems, news);
        trafficAt = load("ticker_traffic", trafficItems, traffic);
        updateText();
    }

    private static void save(String key, List<Item> items, long at) {
        org.json.JSONArray a = new org.json.JSONArray();
        try {
            for (int i = 0; i < items.size() && i < 40; i++) {
                Item it = items.get(i);
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("t", it.title);
                o.put("l", it.link);
                o.put("s", it.source);
                o.put("d", it.date);
                if (it.desc != null) o.put("x", it.desc.length() > 300 ? it.desc.substring(0, 300) : it.desc);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        Prefs.put(key, a);
        Prefs.put(key + "_at", at);
    }

    private static long load(String key, List<Item> items, List<String> titles) {
        org.json.JSONArray a = Prefs.arr(key);
        for (int i = 0; i < a.length(); i++) {
            org.json.JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Item it = new Item();
            it.title = o.optString("t", "");
            it.link = o.optString("l", null);
            it.source = o.optString("s", null);
            it.date = o.optLong("d", 0);
            it.desc = o.optString("x", null);
            items.add(it);
            titles.add(it.title);
        }
        return items.isEmpty() ? 0 : Prefs.lng(key + "_at", 0);
    }

    // ---- while polling, the NEWS / TRAFFIC label breathes
    private int pending;
    private android.animation.ObjectAnimator pulse;

    private void busy(int d) {
        pending = Math.max(0, pending + d);
        if (pending > 0 && pulse == null && Ui.ms(1) > 0) {
            pulse = android.animation.ObjectAnimator.ofFloat(tag, View.ALPHA, 1f, 0.35f);
            pulse.setDuration(600);
            pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            pulse.start();
        } else if (pending == 0 && pulse != null) {
            pulse.cancel();
            pulse = null;
            tag.setAlpha(1f);
        }
    }

    void start() {
        if (running) return;
        running = true;
        Ui.ui.post(loop);
        marquee.run(true);
    }

    void stop() {
        running = false;
        Ui.ui.removeCallbacks(loop);
        marquee.run(false);
    }

    // the label goes round: NEWS → TRAFFIC → LYRICS (the song's words, line by line) → NEWS
    private boolean lyrics;
    private int lyricShown = -2;
    private String lyricKey = "";

    static final String NEWS_ON = "ticker_news_on", TRAFFIC_ON = "ticker_traffic_on", LYRICS_ON = "ticker_lyrics_on";

    static boolean newsOn() {
        return Prefs.flag(NEWS_ON, true);
    }

    static boolean trafficOn() {
        return Prefs.flag(TRAFFIC_ON, true);
    }

    static boolean lyricsOn() {
        return Prefs.flag(LYRICS_ON, true);
    }

    /** Switch News, Traffic or Lyrics on or off (Settings → News Ticker); the ticker moves to one that's on. */
    static void setOn(String key, boolean on) {
        Prefs.put(key, on);
        // alternating only makes sense with both News and Traffic on
        Prefs.put(Prefs.TICKER_MODE, newsOn() && trafficOn() ? "alt" : newsOn() ? "news" : trafficOn() ? "traffic" : "alt");
        if (MainActivity.I != null) MainActivity.I.ticker.fixMode();
    }

    /** Keep the ticker on something that's switched on. */
    void fixMode() {
        if (lyrics && !lyricsOn()) lyrics = false;
        if (!lyrics) {
            if (showingNews && !newsOn() && trafficOn()) showingNews = false;
            else if (!showingNews && !trafficOn() && newsOn()) showingNews = true;
            else if (!newsOn() && !trafficOn() && lyricsOn()) lyrics = true;
        }
        lyricShown = -2;
        applyTag();
        updateText();
    }

    private void tapped() {
        // go round the ones that are on: News → Traffic → Lyrics
        int cur = lyrics ? 2 : showingNews ? 0 : 1;
        boolean[] on = {newsOn(), trafficOn(), lyricsOn()};
        int next = cur;
        for (int i = 1; i <= 3; i++) if (on[(cur + i) % 3]) {
            next = (cur + i) % 3;
            break;
        }
        if (next == cur) return;
        if (next == 2) {
            lyrics = true; // stays on lyrics until tapped again
            lyricShown = -2;
            applyTag();
            updateText();
            ring.setVisibility(View.GONE);
            return;
        }
        lyrics = false;
        showingNews = next == 0;
        int back = Prefs.num(Prefs.TICKER_BACK, 60);
        String mode = Prefs.str(Prefs.TICKER_MODE, "alt");
        manualStart = SystemClock.elapsedRealtime();
        manualUntil = back <= 0 || !"alt".equals(mode) ? Long.MAX_VALUE : manualStart + back * 1000L;
        if (!"alt".equals(mode)) manualUntil = manualStart + Math.max(back, 30) * 1000L;
        applyTag();
        updateText();
    }

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.elapsedRealtime();
            if (fetchedAt == 0 || now - fetchedAt > 15 * 60 * 1000L) fetch();
            if (lyrics) { // follow the song: the line being sung now
                showLyric();
                if (running) Ui.ui.postDelayed(this, 250);
                return;
            }
            String mode = Prefs.str(Prefs.TICKER_MODE, "alt");
            boolean manual = manualUntil > now;
            if (manualUntil != 0 && !manual) {
                manualUntil = 0;
                if (!"alt".equals(mode)) {
                    showingNews = !"traffic".equals(mode);
                    applyTag();
                    updateText();
                }
            }
            if (manual && manualUntil != Long.MAX_VALUE) {
                ring.setVisibility(View.VISIBLE);
                ring.set((now - manualStart) / (float) (manualUntil - manualStart), null, Ui.ACCENT);
            } else ring.setVisibility(View.GONE);
            if (!manual && "alt".equals(mode)) {
                int every = Prefs.num(Prefs.TICKER_EVERY, 60);
                if (now - switchedAt > every * 1000L) {
                    switchedAt = now;
                    showingNews = !showingNews;
                    applyTag();
                    updateText();
                }
            }
            if (running) Ui.ui.postDelayed(this, 250);
        }
    };

    private void applyTag() {
        if (lyrics) {
            tag.setText("LYRICS");
            tag.setTextColor(Ui.TEXT);
            tag.setBackground(Ui.round(0xFF7A4FD0, 6));
            return;
        }
        tag.setText(showingNews ? "NEWS" : "TRAFFIC");
        tag.setTextColor(showingNews ? Ui.TEXT : 0xFF3A2600);
        tag.setBackground(Ui.round(showingNews ? Ui.ACCENT : Ui.AMBER, 6));
    }

    /** Lyrics mode: the line being sung, scrolling across; the song's name when there are no lyrics. */
    private void showLyric() {
        NowPlaying np = NowPlaying.I;
        String key = np == null ? "" : np.lyricKey();
        int idx = np == null || np.lyricCount() == 0 ? -1 : np.currentLyric();
        if (idx == lyricShown && key.equals(lyricKey)) return;
        lyricShown = idx;
        lyricKey = key;
        String line;
        if (np == null || MediaListener.controller == null) line = "Nothing playing";
        else if (np.lyricCount() == 0) line = "♪  " + np.songTitle() + " · " + np.songArtist() + "  ·  no lyrics for this song";
        else if (idx < 0) line = "♪  " + np.songTitle() + " · " + np.songArtist();
        else {
            String l = np.lyricLine(idx);
            line = l.isEmpty() ? "♪" : l;
            if (idx + 1 < np.lyricCount() && !np.lyricLine(idx + 1).isEmpty()) line += "     ·     " + np.lyricLine(idx + 1); // and the next one
        }
        marquee.setItems(java.util.Collections.singletonList(line));
    }

    private void updateText() {
        if (lyrics) {
            lyricShown = -2;
            showLyric();
            return;
        }
        List<String> l = new ArrayList<>();
        for (Item it : showingNews ? newsItems : trafficItems) if (today(it) && !read(it) && !deleted(it) && !archived(it)) l.add(it.title);
        List<String> last = showingNews ? lastNews : lastTraffic;
        Ui.ui.removeCallbacks(loadingOver);
        if (!l.isEmpty()) {
            last.clear();
            last.addAll(l);
            marquee.setItems(l);
        } else if (!last.isEmpty()) marquee.setItems(last); // nothing new yet: keep showing the last feed
        else { // nothing at all yet: "Loading" for 2 s at most, then whatever headlines were saved last
            marquee.setItems(java.util.Collections.singletonList(Gps.has() ? "Loading headlines…" : "Waiting for location to find local headlines"));
            Ui.ui.postDelayed(loadingOver, 2000);
        }
    }

    private final List<String> lastNews = new ArrayList<>(), lastTraffic = new ArrayList<>();
    private final Runnable loadingOver = this::loadingDone;

    private void loadingDone() {
        List<String> l = new ArrayList<>();
        for (Item it : showingNews ? newsItems : trafficItems) if (!deleted(it)) l.add(it.title); // the last feed, even if older
        if (l.isEmpty()) l.add(showingNews ? "No new headlines today" : "No traffic news right now");
        marquee.setItems(l);
    }

    private String place() {
        return Gps.town != null ? Gps.town : "South Africa";
    }

    void fetch() {
        fetchedAt = SystemClock.elapsedRealtime();
        String newsUrl = Prefs.str(Prefs.NEWS_URL, "");
        String trafficUrl = Prefs.str(Prefs.TRAFFIC_URL, "");
        if (newsUrl.isEmpty()) newsUrl = "https://news.google.com/rss/search?q=" + Uri.encode(place()) + "&hl=en-ZA&gl=ZA&ceid=ZA:en";
        if (trafficUrl.isEmpty()) trafficUrl = "https://news.google.com/rss/search?q=" + Uri.encode("traffic " + place()) + "+when:2d&hl=en-ZA&gl=ZA&ceid=ZA:en";
        busy(1);
        Net.get(newsUrl, null, (b, code) -> {
            busy(-1);
            if (b != null) {
                List<Item> items = parseItems(b);
                if (items.isEmpty()) return;
                dateUndated(items, newsItems);
                newsItems.clear();
                newsItems.addAll(items);
                news.clear();
                for (Item it : items) news.add(it.title);
                newsAt = System.currentTimeMillis();
                save("ticker_news", newsItems, newsAt);
                if (showingNews) updateText();
            }
        });
        busy(1);
        Net.get(trafficUrl, null, (b, code) -> {
            busy(-1);
            if (b != null) {
                List<Item> items = parseItems(b);
                if (items.isEmpty() && !trafficItems.isEmpty()) return; // keep the old ones
                dateUndated(items, trafficItems);
                trafficItems.clear();
                trafficItems.addAll(items);
                traffic.clear();
                for (Item it : items) traffic.add(it.title);
                trafficAt = System.currentTimeMillis();
                save("ticker_traffic", trafficItems, trafficAt);
                if (!showingNews) updateText();
            }
        });
        String key = Prefs.str(Prefs.TOMTOM_KEY, "");
        if (!key.isEmpty() && Gps.has()) {
            String bbox = String.format(java.util.Locale.US, "%.3f,%.3f,%.3f,%.3f", Gps.lon - 0.25, Gps.lat - 0.25, Gps.lon + 0.25, Gps.lat + 0.25);
            String url = "https://api.tomtom.com/traffic/services/5/incidentDetails?key=" + Uri.encode(key) + "&bbox=" + bbox
                    + "&fields=%7Bincidents%7Bproperties%7Bevents%7Bdescription%7D,from,to,delay%7D%7D%7D&language=en-GB&timeValidityFilter=present";
            busy(1);
            Net.get(url, null, (b, code) -> {
                busy(-1);
                if (b == null) return;
                try {
                    org.json.JSONArray inc = new org.json.JSONObject(b).getJSONArray("incidents");
                    List<String> live = new ArrayList<>();
                    for (int i = 0; i < inc.length() && live.size() < 12; i++) {
                        org.json.JSONObject p = inc.getJSONObject(i).getJSONObject("properties");
                        String d = p.getJSONArray("events").getJSONObject(0).optString("description");
                        String from = p.optString("from"), to = p.optString("to");
                        int delay = p.optInt("delay", 0);
                        String line = d + (from.isEmpty() ? "" : ": " + from + (to.isEmpty() ? "" : " to " + to)) + (delay > 60 ? " · " + delay / 60 + " min delay" : "");
                        live.add(line);
                        Item it = new Item();
                        it.title = line;
                        it.source = "TomTom live traffic";
                        it.date = System.currentTimeMillis();
                        trafficItems.add(live.size() - 1, it);
                    }
                    if (!live.isEmpty()) {
                        traffic.addAll(0, live);
                        if (!showingNews) updateText();
                    }
                } catch (Exception ignored) {
                }
            });
        }
    }

    static List<Item> parseItems(String xml) {
        List<Item> out = new ArrayList<>();
        try {
            XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
            p.setInput(new StringReader(xml));
            Item cur = null;
            String tagName = null;
            StringBuilder text = new StringBuilder();
            for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT && out.size() < 20; e = p.next()) {
                if (e == XmlPullParser.START_TAG) {
                    tagName = p.getName();
                    text.setLength(0);
                    if ("item".equals(tagName) || "entry".equals(tagName)) cur = new Item();
                    if (cur != null && "link".equals(tagName) && p.getAttributeValue(null, "href") != null) cur.link = p.getAttributeValue(null, "href");
                } else if (e == XmlPullParser.TEXT || e == XmlPullParser.CDSECT) {
                    text.append(p.getText());
                } else if (e == XmlPullParser.END_TAG) {
                    String n = p.getName(), t = text.toString().trim();
                    if (cur != null) {
                        if ("title".equals(n)) cur.title = t;
                        else if ("link".equals(n) && !t.isEmpty()) cur.link = t;
                        else if ("description".equals(n) || "summary".equals(n)) cur.desc = clean(t);
                        else if ("source".equals(n)) cur.source = t;
                        else if ("pubDate".equals(n) || "updated".equals(n) || "published".equals(n)) cur.date = date(t);
                        else if ("item".equals(n) || "entry".equals(n)) {
                            if (cur.title != null && !cur.title.isEmpty()) {
                                // Google News appends " - Source"
                                int dash = cur.title.lastIndexOf(" - ");
                                if (dash > 20) {
                                    if (cur.source == null) cur.source = cur.title.substring(dash + 3);
                                    cur.title = cur.title.substring(0, dash);
                                }
                                if (cur.desc != null && (cur.desc.startsWith(cur.title) || cur.desc.length() < 12)) cur.desc = null;
                                out.add(cur);
                            }
                            cur = null;
                        }
                    }
                    text.setLength(0);
                    tagName = null;
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static List<String> parse(String xml) {
        List<String> l = new ArrayList<>();
        for (Item it : parseItems(xml)) l.add(it.title);
        return l;
    }

    private static String clean(String html) {
        if (html == null) return ""; // some feeds have no description/content: don't hand null to Html.fromHtml
        String t = android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT).toString().replace('\uFFFC', ' ').trim();
        t = t.replaceAll("\\s+", " ");
        return t.length() > 220 ? t.substring(0, 217) + "…" : t;
    }

    private static long date(String s) {
        String[] fmts = {"EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd MMM yyyy HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'"};
        for (String f : fmts) {
            try {
                java.text.SimpleDateFormat d = new java.text.SimpleDateFormat(f, java.util.Locale.US);
                if (f.endsWith("'Z'")) d.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                return d.parse(s).getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    static String ago(long t) {
        if (t <= 0) return "";
        long m = Math.max(0, (System.currentTimeMillis() - t) / 60000);
        if (m < 1) return "just now";
        if (m < 60) return m + " min ago";
        if (m < 48 * 60) return (m / 60) + " h ago";
        return (m / 1440) + " days ago";
    }

    // ------------------------------------------------------------------ feed detail

    /**
     * Once per car start: show the traffic feed in the tile area for 15 seconds (it closes after
     * 15 s without a touch, like other panels). Waits up to a minute for the feed to load.
     */
    void trafficAtStart(int tries) {
        if (!Prefs.flag("traffic_start", true)) return;
        if (trafficItems.isEmpty()) {
            if (tries < 12) Ui.ui.postDelayed(() -> trafficAtStart(tries + 1), 5000);
            return;
        }
        if (Panels.open() || CallState.state != CallState.NONE) return; // don't interrupt
        boolean was = showingNews;
        showingNews = false;
        slideshow(true); // traffic at start: plays its unread headlines, then the list
        showingNews = was;
        if (MainActivity.I != null) MainActivity.I.resetIdle();
    }

    private boolean feedNews = true; // which feed the open window shows (the NEWS / TRAFFIC label switches it)
    private int feedView; // 0 today's headlines, 1 archive, 2 bin

    // ---- text size: one magnifier cycles three sizes (small, medium, large), applied everywhere the feed
    // shows \u2014 the scrolling ticker, the list, the playback slides and the article view.
    static int fontLevel() {
        return Math.max(0, Math.min(2, Prefs.num("ticker_font_level", 1)));
    }

    /** Pick a size for the current level: small / medium / large. */
    static float lvlSize(float sm, float md, float lg) {
        int l = fontLevel();
        return l == 0 ? sm : l == 1 ? md : lg;
    }

    /** Next of the three sizes; refresh the scrolling ticker (callers refresh their own view). */
    static void nextFont() {
        Prefs.put("ticker_font_level", (fontLevel() + 1) % 3);
        if (MainActivity.I != null && MainActivity.I.ticker != null) MainActivity.I.ticker.marquee.applyFont();
    }

    // ---- Playback: each headline shown big on the Centre Stage for 5 s, with tick / archive / delete
    private List<Item> slide;
    private int slideIdx;
    private boolean slidePaused;
    private boolean slideAutoList; // auto-play on opening the ticker: show the list once the round is done
    private boolean slideNews = true; // which feed is playing (news or traffic)
    private static final long SLIDE_MS = 10000; // each headline stays 10 s
    private final Runnable slideTick = new Runnable() {
        @Override
        public void run() {
            if (slide == null || slide.isEmpty() || slidePaused) return;
            if (slideIdx + 1 >= slide.size()) { // finished the last headline
                if (slideAutoList) { feedList(slideNews); return; } // auto-play done → open the list
                slideIdx = 0; // a manual replay just loops
            } else slideIdx++;
            Panels.refresh();
            Ui.ui.postDelayed(this, SLIDE_MS);
        }
    };

    private void buildSlides() {
        slide = new ArrayList<>();
        for (Item it : slideNews ? newsItems : trafficItems) // only the ones you haven't read yet
            if (today(it) && !read(it) && !deleted(it) && !archived(it)) slide.add(it);
    }

    void slideshow() {
        slideshow(false);
    }

    /** autoList: play once through, then drop to the list (used when the ticker is opened). */
    void slideshow(boolean autoList) {
        slideNews = showingNews;
        buildSlides();
        if (slide.isEmpty()) {
            if (autoList) { feedList(slideNews); return; } // nothing to play: straight to the list
            android.widget.Toast.makeText(c, "No headlines to play", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        slideIdx = 0;
        slidePaused = false;
        slideAutoList = autoList;
        Ui.ui.post(() -> Panels.sticky = true); // stays until you close it
        Panels.show(this::slidePage);
        Ui.ui.post(() -> Panels.whenClosed(() -> {
            Ui.ui.removeCallbacks(slideTick);
            slide = null;
        }));
        Ui.ui.removeCallbacks(slideTick);
        Ui.ui.postDelayed(slideTick, SLIDE_MS);
    }

    private void slideAct(Runnable act, boolean remove) {
        if (slide == null || slide.isEmpty()) return;
        Item it = slide.get(slideIdx);
        act.run();
        if (remove) {
            slide.remove(slideIdx);
            updateText();
            if (slide.isEmpty()) { // nothing left to play
                if (slideAutoList) feedList(slideNews);
                else Panels.close();
                return;
            }
            if (slideIdx >= slide.size()) slideIdx = 0;
        } else if (slideIdx + 1 >= slide.size() && slideAutoList) { // past the last one: the list
            feedList(slideNews);
            return;
        } else slideIdx = (slideIdx + 1) % slide.size();
        Panels.refresh();
        Ui.ui.removeCallbacks(slideTick);
        Ui.ui.postDelayed(slideTick, SLIDE_MS);
    }

    private View slidePage() {
        Context c = Panels.ctx();
        // swipe left: next headline (this one counts as read); swipe right: archive it
        LinearLayout b = new LinearLayout(c) {
            private float x0, y0;
            private boolean swiping;

            @Override
            public boolean dispatchTouchEvent(android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        x0 = e.getRawX();
                        y0 = e.getRawY();
                        swiping = false;
                        break;
                    case android.view.MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - x0, dy = e.getRawY() - y0;
                        if (!swiping && Math.abs(dx) > Ui.u(24) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                            swiping = true; // a swipe, not a tap: the headline and buttons let go
                            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                            android.view.MotionEvent cancel = android.view.MotionEvent.obtain(e);
                            cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);
                            super.dispatchTouchEvent(cancel);
                            cancel.recycle();
                        }
                        if (swiping) {
                            setTranslationX(dx * 0.6f);
                            setAlpha(Math.max(0.35f, 1 - Math.abs(dx) / Math.max(1, getWidth())));
                            return true;
                        }
                        break;
                    }
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        if (swiping) {
                            float dx = e.getRawX() - x0;
                            swiping = false;
                            animate().translationX(0).alpha(1f).setDuration(Ui.ms(160)).start();
                            if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP && Math.abs(dx) > Ui.u(70)) {
                                Item cur = slide != null && slideIdx < slide.size() ? slide.get(slideIdx) : null;
                                if (cur != null) Ui.ui.post(dx < 0 ? () -> slideAct(() -> markRead(cur), false)
                                        : () -> slideAct(() -> archive(cur), true));
                            }
                            return true;
                        }
                        break;
                }
                return super.dispatchTouchEvent(e);
            }
        };
        b.setOrientation(LinearLayout.VERTICAL);
        if (slide == null || slide.isEmpty()) {
            b.addView(Ui.text(c, "Nothing to play.", 15, Ui.SUB, false));
            return Panels.panel("Playback", b, 660, 440);
        }
        if (slideIdx >= slide.size()) slideIdx = 0;
        Item it = slide.get(slideIdx);
        // the headline (and story) centred in the window, with the buttons pinned along the bottom
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        b.addView(new View(c), new LinearLayout.LayoutParams(Ui.MATCH, 0, 1)); // space above: centres the text
        TextView h = Ui.text(c, it.title, lvlSize(24, 28, 33), Ui.TEXT, true);
        h.setGravity(Gravity.CENTER);
        h.setLineSpacing(Ui.uf(3), 1);
        h.setOnClickListener(v -> feedList(slideNews)); // tap the headline to skip straight to the list
        b.addView(h, Ui.lp(Ui.MATCH, Ui.WRAP));
        String m = (it.source != null ? it.source : "") + (it.date > 0 ? (it.source != null ? " \u00b7 " : "") + ago(it.date) : "");
        if (!m.isEmpty()) {
            TextView mv = Ui.line(c, m, 13, Ui.MUTED, false);
            mv.setGravity(Gravity.CENTER);
            b.addView(mv, Widgets.top(8));
        }
        String d = clean(it.desc);
        if (!d.isEmpty() && !d.equalsIgnoreCase(it.title)) {
            TextView dt = Ui.text(c, d, lvlSize(15, 17, 20), Ui.TEXT2, false);
            dt.setGravity(Gravity.CENTER);
            dt.setLineSpacing(Ui.uf(5), 1);
            dt.setMaxLines(8); // the stage is roomy: show more of the story
            b.addView(dt, Widgets.top(14));
        }
        b.addView(new View(c), new LinearLayout.LayoutParams(Ui.MATCH, 0, 1)); // space below: centres the text
        // footer along the bottom: pause/resume and which slide on the left; read, archive, delete on the right
        LinearLayout foot = Ui.row(c);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.addView(Ui.iconButton(c, slidePaused ? R.drawable.ic_play : R.drawable.ic_pause,
                slidePaused ? "Resume" : "Pause", () -> {
                    slidePaused = !slidePaused;
                    Ui.ui.removeCallbacks(slideTick);
                    if (!slidePaused) Ui.ui.postDelayed(slideTick, SLIDE_MS);
                    Panels.refresh();
                }));
        foot.addView(Ui.gap(c, 12, 1));
        foot.addView(Ui.line(c, (slideIdx + 1) + " / " + slide.size() + (slidePaused ? "  \u00b7  paused" : ""), 13, Ui.SUB, true));
        foot.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
        foot.addView(Ui.iconButton(c, R.drawable.ic_check, "Mark read", () -> slideAct(() -> markRead(it), false)));
        foot.addView(Ui.gap(c, 10, 1));
        foot.addView(Ui.iconButton(c, R.drawable.ic_archive, "Archive", () -> slideAct(() -> archive(it), true)));
        foot.addView(Ui.gap(c, 10, 1));
        foot.addView(Ui.iconButton(c, R.drawable.ic_trash, "Delete", () -> slideAct(() -> delete(it), true)));
        b.addView(foot, Widgets.top(14));
        LinearLayout pan = Panels.panel("Playback", b, 660, 440);
        // the window's scroll area fills it, so the text sits in the middle and the buttons along the bottom
        if (b.getParent() instanceof android.widget.ScrollView) ((android.widget.ScrollView) b.getParent()).setFillViewport(true);
        return pan;
    }

    /** Headline playback is on screen. */
    boolean playing() {
        return slide != null;
    }

    /** The headline list (when the Tile Drawer goes after covering playback). */
    void openList() {
        feedList(slideNews);
    }

    /** Opening the ticker: play the headlines through once, then drop to the list. */
    private void openFeed() {
        feedNews = showingNews;
        slideshow(true);
    }

    private void feedList() {
        feedList(showingNews);
    }

    private void feedList(boolean news) {
        feedNews = news;
        feedView = 0;
        Ui.ui.removeCallbacks(slideTick); // stop playback if we came from it
        Ui.ui.post(() -> Panels.sticky = true); // news and traffic stay until you close them
        Panels.showSlide(() -> { // slides down out of the ticker, back up when closed
            Context c = Panels.ctx();
            boolean n = feedNews;
            List<Item> items = n ? newsItems : trafficItems;
            LinearLayout card = Ui.col(c);
            card.setBackground(Themes.card(Ui.CARD, 16));
            Ui.pad(card, 26, 20, 26, 16);
            card.setLayoutParams(Panels.centered(660, 440));
            LinearLayout head = Ui.row(c);
            head.setGravity(Gravity.CENTER_VERTICAL);
            TextView tg = Ui.text(c, n ? "NEWS" : "TRAFFIC", 12, n ? Ui.TEXT : 0xFF3A2600, true);
            tg.setBackground(Ui.round(n ? Ui.ACCENT : Ui.AMBER, 6));
            Ui.pad(tg, 9, 3, 9, 3);
            tg.setContentDescription(n ? "Switch to traffic" : "Switch to news");
            tg.setOnClickListener(v -> { // switch feeds right here
                feedNews = !feedNews;
                feedView = 0;
                Panels.refresh();
            });
            Ui.press(tg);
            head.addView(tg);
            TextView title = Ui.line(c, feedView == 1 ? "Archive" : feedView == 2 ? "Bin" : n ? "Local news" : "Traffic", 20, Ui.TEXT, true);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
            tl.leftMargin = Ui.u(12);
            head.addView(title, tl);
            View ed = Ui.icon(c, R.drawable.ic_edit, Ui.ACCENT_SOFT); // the feed's settings
            Ui.pad(ed, 3, 3, 3, 3);
            ed.setContentDescription("Edit source");
            ed.setOnClickListener(v -> SettingsPanels.news());
            Ui.press(ed);
            head.addView(ed, Ui.lpu(28, 28));
            head.addView(Ui.gap(c, 12, 1));
            // play the headlines as full-stage slides, and one magnifier that cycles the three text sizes
            View pl = Ui.icon(c, R.drawable.ic_play, Ui.ACCENT_SOFT);
            Ui.pad(pl, 4, 4, 4, 4);
            pl.setContentDescription("Play the headlines");
            pl.setOnClickListener(v -> slideshow());
            Ui.press(pl);
            head.addView(pl, Ui.lpu(30, 30));
            head.addView(Ui.gap(c, 10, 1));
            View fs = Ui.icon(c, R.drawable.ic_search, Ui.ACCENT_SOFT);
            Ui.pad(fs, 4, 4, 4, 4);
            fs.setContentDescription("Text size");
            fs.setOnClickListener(v -> { nextFont(); Panels.refresh(); }); // rebuild the list at the new size
            Ui.press(fs);
            head.addView(fs, Ui.lpu(30, 30));
            head.addView(Ui.gap(c, 12, 1));
            // archive and bin: tap to see them, tap again for today's headlines (lit while open)
            for (int k = 1; k <= 2; k++) {
                final int mode = k;
                View ic = Ui.icon(c, k == 1 ? R.drawable.ic_archive : R.drawable.ic_trash, feedView == k ? Ui.TEXT : Ui.ACCENT_SOFT);
                Ui.pad(ic, 4, 4, 4, 4);
                if (feedView == k) ic.setBackground(Ui.oval(Ui.ACCENT));
                ic.setContentDescription(k == 1 ? "Archive" : "Bin");
                ic.setOnClickListener(v -> {
                    feedView = feedView == mode ? 0 : mode;
                    Panels.refresh();
                });
                Ui.press(ic);
                head.addView(ic, Ui.lpu(30, 30));
                head.addView(Ui.gap(c, 12, 1));
            }
            View x = Ui.icon(c, R.drawable.ic_x, Ui.SUB);
            x.setOnClickListener(v -> Panels.close());
            head.addView(x, Ui.lpu(28, 28));
            // the title and buttons go up into the ticker's place; the list gets the room
            if (MainActivity.I != null && MainActivity.I.titleToTicker(head)) Ui.pad(card, 22, 8, 22, 12);
            else card.addView(head);
            String url = Prefs.str(n ? Prefs.NEWS_URL : Prefs.TRAFFIC_URL, "");
            long at = n ? newsAt : trafficAt;
            String src = (url.isEmpty() ? "Google News · " + place() : Uri.parse(url).getHost()) + (at > 0 ? " · updated " + ago(at) : "");
            card.addView(Ui.line(c, src, 12, Ui.MUTED, false), Widgets.top(6));
            LinearLayout list = Ui.col(c);
            if (feedView == 1) { // archive: kept until deleted (swipe left)
                List<Item> arch = archivedItems();
                if (arch.isEmpty()) list.addView(Ui.text(c, "Nothing archived. Swipe a headline right to keep it here.", 14, Ui.SUB, false), Widgets.top(12));
                for (Item it : arch) list.addView(row(c, it, false, 1));
                if (!arch.isEmpty()) list.addView(Ui.text(c, "Swipe left to delete", 11, Ui.MUTED, false), Widgets.top(12));
            } else if (feedView == 2) { // bin: today's deleted headlines (both feeds), swipe right to put one back
                List<Item> gone = new ArrayList<>();
                for (Item it : newsItems) if (deleted(it) && today(it)) gone.add(it);
                for (Item it : trafficItems) if (deleted(it) && today(it)) gone.add(it);
                if (gone.isEmpty()) list.addView(Ui.text(c, "The bin is empty.", 14, Ui.SUB, false), Widgets.top(12));
                for (Item it : gone) list.addView(row(c, it, true, 2));
                if (!gone.isEmpty()) list.addView(Ui.text(c, "Swipe right to put it back · emptied at midnight", 11, Ui.MUTED, false), Widgets.top(12));
            } else { // today's headlines: unread first, then read ones greyed
                List<Item> unread = new ArrayList<>(), seen = new ArrayList<>();
                for (Item it : items) {
                    if (!inList(it)) continue; // read ones go 24 hours after reading
                    (read(it) ? seen : unread).add(it);
                }
                if (unread.isEmpty() && seen.isEmpty())
                    list.addView(Ui.text(c, at > 0 ? "No new headlines today." : "Loading headlines…", 14, Ui.SUB, false), Widgets.top(12));
                for (Item it : unread) list.addView(row(c, it, false, 0));
                for (Item it : seen) list.addView(row(c, it, true, 0));
                list.addView(Ui.text(c, "Swipe left to delete · swipe right to archive", 11, Ui.MUTED, false), Widgets.top(12));
            }
            android.widget.ScrollView sv = new android.widget.ScrollView(c);
            sv.setVerticalScrollBarEnabled(false);
            sv.addView(list);
            LinearLayout.LayoutParams sl = Ui.weight(1, false);
            sl.topMargin = Ui.u(4);
            card.addView(sv, sl);
            return card;
        });
    }

    /** A headline in the list: tap to read; swipe left to delete, right to archive (archived: left deletes). */
    private View row(Context c, Item it, boolean wasRead, int mode) {
        final boolean inArchive = mode == 1, inBin = mode == 2;
        android.widget.FrameLayout holder = new android.widget.FrameLayout(c);
        LinearLayout r = Ui.col(c);
        Ui.pad(r, 0, 11, 0, 11);
        r.addView(Ui.hr(c));
        TextView t = Ui.text(c, it.title, lvlSize(14, 15, 18), wasRead ? Ui.MUTED : Ui.TEXT, !wasRead);
        t.setLineSpacing(Ui.uf(2), 1);
        r.addView(t, Widgets.top(10));
        if (it.desc != null && !wasRead) {
            TextView d = Ui.text(c, it.desc, lvlSize(11, 12, 14), Ui.SUB, false);
            d.setMaxLines(3);
            r.addView(d, Widgets.top(4));
        }
        String m = (it.source != null ? it.source : "") + (it.date > 0 ? (it.source != null ? " · " : "") + ago(it.date) : "");
        r.addView(Ui.line(c, m, 12, Ui.MUTED, false), Widgets.top(6));
        r.setAlpha(wasRead ? 0.6f : 1f);
        holder.addView(r, new android.widget.FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        final float[] x0 = {0};
        final boolean[] dragging = {false};
        final int slop = android.view.ViewConfiguration.get(c).getScaledTouchSlop();
        r.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    x0[0] = e.getRawX();
                    dragging[0] = false;
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - x0[0];
                    if (!dragging[0] && Math.abs(dx) > slop * 2) {
                        dragging[0] = true;
                        v.getParent().requestDisallowInterceptTouchEvent(true); // a swipe, not a scroll
                    }
                    if (dragging[0]) {
                        if (inArchive && dx > 0) dx = dx / 4; // archive: only left (delete) goes all the way
                        if (inBin && dx < 0) dx = dx / 4; // bin: only right (put back)
                        v.setTranslationX(dx);
                        v.setAlpha(Math.max(0.3f, 1 - Math.abs(dx) / v.getWidth()));
                    }
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    float d = v.getTranslationX();
                    boolean far = Math.abs(d) > v.getWidth() * 0.3f && e.getActionMasked() == android.view.MotionEvent.ACTION_UP;
                    if (far && inBin && d > 0) { // bin, right: put it back
                        v.animate().translationX(v.getWidth()).alpha(0).setDuration(Ui.ms(180)).withEndAction(() -> {
                            restore(it);
                            holder.setVisibility(View.GONE);
                            updateText();
                        }).start();
                    } else if (inBin && dragging[0]) {
                        v.animate().translationX(0).alpha(wasRead ? 0.6f : 1f).setDuration(Ui.ms(160)).start();
                    } else if (far && d < 0) { // left: delete
                        v.animate().translationX(-v.getWidth()).alpha(0).setDuration(Ui.ms(180)).withEndAction(() -> {
                            delete(it);
                            holder.setVisibility(View.GONE);
                            updateText();
                        }).start();
                    } else if (far && !inArchive) { // right: archive
                        v.animate().translationX(v.getWidth()).alpha(0).setDuration(Ui.ms(180)).withEndAction(() -> {
                            archive(it);
                            updateText();
                            Panels.refresh(); // it shows again under Archived
                        }).start();
                    } else {
                        v.animate().translationX(0).alpha(wasRead ? 0.6f : 1f).setDuration(Ui.ms(160)).start();
                        if (!dragging[0] && e.getActionMasked() == android.view.MotionEvent.ACTION_UP) { // a tap: read it
                            markRead(it);
                            updateText();
                            reader(it);
                        }
                    }
                    return true;
            }
            return false;
        });
        return holder;
    }

    /** One story, easy to read: big headline, where it's from, the summary, then the article's text when it can be fetched. */
    static void reader(Item it) {
        Panels.push(() -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            TextView t = Ui.text(c, it.title, lvlSize(20, 22, 26), Ui.TEXT, true);
            t.setLineSpacing(Ui.uf(3), 1);
            b.addView(t);
            String m = (it.source != null ? it.source : "") + (it.date > 0 ? (it.source != null ? " · " : "") + ago(it.date) : "");
            if (!m.isEmpty()) b.addView(Ui.line(c, m, 13, Ui.MUTED, false), Widgets.top(6));
            // a readable summary (the story's opening paragraphs), then the rest of the story below
            TextView sumHead = Ui.line(c, "SUMMARY", 11, Ui.SUB, true);
            sumHead.setLetterSpacing(0.08f);
            b.addView(sumHead, Widgets.top(14));
            TextView summary = Ui.text(c, it.link != null ? "Getting the story…" : clean(it.desc), lvlSize(16, 18, 21), Ui.TEXT, false);
            summary.setLineSpacing(Ui.uf(6), 1);
            b.addView(summary, Widgets.top(6));
            TextView restHead = Ui.line(c, "FULL STORY", 11, Ui.SUB, true);
            restHead.setLetterSpacing(0.08f);
            restHead.setVisibility(View.GONE);
            b.addView(restHead, Widgets.top(18));
            TextView body = Ui.text(c, "", lvlSize(14, 16, 19), Ui.TEXT2, false);
            body.setLineSpacing(Ui.uf(6), 1);
            b.addView(body, Widgets.top(6));
            if (it.link != null) realLink(it.link, url -> Net.get(url, pageHeaders(), (html, code) -> Ui.ui.post(() -> {
                String text = html == null ? null : article(html);
                if (text == null) { // the site wouldn't give its text: the feed's own summary
                    String d = clean(it.desc);
                    String bn = browser() != null ? Apps.label(browser()) : "the browser";
                    summary.setText(d.isEmpty() || d.equalsIgnoreCase(it.title) ? "This site doesn't share its text. The full story opens in " + bn + ": tap its icon at the top." : d);
                    return;
                }
                String[] paras = text.split("\n\n");
                StringBuilder sum = new StringBuilder(), rest = new StringBuilder();
                int words = 0;
                for (String para : paras) { // the opening paragraphs, about 120 words, are the summary
                    if (words < 120) {
                        if (sum.length() > 0) sum.append("\n\n");
                        sum.append(para);
                        words += para.split("\\s+").length;
                    } else {
                        if (rest.length() > 0) rest.append("\n\n");
                        rest.append(para);
                    }
                }
                summary.setText(sum.toString());
                if (rest.length() > 0) {
                    restHead.setVisibility(View.VISIBLE);
                    body.setText(rest.toString());
                }
            })));
            // title-bar actions: the same magnifier (cycles the three text sizes, live) and open-in-browser
            LinearLayout acts = Ui.row(c);
            acts.setGravity(Gravity.CENTER_VERTICAL);
            View fs = Ui.icon(c, R.drawable.ic_search, Ui.ACCENT_SOFT);
            Ui.pad(fs, 3, 3, 3, 3);
            fs.setContentDescription("Text size");
            fs.setOnClickListener(v -> {
                nextFont();
                t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.uf(lvlSize(20, 22, 26)));
                summary.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.uf(lvlSize(16, 18, 21)));
                body.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.uf(lvlSize(14, 16, 19)));
            });
            Ui.press(fs);
            acts.addView(fs, Ui.lpu(30, 30));
            if (it.link != null) {
                String bp = browser();
                android.graphics.drawable.Drawable bi = bp != null ? Apps.iconOf(bp) : null;
                View open;
                if (bi != null) {
                    android.widget.ImageView iv = new android.widget.ImageView(c);
                    iv.setImageDrawable(bi);
                    open = iv;
                } else open = Ui.icon(c, R.drawable.ic_open_new, Ui.ACCENT_SOFT);
                Ui.pad(open, 2, 2, 2, 2);
                open.setContentDescription("Open in " + (bp != null ? Apps.label(bp) : "the browser"));
                open.setOnClickListener(v -> openLink(c, it.link));
                open.setOnLongClickListener(v -> { // hold: choose the browser
                    browserPicker();
                    return true;
                });
                Ui.press(open);
                acts.addView(Ui.gap(c, 8, 1));
                acts.addView(open, Ui.lpu(30, 30));
            }
            return Panels.panel(null, it.source != null ? it.source : "Story", b, 660, 440, acts);
        });
    }

    private static java.util.Map<String, String> pageHeaders() {
        java.util.Map<String, String> h = new java.util.HashMap<>();
        h.put("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
        h.put("Accept-Language", "en-ZA,en;q=0.9");
        return h;
    }

    interface Url {
        void on(String url);
    }

    /**
     * Google News links don't go to the story; they're decoded here to the newspaper's own address (the way
     * Google's own page does it), so the story's text can be read. Anything else is used as it is.
     */
    static void realLink(String link, Url out) {
        Uri u = Uri.parse(link);
        String id = u.getLastPathSegment();
        if (!"news.google.com".equals(u.getHost()) || id == null || !link.contains("/articles/")) {
            out.on(link);
            return;
        }
        Net.get("https://news.google.com/articles/" + id, pageHeaders(), (html, code) -> {
            try {
                java.util.regex.Matcher sg = java.util.regex.Pattern.compile("data-n-a-sg=\"([^\"]+)\"").matcher(html);
                java.util.regex.Matcher ts = java.util.regex.Pattern.compile("data-n-a-ts=\"([^\"]+)\"").matcher(html);
                if (!sg.find() || !ts.find()) {
                    out.on(link);
                    return;
                }
                String inner = "[\"garturlreq\",[[\"X\",\"X\",[\"X\",\"X\"],null,null,1,1,\"US:en\",null,1,null,null,null,null,null,0,1],"
                        + "\"X\",\"X\",1,[1,1,1],1,1,null,0,0,null,0],\"" + id + "\"," + ts.group(1) + ",\"" + sg.group(1) + "\"]";
                org.json.JSONArray req = new org.json.JSONArray().put(new org.json.JSONArray().put(new org.json.JSONArray().put("Fbv4je").put(inner)));
                java.util.Map<String, String> h = pageHeaders();
                h.put("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
                Net.post("https://news.google.com/_/DotsSplashUi/data/batchexecute", h, "f.req=" + Uri.encode(req.toString()), (body, c2) -> {
                    String real = link;
                    try {
                        String j = body.substring(body.indexOf("\n\n") + 2);
                        org.json.JSONArray a = new org.json.JSONArray(j);
                        real = new org.json.JSONArray(a.getJSONArray(0).getString(2)).getString(1);
                    } catch (Exception ignored) {
                    }
                    out.on(real);
                });
            } catch (Exception e) {
                out.on(link);
            }
        });
    }

    private static String article(String html) {
        java.util.regex.Matcher mt = java.util.regex.Pattern.compile("(?is)<p[^>]*>(.*?)</p>").matcher(html);
        StringBuilder out = new StringBuilder();
        while (mt.find() && out.length() < 6000) {
            String p = android.text.Html.fromHtml(mt.group(1), android.text.Html.FROM_HTML_MODE_COMPACT).toString().replaceAll("\\s+", " ").trim();
            if (p.length() < 60) continue; // menus, captions, cookie notes
            if (out.length() > 0) out.append("\n\n");
            out.append(p);
        }
        return out.length() >= 300 ? out.toString() : null;
    }

    /** Browsers on the box (apps that open web links). */
    static List<Apps.App> browsers(Context c) {
        List<Apps.App> out = new ArrayList<>();
        android.content.Intent v = new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://example.com"));
        for (android.content.pm.ResolveInfo ri : c.getPackageManager().queryIntentActivities(v, android.content.pm.PackageManager.MATCH_ALL)) {
            Apps.App a = Apps.byPkg(ri.activityInfo.packageName);
            if (a != null && !out.contains(a)) out.add(a);
        }
        return out;
    }

    /** The browser stories open in: the one chosen in News settings, else Chrome if it's installed (null = Android's default). */
    static String browser() {
        String p = Prefs.str("browser_pkg", null);
        if (p != null && Apps.installed(p)) return p;
        return Apps.installed("com.android.chrome") ? "com.android.chrome" : null;
    }

    /** Choose the browser for stories. */
    static void browserPicker() {
        Panels.push(() -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            String cur = browser();
            for (Apps.App a : browsers(c))
                b.addView(SettingsPanels.appRow(c, a, a.pkg.equals(cur), () -> {
                    Prefs.put("browser_pkg", a.pkg);
                    Panels.back();
                    Panels.refresh();
                }));
            if (b.getChildCount() == 0) b.addView(Ui.text(c, "No browser is installed on this box.", 14, Ui.SUB, false));
            return Panels.panel("Open stories in", b, 520, 400);
        });
    }

    static void openLink(Context c, String url) {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        String bp = browser();
        try {
            if (bp == null) throw new Exception("default");
            c.startActivity(new android.content.Intent(i).setPackage(bp));
        } catch (Exception e) {
            try {
                c.startActivity(i);
            } catch (Exception e2) {
                android.widget.Toast.makeText(c, "No browser on this box", android.widget.Toast.LENGTH_SHORT).show();
            }
        }
        Panels.close();
    }

    /** Smooth scrolling text, or one headline at a time when scrolling is off. */
    final class Marquee extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<String> items = new ArrayList<>();
        private String joined = "";
        private float offset, width;
        private long lastFrame;
        private boolean on;
        private int single;
        private long singleAt;

        Marquee(Context c) {
            super(c);
            p.setColor(Ui.TEXT);
            p.setTextSize(Ui.uf(lvlSize(13, 16, 20)));
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            dot.setColor(Ui.TEXT2);
        }

        /** The feed text size changed (the magnifier): re-measure and redraw. */
        void applyFont() {
            p.setTextSize(Ui.uf(lvlSize(13, 16, 20)));
            width = p.measureText(joined);
            invalidate();
        }

        void setItems(List<String> l) {
            items = new ArrayList<>(l);
            joined = TextUtils.join("     •     ", items) + "     •     ";
            width = p.measureText(joined);
            offset = 0;
            single = 0;
            singleAt = SystemClock.elapsedRealtime();
            invalidate();
        }

        void run(boolean r) {
            on = r;
            lastFrame = 0;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float y = getHeight() / 2f - (p.descent() + p.ascent()) / 2f;
            boolean scroll = Prefs.flag(Prefs.TICKER_SCROLL, true);
            cv.save();
            cv.clipRect(0, 0, getWidth(), getHeight());
            if (items.isEmpty()) {
                cv.restore();
                return;
            }
            if (scroll) {
                long now = SystemClock.uptimeMillis();
                if (on && lastFrame != 0) offset += (now - lastFrame) * Ui.uf(34) / 1000f; // a touch slower for the bigger text
                lastFrame = now;
                if (width > 0) offset %= width;
                float x = -offset;
                while (x < getWidth()) {
                    cv.drawText(joined, x, y, p);
                    x += width;
                }
                if (on) postInvalidateOnAnimation();
            } else {
                long now = SystemClock.elapsedRealtime();
                if (now - singleAt > 6000) {
                    single = (single + 1) % items.size();
                    singleAt = now;
                }
                String s = items.get(single % items.size());
                String t = TextUtils.ellipsize(s, new android.text.TextPaint(p), getWidth(), TextUtils.TruncateAt.END).toString();
                cv.drawText(t, 0, y, p);
                if (on) postInvalidateDelayed(500);
            }
            cv.restore();
        }
    }

    // ------------------------------------------------------------------ edit bar shown instead of the ticker in tile edit mode

    static View editBar(Context c, Runnable done) {
        LinearLayout b = Ui.row(c);
        b.setBackground(Ui.round(0xFF1A2740, 12));
        Ui.pad(b, 12, 0, 6, 0);
        b.addView(Ui.icon(c, R.drawable.ic_drag, Ui.TEXT2), Ui.lpu(16, 16));
        TextView t = Ui.line(c, "Swipe to slide · hold a tile to move it · red − to remove", 13, Ui.TEXT2, false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        tl.leftMargin = Ui.u(10);
        b.addView(t, tl);
        TextView d = Ui.text(c, "Done", 13, Ui.TEXT, true);
        d.setGravity(Gravity.CENTER);
        d.setBackground(Ui.round(Ui.ACCENT, 10));
        Ui.pad(d, 14, 4, 14, 4);
        d.setOnClickListener(v -> done.run());
        TextView w = Ui.text(c, "+ Tiles", 13, Ui.TEXT, true);
        w.setGravity(Gravity.CENTER);
        w.setBackground(Ui.round(0xFF2E3747, 10));
        Ui.pad(w, 12, 4, 12, 4);
        w.setOnClickListener(v -> AppPanels.gallery(-1));
        b.addView(w);
        b.addView(Ui.gap(c, 8, 1));
        b.addView(d);
        return b;
    }
}
