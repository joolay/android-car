package com.carbox.launcher;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Predictive text for the car keyboard (English to start; other languages can be added as their own
 * dictionary file, assets/dict_<lang>.txt, most common words first).
 * Suggests up to 3 words: completions of the word being typed, or after a space the next word.
 * It learns the words you use and which word you put after which; everything stays on the box.
 * Word list: SCOWL (see assets/dict_en_license.txt).
 */
final class Predictor {
    private static List<String> words;           // dictionary, most common first
    private static String[] lower;               // the same, in lower case (no work per key press)
    private static Map<String, Integer> rank;     // word (lower case) → position
    private static Map<String, Integer> used;     // learned: word → times used
    private static Map<String, Map<String, Integer>> next; // learned: word → following word → times
    private static boolean dirty;

    private Predictor() {
    }

    /** Load once, in the background (about a million characters). */
    static void warm(Context c) {
        if (words != null) return;
        final Context app = c.getApplicationContext();
        new Thread(() -> load(app), "predictor").start();
    }

    private static synchronized void load(Context c) {
        if (words != null) return;
        List<String> w = new ArrayList<>(120000);
        Map<String, Integer> r = new HashMap<>(160000);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(c.getAssets().open("dict_en.txt"), "UTF-8"))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) continue;
                String k = line.toLowerCase(Locale.ROOT);
                if (r.containsKey(k)) continue;
                r.put(k, w.size());
                w.add(line);
            }
        } catch (Exception ignored) {
        }
        used = new HashMap<>();
        next = new HashMap<>();
        try {
            org.json.JSONObject u = new org.json.JSONObject(Prefs.str("kb_used", "{}"));
            for (java.util.Iterator<String> it = u.keys(); it.hasNext(); ) {
                String k = it.next();
                used.put(k, u.optInt(k));
            }
            org.json.JSONObject n = new org.json.JSONObject(Prefs.str("kb_next", "{}"));
            for (java.util.Iterator<String> it = n.keys(); it.hasNext(); ) {
                String k = it.next();
                org.json.JSONObject m = n.optJSONObject(k);
                if (m == null) continue;
                Map<String, Integer> mm = new HashMap<>();
                for (java.util.Iterator<String> j = m.keys(); j.hasNext(); ) {
                    String x = j.next();
                    mm.put(x, m.optInt(x));
                }
                next.put(k, mm);
            }
        } catch (Exception ignored) {
        }
        String[] lo = new String[w.size()];
        for (int i = 0; i < lo.length; i++) lo[i] = w.get(i).toLowerCase(Locale.ROOT);
        lower = lo;
        rank = r;
        words = w;
    }

    /** The word being typed at the end of the text (letters and apostrophes), or "". */
    static String current(CharSequence before) {
        if (before == null) return "";
        int i = before.length();
        while (i > 0 && (Character.isLetter(before.charAt(i - 1)) || before.charAt(i - 1) == '\'')) i--;
        return before.subSequence(i, before.length()).toString();
    }

    /** The finished word before the one being typed (for next-word guesses), or "". */
    static String previous(CharSequence before) {
        if (before == null) return "";
        String s = before.toString();
        s = s.substring(0, s.length() - current(s).length()).trim();
        if (s.isEmpty() || !Character.isLetter(s.charAt(s.length() - 1)) && s.charAt(s.length() - 1) != '\'') return "";
        return current(s).toLowerCase(Locale.ROOT);
    }

    /** Up to 3 suggestions for the text before the cursor. */
    static List<String> suggest(CharSequence before) {
        List<String> out = new ArrayList<>();
        if (words == null) return out;
        String cur = current(before), prev = previous(before);
        String p = cur.toLowerCase(Locale.ROOT);
        List<String[]> scored = new ArrayList<>(); // {word, score}
        // what you've put after the previous word before
        Map<String, Integer> after = prev.isEmpty() ? null : next.get(prev);
        if (after != null) for (Map.Entry<String, Integer> e : after.entrySet())
            if (e.getKey().startsWith(p) && !e.getKey().equals(p)) scored.add(new String[]{e.getKey(), String.valueOf(1_000_000 + e.getValue() * 1000)});
        // your own words, then the dictionary in order of how common
        for (Map.Entry<String, Integer> e : used.entrySet())
            if (!p.isEmpty() && e.getKey().startsWith(p) && !e.getKey().equals(p)) scored.add(new String[]{e.getKey(), String.valueOf(500_000 + e.getValue() * 1000)});
        if (!p.isEmpty()) {
            int found = 0;
            for (int i = 0; i < lower.length; i++) {
                String lw = lower[i];
                if (lw.startsWith(p) && !lw.equals(p)) {
                    scored.add(new String[]{words.get(i), String.valueOf(400_000 - i)});
                    if (++found >= 12) break; // the list is in order: the first few are the commonest
                }
            }
        } else if (after == null || after.isEmpty()) { // new word, nothing learned: the commonest starters
            String[] start = before == null || before.toString().trim().isEmpty() || before.toString().trim().matches(".*[.!?]$")
                    ? new String[]{"I", "The", "Hi"} : new String[]{"the", "to", "and"};
            for (String s : start) scored.add(new String[]{s, "1"});
        }
        scored.sort((a, b) -> Long.compare(Long.parseLong(b[1]), Long.parseLong(a[1])));
        boolean cap = !cur.isEmpty() && Character.isUpperCase(cur.charAt(0));
        for (String[] s : scored) {
            String w = s[0];
            // keep the case of a name in the dictionary; match a capital you typed
            Integer ri = rank.get(w.toLowerCase(Locale.ROOT));
            if (ri != null && Character.isUpperCase(words.get(ri).charAt(0))) w = words.get(ri);
            if (cap) w = Character.toUpperCase(w.charAt(0)) + w.substring(1);
            boolean dup = false;
            for (String o : out) if (o.equalsIgnoreCase(w)) dup = true;
            if (!dup) out.add(w);
            if (out.size() == 3) break;
        }
        return out;
    }

    /** A word was finished (space, punctuation or a suggestion): learn it, and which word it followed. */
    static void learn(String word, String prev) {
        if (words == null || word == null) return;
        String w = word.toLowerCase(Locale.ROOT).replaceAll("[^a-z']", "");
        if (w.length() < 2) return;
        used.put(w, used.getOrDefault(w, 0) + 1);
        if (prev != null && !prev.isEmpty()) {
            Map<String, Integer> m = next.computeIfAbsent(prev, k -> new HashMap<>());
            m.put(w, m.getOrDefault(w, 0) + 1);
        }
        if (!dirty) {
            dirty = true;
            Ui.ui.postDelayed(Predictor::save, 5000); // saved in batches, not on every key
        }
    }

    private static void save() {
        dirty = false;
        try {
            org.json.JSONObject u = new org.json.JSONObject();
            // keep the most used 3000 words and 1500 pairs so it stays small
            List<Map.Entry<String, Integer>> ul = new ArrayList<>(used.entrySet());
            ul.sort((a, b) -> b.getValue() - a.getValue());
            for (int i = 0; i < ul.size() && i < 3000; i++) u.put(ul.get(i).getKey(), ul.get(i).getValue());
            org.json.JSONObject n = new org.json.JSONObject();
            int pairs = 0;
            for (Map.Entry<String, Map<String, Integer>> e : next.entrySet()) {
                if (pairs > 1500) break;
                org.json.JSONObject m = new org.json.JSONObject();
                for (Map.Entry<String, Integer> x : e.getValue().entrySet()) {
                    m.put(x.getKey(), x.getValue());
                    pairs++;
                }
                n.put(e.getKey(), m);
            }
            Prefs.put("kb_used", u.toString());
            Prefs.put("kb_next", n.toString());
        } catch (Exception ignored) {
        }
    }
}
