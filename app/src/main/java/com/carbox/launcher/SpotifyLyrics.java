package com.carbox.launcher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spotify's own (synced) lyrics, unofficially: you sign in to the Spotify web player once inside the
 * launcher (Settings → News Ticker / Music → Spotify lyrics). Its sign-in cookies stay in the launcher's own
 * web storage on the box; nothing is copied by hand, nothing goes to GitHub. When lyrics are wanted, a hidden
 * web player page opens for a moment and the access key it uses is picked up (it lasts about an hour), then
 * Spotify's lyrics service is asked for the track.
 *
 * PERSONAL USE ONLY: not allowed by Spotify's terms. Switched off by ENABLED; before the Play Store set it
 * to false (or delete this class). Falls back to the free lyrics source whenever this has nothing.
 */
final class SpotifyLyrics {
    /** Only with the owner's private code (PrivateAddons). */
    static boolean ENABLED() {
        return PrivateAddons.on(PrivateAddons.SPOTIFY);
    }
    private static final String ON = "spotify_lyrics_on";
    private static final String UA = "Mozilla/5.0 (Linux; Android 13; Car) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static String token;
    private static long tokenAt;
    private static WebView hidden;
    private static final List<Runnable> waiting = new ArrayList<>();

    private SpotifyLyrics() {
    }

    /** Signed in (the web player's cookie is there) and switched on. */
    static boolean active() {
        return ENABLED() && Prefs.flag(ON, false) && signedIn();
    }

    static boolean signedIn() {
        try {
            String ck = CookieManager.getInstance().getCookie("https://open.spotify.com");
            return ck != null && ck.contains("sp_dc=");
        } catch (Throwable t) {
            return false;
        }
    }

    interface Lines {
        /** times in ms and the words; both empty when Spotify has none (use the free source then). */
        void on(List<long[]> times, List<String> words);
    }

    /** Lyrics for a Spotify track ID, or a search by title and artist when there's no ID. */
    static void fetch(Context c, String trackId, String title, String artist, Lines out) {
        withToken(c, tok -> {
            if (tok == null) {
                out.on(new ArrayList<>(), new ArrayList<>());
                return;
            }
            if (trackId != null) lyrics(tok, trackId, out);
            else search(tok, title, artist, id -> {
                if (id == null) out.on(new ArrayList<>(), new ArrayList<>());
                else lyrics(tok, id, out);
            });
        });
    }

    /** The track ID from what Spotify tells Android ("spotify:track:…"), or null. */
    static String trackId(String mediaId) {
        if (mediaId == null) return null;
        int i = mediaId.indexOf("spotify:track:");
        if (i < 0) return null;
        String id = mediaId.substring(i + 14);
        int e = 0;
        while (e < id.length() && Character.isLetterOrDigit(id.charAt(e))) e++;
        return e == 22 ? id.substring(0, 22) : null;
    }

    private interface Tok {
        void on(String token);
    }

    private interface Id {
        void on(String id);
    }

