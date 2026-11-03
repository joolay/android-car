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

    static void markRead(Item it) {
        add(READ, it);
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
        ring = new Ui.Ring(c);
        ring.setVisibility(View.GONE);
        LinearLayout.LayoutParams rlp = Ui.lpu(18, 18);
        rlp.leftMargin = Ui.u(8);
        view.addView(ring, rlp);
        // the NEWS / TRAFFIC label switches feeds; the headlines open the feed for more detail
        tag.setOnClickListener(v -> tapped());
        view.setOnClickListener(v -> openFeed());
        String mode = Prefs.str(Prefs.TICKER_MODE, "alt");
        showingNews = !"traffic".equals(mode);
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

    private void tapped() {
        showingNews = !showingNews;
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
        tag.setText(showingNews ? "NEWS" : "TRAFFIC");
        tag.setTextColor(showingNews ? Ui.TEXT : 0xFF3A2600);
        tag.setBackground(Ui.round(showingNews ? Ui.ACCENT : Ui.AMBER, 6));
    }

    private void updateText() {
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
        openFeed();
        showingNews = was;
        if (MainActivity.I != null) MainActivity.I.resetIdle();
    }

    private void openFeed() {
        final boolean wasNews = showingNews;
        Panels.showSlide(() -> { // slides down out of the ticker, back up when closed
            Context c = Panels.ctx();
            boolean n = wasNews;
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
            head.addView(tg);
            TextView title = Ui.line(c, n ? "Local news" : "Traffic", 20, Ui.TEXT, true);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
            tl.leftMargin = Ui.u(12);
            head.addView(title, tl);
            View ed = Ui.icon(c, R.drawable.ic_edit, Ui.ACCENT_SOFT); // the feed's settings
            Ui.pad(ed, 3, 3, 3, 3);
            ed.setContentDescription("Edit source");
            ed.setOnClickListener(v -> SettingsPanels.news());
            Ui.press(ed);
            head.addView(ed, Ui.lpu(28, 28));
            head.addView(Ui.gap(c, 14, 1));
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
            // today's headlines: unread first, then read ones greyed; your archive at the bottom
            List<Item> unread = new ArrayList<>(), seen = new ArrayList<>();
            for (Item it : items) {
                if (!today(it) || deleted(it) || archived(it)) continue;
                (read(it) ? seen : unread).add(it);
            }
            List<Item> arch = archivedItems();
            if (unread.isEmpty() && seen.isEmpty())
                list.addView(Ui.text(c, at > 0 ? "No new headlines today." : "Loading headlines…", 14, Ui.SUB, false), Widgets.top(12));
            for (Item it : unread) list.addView(row(c, it, false, false));
            for (Item it : seen) list.addView(row(c, it, true, false));
            if (!arch.isEmpty()) {
                TextView ah = Ui.line(c, "ARCHIVED", 11, Ui.SUB, true);
                ah.setLetterSpacing(0.08f);
                list.addView(ah, Widgets.top(18));
                for (Item it : arch) list.addView(row(c, it, false, true));
            }
            list.addView(Ui.text(c, "Swipe left to delete · swipe right to keep it in the archive", 11, Ui.MUTED, false), Widgets.top(12));
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
    private View row(Context c, Item it, boolean wasRead, boolean inArchive) {
        android.widget.FrameLayout holder = new android.widget.FrameLayout(c);
        LinearLayout r = Ui.col(c);
        Ui.pad(r, 0, 11, 0, 11);
        r.addView(Ui.hr(c));
        TextView t = Ui.text(c, it.title, 15, wasRead ? Ui.MUTED : Ui.TEXT, !wasRead);
        t.setLineSpacing(Ui.uf(2), 1);
        r.addView(t, Widgets.top(10));
        if (it.desc != null && !wasRead) {
            TextView d = Ui.text(c, it.desc, 12, Ui.SUB, false);
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
                        v.setTranslationX(dx);
                        v.setAlpha(Math.max(0.3f, 1 - Math.abs(dx) / v.getWidth()));
                    }
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    float d = v.getTranslationX();
                    boolean far = Math.abs(d) > v.getWidth() * 0.3f && e.getActionMasked() == android.view.MotionEvent.ACTION_UP;
                    if (far && d < 0) { // left: delete
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
            TextView t = Ui.text(c, it.title, 22, Ui.TEXT, true);
            t.setLineSpacing(Ui.uf(3), 1);
            b.addView(t);
            String m = (it.source != null ? it.source : "") + (it.date > 0 ? (it.source != null ? " · " : "") + ago(it.date) : "");
            if (!m.isEmpty()) b.addView(Ui.line(c, m, 13, Ui.MUTED, false), Widgets.top(6));
            if (it.desc != null && !it.desc.isEmpty()) {
                TextView d = Ui.text(c, it.desc, 16, Ui.TEXT2, false);
                d.setLineSpacing(Ui.uf(5), 1);
                b.addView(d, Widgets.top(14));
            }
            TextView body = Ui.text(c, it.link != null ? "Fetching the full story…" : "", 16, Ui.TEXT2, false);
            body.setLineSpacing(Ui.uf(6), 1);
            b.addView(body, Widgets.top(14));
            if (it.link != null) Net.get(it.link, null, (html, code) -> {
                String text = html == null ? null : article(html);
                String bn = browser() != null ? Apps.label(browser()) : "the browser";
                body.setText(text != null ? text : "The full story opens in " + bn + ": tap its icon at the top.");
                body.setTextColor(text != null ? Ui.TEXT2 : Ui.SUB);
            });
            View open = null; // open in the chosen browser (Chrome by default): its icon in the title bar
            if (it.link != null) {
                String bp = browser();
                android.graphics.drawable.Drawable bi = bp != null ? Apps.iconOf(bp) : null;
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
            }
            return Panels.panel(null, it.source != null ? it.source : "Story", b, 660, 440, open);
        });
    }

    /** The readable text of an article page: its paragraphs, or null when there's too little. */
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
            p.setTextSize(Ui.uf(15));
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            dot.setColor(Ui.TEXT2);
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
