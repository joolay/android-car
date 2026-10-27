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
final class Ticker {
    private final Context c;
    final LinearLayout view;
    private final TextView tag;
    private final Marquee marquee;
    private final U.Ring ring;
    private final List<String> news = new ArrayList<>(), traffic = new ArrayList<>();
    private final List<Item> newsItems = new ArrayList<>(), trafficItems = new ArrayList<>();
    private long newsAt, trafficAt; // wall clock of last successful load

    static final class Item {
        String title, link, desc, source;
        long date;
    }
    private boolean showingNews = true;
    private long manualUntil;     // elapsedRealtime; 0 = automatic
    private long manualStart;
    private long switchedAt;
    private long fetchedAt;
    private boolean running;

    Ticker(Context c) {
        this.c = c;
        view = U.row(c);
        view.setBackground(U.round(U.CARD, 12));
        U.pad(view, 8, 0, 12, 0);
        tag = U.text(c, "NEWS", 11, U.TEXT, true);
        tag.setLetterSpacing(0.04f);
        U.pad(tag, 7, 3, 7, 3);
        view.addView(tag);
        marquee = new Marquee(c);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, U.MATCH, 1);
        mlp.leftMargin = U.u(10);
        view.addView(marquee, mlp);
        ring = new U.Ring(c);
        ring.setVisibility(View.GONE);
        LinearLayout.LayoutParams rlp = U.lpu(18, 18);
        rlp.leftMargin = U.u(8);
        view.addView(ring, rlp);
        // the NEWS / TRAFFIC label switches feeds; the headlines open the feed for more detail
        tag.setOnClickListener(v -> tapped());
        view.setOnClickListener(v -> openFeed());
        String mode = Store.str(Store.TICKER_MODE, "alt");
        showingNews = !"traffic".equals(mode);
        applyTag();
    }

    void start() {
        if (running) return;
        running = true;
        U.ui.post(loop);
        marquee.run(true);
    }

    void stop() {
        running = false;
        U.ui.removeCallbacks(loop);
        marquee.run(false);
    }

    private void tapped() {
        showingNews = !showingNews;
        int back = Store.num(Store.TICKER_BACK, 60);
        String mode = Store.str(Store.TICKER_MODE, "alt");
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
            String mode = Store.str(Store.TICKER_MODE, "alt");
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
                ring.set((now - manualStart) / (float) (manualUntil - manualStart), null, U.ACCENT);
            } else ring.setVisibility(View.GONE);
            if (!manual && "alt".equals(mode)) {
                int every = Store.num(Store.TICKER_EVERY, 60);
                if (now - switchedAt > every * 1000L) {
                    switchedAt = now;
                    showingNews = !showingNews;
                    applyTag();
                    updateText();
                }
            }
            if (running) U.ui.postDelayed(this, 250);
        }
    };

    private void applyTag() {
        tag.setText(showingNews ? "NEWS" : "TRAFFIC");
        tag.setTextColor(showingNews ? U.TEXT : 0xFF3A2600);
        tag.setBackground(U.round(showingNews ? U.ACCENT : U.AMBER, 6));
    }

    private void updateText() {
        List<String> l = showingNews ? news : traffic;
        if (l.isEmpty()) {
            marquee.setItems(java.util.Collections.singletonList(Loc.has() ? "Loading headlines…" : "Waiting for location to find local headlines"));
        } else marquee.setItems(l);
    }

    private String place() {
        return Loc.town != null ? Loc.town : "South Africa";
    }

    void fetch() {
        fetchedAt = SystemClock.elapsedRealtime();
        String newsUrl = Store.str(Store.NEWS_URL, "");
        String trafficUrl = Store.str(Store.TRAFFIC_URL, "");
        if (newsUrl.isEmpty()) newsUrl = "https://news.google.com/rss/search?q=" + Uri.encode(place()) + "&hl=en-ZA&gl=ZA&ceid=ZA:en";
        if (trafficUrl.isEmpty()) trafficUrl = "https://news.google.com/rss/search?q=" + Uri.encode("traffic " + place()) + "+when:2d&hl=en-ZA&gl=ZA&ceid=ZA:en";
        Net.get(newsUrl, null, (b, code) -> {
            if (b != null) {
                List<Item> items = parseItems(b);
                if (items.isEmpty()) return;
                newsItems.clear();
                newsItems.addAll(items);
                news.clear();
                for (Item it : items) news.add(it.title);
                newsAt = System.currentTimeMillis();
                if (showingNews) updateText();
            }
        });
        Net.get(trafficUrl, null, (b, code) -> {
            if (b != null) {
                List<Item> items = parseItems(b);
                trafficItems.clear();
                trafficItems.addAll(items);
                traffic.clear();
                for (Item it : items) traffic.add(it.title);
                trafficAt = System.currentTimeMillis();
                if (!showingNews) updateText();
            }
        });
        String key = Store.str(Store.TOMTOM_KEY, "");
        if (!key.isEmpty() && Loc.has()) {
            String bbox = String.format(java.util.Locale.US, "%.3f,%.3f,%.3f,%.3f", Loc.lon - 0.25, Loc.lat - 0.25, Loc.lon + 0.25, Loc.lat + 0.25);
            String url = "https://api.tomtom.com/traffic/services/5/incidentDetails?key=" + Uri.encode(key) + "&bbox=" + bbox
                    + "&fields=%7Bincidents%7Bproperties%7Bevents%7Bdescription%7D,from,to,delay%7D%7D%7D&language=en-GB&timeValidityFilter=present";
            Net.get(url, null, (b, code) -> {
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

    private void openFeed() {
        final boolean wasNews = showingNews;
        Sheets.show(() -> {
            Context c = Sheets.ctx();
            boolean n = wasNews;
            List<Item> items = n ? newsItems : trafficItems;
            LinearLayout card = U.col(c);
            card.setBackground(U.round(U.CARD, 22));
            U.pad(card, 26, 20, 26, 16);
            card.setLayoutParams(Sheets.centered(660, 440));
            LinearLayout head = U.row(c);
            TextView tg = U.text(c, n ? "NEWS" : "TRAFFIC", 12, n ? U.TEXT : 0xFF3A2600, true);
            tg.setBackground(U.round(n ? U.ACCENT : U.AMBER, 6));
            U.pad(tg, 9, 3, 9, 3);
            head.addView(tg);
            TextView title = U.line(c, n ? "Local news" : "Traffic", 20, U.TEXT, true);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
            tl.leftMargin = U.u(12);
            head.addView(title, tl);
            head.addView(U.button(c, "Edit source", false, SettingsPanels::news));
            head.addView(U.gap(c, 12, 1));
            View x = U.icon(c, R.drawable.ic_x, U.SUB);
            x.setOnClickListener(v -> Sheets.close());
            head.addView(x, U.lpu(24, 24));
            card.addView(head);
            String url = Store.str(n ? Store.NEWS_URL : Store.TRAFFIC_URL, "");
            long at = n ? newsAt : trafficAt;
            String src = (url.isEmpty() ? "Google News · " + place() : Uri.parse(url).getHost()) + (at > 0 ? " · updated " + ago(at) : "");
            card.addView(U.line(c, src, 12, U.MUTED, false), Widgets.top(6));
            LinearLayout list = U.col(c);
            if (items.isEmpty()) list.addView(U.text(c, at > 0 ? "No headlines in this feed right now." : "Loading headlines…", 14, U.SUB, false), Widgets.top(12));
            for (Item it : items) {
                LinearLayout r = U.col(c);
                U.pad(r, 0, 11, 0, 11);
                r.addView(U.hr(c));
                TextView t = U.text(c, it.title, 15, U.TEXT, true);
                t.setLineSpacing(U.uf(2), 1);
                r.addView(t, Widgets.top(10));
                if (it.desc != null) {
                    TextView d = U.text(c, it.desc, 12, U.SUB, false);
                    d.setMaxLines(3);
                    r.addView(d, Widgets.top(4));
                }
                LinearLayout meta = U.row(c);
                String m = (it.source != null ? it.source : "") + (it.date > 0 ? (it.source != null ? " · " : "") + ago(it.date) : "");
                meta.addView(U.line(c, m, 12, U.MUTED, false), new LinearLayout.LayoutParams(0, U.WRAP, 1));
                if (it.link != null) {
                    TextView open = U.text(c, "Open in Chrome ›", 12, U.ACCENT_SOFT, true);
                    U.pad(open, 10, 4, 0, 4);
                    open.setOnClickListener(v -> openLink(c, it.link));
                    meta.addView(open);
                    r.setOnClickListener(v -> openLink(c, it.link));
                }
                r.addView(meta, Widgets.top(6));
                list.addView(r);
            }
            android.widget.ScrollView sv = new android.widget.ScrollView(c);
            sv.setVerticalScrollBarEnabled(false);
            sv.addView(list);
            LinearLayout.LayoutParams sl = U.weight(1, false);
            sl.topMargin = U.u(4);
            card.addView(sv, sl);
            return card;
        });
    }

    static void openLink(Context c, String url) {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(new android.content.Intent(i).setPackage("com.android.chrome"));
        } catch (Exception e) {
            try {
                c.startActivity(i);
            } catch (Exception e2) {
                android.widget.Toast.makeText(c, "No browser on this box", android.widget.Toast.LENGTH_SHORT).show();
            }
        }
        Sheets.close();
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
            p.setColor(U.TEXT2);
            p.setTextSize(U.uf(13));
            p.setTypeface(Typeface.DEFAULT);
            dot.setColor(U.SUB);
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
            boolean scroll = Store.flag(Store.TICKER_SCROLL, true);
            cv.save();
            cv.clipRect(0, 0, getWidth(), getHeight());
            if (items.isEmpty()) {
                cv.restore();
                return;
            }
            if (scroll) {
                long now = SystemClock.uptimeMillis();
                if (on && lastFrame != 0) offset += (now - lastFrame) * U.uf(40) / 1000f;
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
        LinearLayout b = U.row(c);
        b.setBackground(U.round(0xFF1A2740, 12));
        U.pad(b, 12, 0, 6, 0);
        b.addView(U.icon(c, R.drawable.ic_drag, U.TEXT2), U.lpu(16, 16));
        TextView t = U.line(c, "Drag to reorder; tap ⋯ to change or remove. + adds a tile.", 13, U.TEXT2, false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
        tl.leftMargin = U.u(10);
        b.addView(t, tl);
        TextView d = U.text(c, "Done", 13, U.TEXT, true);
        d.setGravity(Gravity.CENTER);
        d.setBackground(U.round(U.ACCENT, 10));
        U.pad(d, 14, 4, 14, 4);
        d.setOnClickListener(v -> done.run());
        b.addView(d);
        return b;
    }
}