    private static void lyrics(String tok, String id, Lines out) {
        Map<String, String> h = headers(tok);
        Net.get("https://spclient.wg.spotify.com/color-lyrics/v2/track/" + id + "?format=json&market=from_token", h, (body, code) -> {
            List<long[]> times = new ArrayList<>();
            List<String> words = new ArrayList<>();
            if (code == 401) token = null; // the key ran out: a fresh one next time
            try {
                if (body != null && code == 200) {
                    JSONObject l = new JSONObject(body).getJSONObject("lyrics");
                    if ("LINE_SYNCED".equals(l.optString("syncType"))) {
                        JSONArray a = l.getJSONArray("lines");
                        for (int i = 0; i < a.length(); i++) {
                            JSONObject o = a.getJSONObject(i);
                            String w = o.optString("words", "");
                            times.add(new long[]{Long.parseLong(o.optString("startTimeMs", "0"))});
                            words.add("♪".equals(w) ? "" : w);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            Trace.event("Spotify lyrics: " + (words.isEmpty() ? "none (" + code + ")" : words.size() + " lines"));
            Ui.ui.post(() -> out.on(times, words));
        });
    }

    private static void search(String tok, String title, String artist, Id out) {
        String q = android.net.Uri.encode("track:" + title + " artist:" + artist);
        Net.get("https://api.spotify.com/v1/search?type=track&limit=1&q=" + q, headers(tok), (body, code) -> {
            String id = null;
            try {
                if (body != null && code == 200)
                    id = new JSONObject(body).getJSONObject("tracks").getJSONArray("items").getJSONObject(0).getString("id");
            } catch (Exception ignored) {
            }
            final String f = id;
            Ui.ui.post(() -> out.on(f));
        });
    }

    private static Map<String, String> headers(String tok) {
        Map<String, String> h = new HashMap<>();
        h.put("Authorization", "Bearer " + tok);
        h.put("App-Platform", "WebPlayer");
        h.put("User-Agent", UA);
        return h;
    }

    /**
     * A fresh access key: the web player (signed in with the launcher's cookies) is opened unseen for a moment and
     * the key it sends with its own requests is picked up. Kept for 50 minutes.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private static void withToken(Context c, Tok out) {
        if (token != null && System.currentTimeMillis() - tokenAt < 50 * 60000L) {
            out.on(token);
            return;
        }
        Runnable done = () -> out.on(token);
        waiting.add(done);
        if (hidden != null) return; // already fetching
        try {
            hidden = new WebView(c.getApplicationContext());
            WebSettings s = hidden.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setUserAgentString(UA);
            hidden.setWebViewClient(new WebViewClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                    Map<String, String> h = r.getRequestHeaders();
                    if (h != null && token == null) for (Map.Entry<String, String> e : h.entrySet())
                        if ("authorization".equalsIgnoreCase(e.getKey()) && e.getValue().startsWith("Bearer ")) {
                            token = e.getValue().substring(7);
                            tokenAt = System.currentTimeMillis();
                            Ui.ui.post(SpotifyLyrics::finish);
                        }
                    return null;
                }
            });
            token = null;
            hidden.loadUrl("https://open.spotify.com/");
            Ui.ui.postDelayed(SpotifyLyrics::finish, 20000); // no key within 20 s: give up for now
        } catch (Throwable t) {
            finish();
        }
    }

    private static void finish() {
        if (hidden != null) {
            try {
                hidden.stopLoading();
                hidden.destroy();
            } catch (Throwable ignored) {
            }
            hidden = null;
        }
        List<Runnable> l = new ArrayList<>(waiting);
        waiting.clear();
        for (Runnable r : l) r.run();
    }

    // ------------------------------------------------------------------ settings: sign in inside the launcher

    static void settings() {
        Panels.push(() -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            boolean in = signedIn();
            b.addView(Ui.text(c, in ? "Signed in. Spotify's own lyrics are used first; the free source fills in when Spotify has none."
                    : "Sign in to Spotify here once to get Spotify's own synced lyrics. Personal use only: this isn't an official Spotify feature.", 13, Ui.SUB, false));
            if (in) {
                b.addView(Panels.switchRow(0, 0, "Use Spotify lyrics", "First choice for every song", Prefs.flag(ON, true), on -> Prefs.put(ON, on)), SettingsPanels.top(10));
                b.addView(Panels.item("Sign out", "Clears the Spotify sign-in from the launcher", () -> {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    token = null;
                    Prefs.put(ON, false);
                    Panels.refresh();
                }), SettingsPanels.top(8));
            } else b.addView(Ui.textButton(c, "Sign in to Spotify", true, SpotifyLyrics::signIn), SettingsPanels.top(14));
            return Panels.panel("Spotify lyrics", b, 560, 300);
        });
    }

    /** Spotify's own sign-in page in the launcher; when it's done (the cookie appears) it closes by itself. */
    @SuppressLint("SetJavaScriptEnabled")
    private static void signIn() {
        Panels.showFull(() -> {
            Context c = Panels.ctx();
            FrameLayout f = new FrameLayout(c);
            f.setBackground(Themes.card(Ui.CARD, 16));
            f.setLayoutParams(new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            WebView w = new WebView(c);
            WebSettings s = w.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setUserAgentString(UA);
            CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
            w.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView v, String url) {
                    if (signedIn()) { // signed in: done
                        CookieManager.getInstance().flush();
                        Prefs.put(ON, true);
                        token = null;
                        Trace.event("Spotify lyrics: signed in");
                        android.widget.Toast.makeText(c, "Signed in: Spotify lyrics are on", android.widget.Toast.LENGTH_LONG).show();
                        Panels.close();
                    }
                }
            });
            w.loadUrl("https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F");
            FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH);
            wl.setMargins(Ui.u(8), Ui.u(52), Ui.u(8), Ui.u(8));
            f.addView(w, wl);
            android.view.View x = Ui.iconButton(c, R.drawable.ic_x, "Close", Panels::close);
            FrameLayout.LayoutParams xl = Ui.flpu(44, 44, Gravity.TOP | Gravity.END);
            xl.setMargins(0, Ui.u(4), Ui.u(8), 0);
            f.addView(x, xl);
            InAppKeyboard.bubbleHost = null;
            return f;
        });
    }
}
