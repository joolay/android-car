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
        view.setOnClickListener(v -> tapped());
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
                news.clear();
                news.addAll(parse(b));
                if (showingNews) updateText();
            }
        });
        Net.get(trafficUrl, null, (b, code) -> {
            if (b != null) {
                traffic.clear();
                traffic.addAll(parse(b));
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
                        live.add(d + (from.isEmpty() ? "" : ": " + from + (to.isEmpty() ? "" : " to " + to)) + (delay > 60 ? " · " + delay / 60 + " min delay" : ""));
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

    static List<String> parse(String xml) {
        List<String> out = new ArrayList<>();
        try {
            XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
            p.setInput(new StringReader(xml));
            boolean inItem = false;
            String tagName = null;
            for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT && out.size() < 15; e = p.next()) {
                if (e == XmlPullParser.START_TAG) {
                    tagName = p.getName();
                    if ("item".equals(tagName) || "entry".equals(tagName)) inItem = true;
                } else if (e == XmlPullParser.TEXT && inItem && "title".equals(tagName)) {
                    String t = p.getText().trim();
                    // Google News appends " - Source"
                    int dash = t.lastIndexOf(" - ");
                    if (dash > 20) t = t.substring(0, dash);
                    if (!t.isEmpty()) out.add(t);
                } else if (e == XmlPullParser.END_TAG) {
                    if ("item".equals(p.getName()) || "entry".equals(p.getName())) inItem = false;
                    tagName = null;
                }
            }
        } catch (Exception ignored) {
        }
        return out;
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
        TextView t = U.line(c, "Drag tiles to reorder. Long-press a tile to start.", 13, U.TEXT2, false);
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
