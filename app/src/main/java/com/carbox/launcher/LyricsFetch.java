package com.carbox.launcher;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lyrics, found once and kept: in memory and on the box (the last 60 songs), so a song heard before is
 * instant. The next few songs in the music app's queue are fetched ahead of time. When a song isn't kept
 * yet (picked by hand, no queue), every source is asked at once and the first to answer with words wins;
 * if that was untimed and timed lines arrive a moment later, those take over.
 */
final class LyricsFetch {
    private LyricsFetch() {
    }

    /** One song's lyrics. times is null for untimed words (spread over the song when shown). */
    static final class L {
        final List<long[]> times;
        final List<String> words;
        final String from;

        L(List<long[]> times, List<String> words, String from) {
            this.times = times;
            this.words = words;
            this.from = from;
        }

        boolean timed() {
            return times != null;
        }
    }

    interface Out {
        /** On the main thread; may come twice (untimed first, then timed). null: nothing found anywhere. */
        void on(L l);
    }

    private static final android.util.LruCache<String, L> mem = new android.util.LruCache<>(40);
    private static final java.util.Set<String> busy = new java.util.HashSet<>(); // being prefetched right now
    private static final int KEEP = 60; // songs kept on the box
    private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]\\s*(.*)");

    static String key(String track, String artist) {
        return (track + "|" + artist).toLowerCase(java.util.Locale.ROOT).trim();
    }

    /** Kept lyrics for a song, or null. */
    static L cached(Context c, String track, String artist) {
        String k = key(track, artist);
        L l = mem.get(k);
        if (l != null) return l;
        l = load(c, k);
        if (l != null) mem.put(k, l);
        return l;
    }

    /** Lyrics for the song now playing: kept ones straight away, else a race between all the sources. */
    static void get(Context c, String track, String artist, long durMs, String mediaId, Out out) {
        L hit = cached(c, track, artist);
        if (hit != null) {
            out.on(hit);
            if (hit.timed()) return; // as good as it gets
        } else if (noneLately(key(track, artist))) { // no source had any this week (e.g. a recitation): don't ask again
            out.on(null);
            return;
        }
        race(c, track, artist, durMs, mediaId, hit, out, null);
    }

    /**
     * Fetch ahead for the songs coming up, {title, artist, mediaId} each, one song at a time so the
     * other things on the network (weather, news, the map) aren't held up. Nothing is shown; it's kept.
     */
    static void prefetch(Context c, List<String[]> songs) {
        next(c, new ArrayList<>(songs), 0);
    }

    private static void next(Context c, List<String[]> songs, int at) {
        if (at >= songs.size()) return;
        String[] s = songs.get(at);
        String track = s[0], artist = s[1];
        Runnable go = () -> next(c, songs, at + 1);
        if (track == null || artist == null || track.isEmpty()) {
            go.run();
            return;
        }
        String k = key(track, artist);
        L hit = cached(c, track, artist);
        if (busy.contains(k) || (hit != null && hit.timed()) || (hit == null && noneLately(k))) { // kept, on its way, or none to be had
            go.run();
            return;
        }
        busy.add(k);
        race(c, track, artist, 0, s[2], hit, l -> {
        }, () -> {
            busy.remove(k);
            go.run();
        });
    }

    // ------------------------------------------------------------------ the race

    private static void race(Context c, String track, String artist, long durMs, String mediaId, L already, Out out, Runnable done) {
        final String k = key(track, artist);
        final boolean[] shown = {already != null}, shownTimed = {already != null && already.timed()};
        final int[] left = {0};
        final boolean[] reached = {false}; // did any source actually answer (vs no signal)
        Out offer = l -> {
            left[0]--;
            if (l != null && !l.words.isEmpty() && (!shown[0] || (l.timed() && !shownTimed[0]))) {
                shown[0] = true;
                shownTimed[0] = l.timed();
                store(c, k, l);
                out.on(l);
            }
            if (left[0] == 0) { // every source has answered
                if (!shown[0]) {
                    if (reached[0]) markNone(k); // only when a source said so, not when the network was down
                    Trace.event("Lyrics: none found for " + track);
                    out.on(null);
                }
                if (done != null) done.run();
            }
        };
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", "CarLauncher (github.com/joolay/carlauncher)");
        String t2 = cleanTitle(track), a2 = artist.split(",|&| feat| ft\\.| x ")[0].trim();

        // 1. LRCLIB, the exact song
        left[0]++;
        Net.get("https://lrclib.net/api/get?track_name=" + Uri.encode(track) + "&artist_name=" + Uri.encode(artist)
                + (durMs > 0 ? "&duration=" + Math.round(durMs / 1000.0) : ""), h, (body, code) -> {
            if (code >= 200 && code < 500) reached[0] = true; // a real answer, not a failed connection
            L l = null;
            try {
                JSONObject o = new JSONObject(body);
                l = fromLrclib(o.optString("syncedLyrics", ""), o.optString("plainLyrics", ""), "LRCLIB");
            } catch (Exception ignored) {
            }
            offer.on(l);
        });
        // 2. LRCLIB search with a cleaned-up title (the right length of song)
        left[0]++;
        Net.get("https://lrclib.net/api/search?track_name=" + Uri.encode(t2) + "&artist_name=" + Uri.encode(a2), h, (body, code) -> {
            if (code >= 200 && code < 500) reached[0] = true; // a real answer, not a failed connection
            L l = null;
            try {
                JSONArray arr = new JSONArray(body);
                L plain = null;
                for (int i = 0; i < arr.length() && l == null; i++) {
                    JSONObject o = arr.getJSONObject(i);
                    double d = o.optDouble("duration", 0);
                    if (durMs > 0 && d > 0 && Math.abs(d - durMs / 1000.0) > 8) continue; // another version
                    L got = fromLrclib(o.optString("syncedLyrics", ""), o.optString("plainLyrics", ""), "LRCLIB search");
                    if (got == null) continue;
                    if (got.timed()) l = got;
                    else if (plain == null) plain = got;
                }
                if (l == null) l = plain;
            } catch (Exception ignored) {
            }
            offer.on(l);
        });
        // 3. lyrics.ovh (words only)
        left[0]++;
        Net.get("https://api.lyrics.ovh/v1/" + Uri.encode(a2) + "/" + Uri.encode(t2), h, (body, code) -> {
            if (code >= 200 && code < 500) reached[0] = true; // a real answer, not a failed connection
            L l = null;
            try {
                l = untimed(new JSONObject(body).optString("lyrics", ""), "lyrics.ovh");
            } catch (Exception ignored) {
            }
            offer.on(l);
        });
        // 4. Spotify's own (timed), when signed in
        if (SpotifyLyrics.active()) {
            left[0]++;
            SpotifyLyrics.fetch(c, SpotifyLyrics.trackId(mediaId), track, artist, (times, words) ->
                    offer.on(words.isEmpty() ? null : new L(new ArrayList<>(times), new ArrayList<>(words), "Spotify")));
        }
    }

    private static L fromLrclib(String synced, String plain, String from) {
        if (synced != null && !synced.isEmpty() && !"null".equals(synced)) {
            L l = timed(synced, from);
            if (l != null) return l;
        }
        if (plain != null && !plain.isEmpty() && !"null".equals(plain)) return untimed(plain, from + " (untimed)");
        return null;
    }

    private static L timed(String synced, String from) {
        List<long[]> t = new ArrayList<>();
        List<String> w = new ArrayList<>();
        for (String ln : synced.split("\n")) {
            Matcher m = LRC.matcher(ln.trim());
            if (!m.matches()) continue;
            long ms = Long.parseLong(m.group(1)) * 60000 + Long.parseLong(m.group(2)) * 1000;
            if (m.group(3) != null) {
                String f = m.group(3);
                ms += Long.parseLong(f) * (f.length() == 2 ? 10 : f.length() == 1 ? 100 : 1);
            }
            t.add(new long[]{ms});
            w.add(m.group(4));
        }
        return w.isEmpty() ? null : new L(t, w, from);
    }

    private static L untimed(String plain, String from) {
        if (plain == null) return null;
        List<String> w = new ArrayList<>();
        for (String ln : plain.split("\n")) if (!ln.trim().isEmpty()) w.add(ln.trim());
        return w.isEmpty() ? null : new L(null, w, from);
    }

    static String cleanTitle(String t) {
        return t.replaceAll("(?i)\\s*[\\(\\[](feat|ft|with|from|remaster|live|radio|edit|version|prod)[^\\)\\]]*[\\)\\]]", "")
                .replaceAll("(?i)\\s+-\\s+(remaster|live|radio|edit|single|mono|stereo|feat|from).*$", "").trim();
    }

    // ------------------------------------------------------------------ songs with no lyrics anywhere

    private static final String NONE = "lyrics_none"; // {song key: when every source came up empty}
    private static final long NONE_MS = 7 * 24 * 3600000L; // asked again after a week (lyrics do get added)

    private static boolean noneLately(String k) {
        try {
            long at = new JSONObject(Prefs.str(NONE, "{}")).optLong(k, 0);
            return at > 0 && System.currentTimeMillis() - at < NONE_MS;
        } catch (Exception e) {
            return false;
        }
    }

    private static void markNone(String k) {
        try {
            JSONObject o = new JSONObject(Prefs.str(NONE, "{}"));
            long now = System.currentTimeMillis();
            o.put(k, now);
            java.util.Iterator<String> it = o.keys();
            List<String> old = new ArrayList<>();
            while (it.hasNext()) {
                String x = it.next();
                if (now - o.optLong(x, now) > NONE_MS) old.add(x);
            }
            for (String x : old) o.remove(x);
            Prefs.put(NONE, o.toString());
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ kept on the box

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "lyrics");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File file(Context c, String k) {
        return new File(dir(c), Integer.toHexString(k.hashCode()) + ".json");
    }

    private static void store(Context c, String k, L l) {
        mem.put(k, l);
        try {
            JSONObject o = new JSONObject();
            o.put("k", k);
            o.put("from", l.from);
            JSONArray w = new JSONArray();
            for (String s : l.words) w.put(s);
            o.put("w", w);
            if (l.times != null) {
                JSONArray t = new JSONArray();
                for (long[] x : l.times) t.put(x[0]);
                o.put("t", t);
            }
            java.io.FileOutputStream f = new java.io.FileOutputStream(file(c, k));
            f.write(o.toString().getBytes("UTF-8"));
            f.close();
            // keep the newest ones only
            File[] all = dir(c).listFiles();
            if (all != null && all.length > KEEP) {
                java.util.Arrays.sort(all, (x, y) -> Long.compare(y.lastModified(), x.lastModified()));
                for (int i = KEEP; i < all.length; i++) all[i].delete();
            }
        } catch (Exception ignored) {
        }
    }

    private static L load(Context c, String k) {
        File f = file(c, k);
        if (!f.exists()) return null;
        try {
            byte[] b = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int n = 0;
            while (n < b.length) {
                int r = in.read(b, n, b.length - n);
                if (r < 0) break;
                n += r;
            }
            in.close();
            JSONObject o = new JSONObject(new String(b, 0, n, "UTF-8"));
            if (!k.equals(o.optString("k"))) return null; // a different song with the same short name
            List<String> w = new ArrayList<>();
            JSONArray wa = o.getJSONArray("w");
            for (int i = 0; i < wa.length(); i++) w.add(wa.getString(i));
            List<long[]> t = null;
            JSONArray ta = o.optJSONArray("t");
            if (ta != null) {
                t = new ArrayList<>();
                for (int i = 0; i < ta.length(); i++) t.add(new long[]{ta.getLong(i)});
            }
            f.setLastModified(System.currentTimeMillis()); // recently used: keep it longer
            return w.isEmpty() ? null : new L(t, w, o.optString("from", "kept") + ", kept");
        } catch (Exception e) {
            return null;
        }
    }
}
