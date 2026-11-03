package com.carbox.launcher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Full-screen phone: Favourites, Recents, Contacts and a keypad. Calls go out through the Calls setting (BT Phone by default). */
final class PhoneScreen {
    private PhoneScreen() {
    }

    private static FrameLayout shown, holder;
    private static String tab = "contacts";
    private static String query = "";
    private static final StringBuilder number = new StringBuilder();

    static boolean open() {
        return shown != null;
    }

    static void show(String dialNumber) {
        MainActivity a = MainActivity.I;
        if (a == null) return;
        Panels.close();
        if (dialNumber != null) {
            number.setLength(0);
            number.append(dialNumber.replaceAll("[^0-9+*#]", ""));
            tab = "keypad";
        }
        if (shown == null) {
            shown = new FrameLayout(a);
            shown.setClickable(true);
            shown.setBackgroundColor(Ui.BG);
            shown.setBackground(Ui.round(Ui.BG, 16));
            // inside the tile area, under any open panel; a clipping holder keeps it inside the
            // tile area while it slides up out of the ticker, like the tile drawer
            holder = new FrameLayout(a);
            holder.addView(shown, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            a.tileArea.addView(holder, a.tileArea.indexOfChild(a.sheetHost), new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            shown.setTranslationY(a.tileArea.getHeight() + Ui.u(12));
            shown.animate().translationY(0).setDuration(Ui.ms(340)).setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
        }
        Prefs.put("phone_seen", true);
        a.mapAside(true); // the big map steps aside for the dialler
        render();
    }

    static boolean isShown() {
        return shown != null;
    }

    static boolean hide() {
        if (shown == null) return false;
        InAppKeyboard.hide();
        // slides back down into the ticker, then goes
        final FrameLayout v = shown, h = holder;
        shown = null;
        holder = null;
        if (MainActivity.I != null) MainActivity.I.mapAside(false);
        Runnable gone = () -> {
            View x = h != null ? h : v;
            if (x.getParent() != null) ((ViewGroup) x.getParent()).removeView(x);
        };
        float down = (h != null ? h.getHeight() : v.getHeight()) + Ui.u(12);
        if (Ui.ms(300) == 0 || down <= Ui.u(12)) gone.run();
        else v.animate().translationY(down).setDuration(Ui.ms(300)).setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).withEndAction(gone).start();
        return true;
    }

    private static void render() {
        Context c = shown.getContext();
        shown.removeAllViews();
        LinearLayout row = Ui.row(c);
        row.setGravity(Gravity.TOP);
        Ui.pad(row, 0);

        // tabs: icons (Favourites, Recents, Contacts, Keypad); themed so they frost in the Glass theme
        LinearLayout tabs = Ui.col(c);
        String[][] t = {{"favourites", "Favourites"}, {"recents", "Recents"}, {"contacts", "Contacts"}, {"keypad", "Keypad"}};
        int[] icons = {R.drawable.ic_star, R.drawable.ic_history, R.drawable.ic_contacts, R.drawable.ic_apps};
        for (int i = 0; i < t.length; i++) {
            final String id = t[i][0];
            boolean sel = id.equals(tab);
            FrameLayout b = new FrameLayout(c);
            b.setBackground(sel ? Ui.round(Ui.ACCENT, 14) : Themes.inner(Ui.CARD2, 14));
            b.addView(Ui.icon(c, icons[i], sel ? Ui.TEXT : Ui.TEXT2), Ui.flpu(24, 24, Gravity.CENTER));
            b.setContentDescription(t[i][1]);
            b.setOnClickListener(v -> {
                tab = id;
                render();
            });
            Ui.press(b);
            LinearLayout.LayoutParams bl = Ui.lpu(Ui.MATCH, 52);
            bl.bottomMargin = Ui.u(6);
            tabs.addView(b, bl);
        }
        tabs.addView(new View(c), Ui.weight(1, false));
        String bt = Actions.btDialler();
        TextView via = Ui.text(c, "Calls go through\n" + (Prefs.str(Prefs.CALLS, "bt").equals("bt") ? (bt == null ? "Bluetooth" : Apps.label(bt)) : Widgets.callsLabel()), 11, Ui.SUB, false);
        via.setPadding(Ui.u(4), 0, 0, Ui.u(4));
        tabs.addView(via);
        row.addView(tabs, Ui.lpu(104, Ui.MATCH));

        LinearLayout body = Ui.col(c);
        body.setBackground(Themes.card(Ui.CARD, 18));
        Ui.pad(body, 12, 10, 12, 10);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, Ui.MATCH, 1);
        bl.leftMargin = Ui.u(8);
        row.addView(body, bl);
        shown.addView(row, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        if ("keypad".equals(tab)) keypad(c, body);
        else list(c, body);
    }

    // ------------------------------------------------------------------ lists

    private static void list(Context c, LinearLayout body) {
        LinearLayout top = Ui.row(c);
        LinearLayout results = Ui.col(c);
        if ("contacts".equals(tab)) {
            LinearLayout sb = Ui.row(c);
            sb.setBackground(Themes.inner(Ui.CARD2, 12));
            Ui.pad(sb, 12, 0, 6, 0);
            sb.addView(Ui.icon(c, R.drawable.ic_search, Ui.SUB), Ui.lpu(20, 20));
            EditText e = Widgets.field("Search contacts", query, false, s -> {
                query = s;
                Ui.ui.removeCallbacks(search);
                search = () -> fill(c, results);
                Ui.ui.postDelayed(search, 250); // wait until typing pauses
            });
            e.setBackground(null);
            sb.addView(e, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            top.addView(sb, new LinearLayout.LayoutParams(0, Ui.u(44), 1));
            top.addView(Ui.gap(c, 10, 1));
        } else {
            top.addView(Ui.line(c, "favourites".equals(tab) ? "Favourites" : "Recent calls", 20, Ui.TEXT, true), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        }
        View close = square(c, R.drawable.ic_x, PhoneScreen::hide);
        top.addView(close, Ui.lpu(48, 48));
        body.addView(top);

        LinearLayout lr = Ui.row(c);
        lr.setGravity(Gravity.TOP);
        // a recycling list: only the rows on screen are built, so 800 contacts stay quick
        list = new android.widget.ListView(c);
        list.setDivider(new android.graphics.drawable.ColorDrawable(Ui.LINE));
        list.setDividerHeight(1);
        list.setVerticalScrollBarEnabled(false);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        FrameLayout area = new FrameLayout(c);
        area.addView(list, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        area.addView(results, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP)); // messages (no contacts, permission…)
        lr.addView(area, new LinearLayout.LayoutParams(0, Ui.MATCH, 1));
        if ("contacts".equals(tab)) {
            // A–Z strip: tap a letter to jump
            LinearLayout az = Ui.col(c);
            az.setGravity(Gravity.CENTER_HORIZONTAL);
            for (char ch = 'A'; ch <= 'Z'; ch++) {
                final char L = ch;
                TextView tl = Ui.text(c, String.valueOf(ch), 10, Ui.ACCENT_SOFT, true);
                tl.setGravity(Gravity.CENTER);
                tl.setOnClickListener(v -> {
                    for (char x = L; x <= 'Z'; x++) { // no one under this letter: next letter that has someone
                        Integer pos = letters.get(x);
                        if (pos != null) {
                            list.setSelection(pos);
                            break;
                        }
                    }
                });
                az.addView(tl, Ui.weight(1, false));
            }
            LinearLayout.LayoutParams al = Ui.lpu(26, Ui.MATCH);
            al.leftMargin = Ui.u(8);
            lr.addView(az, al);
        }
        LinearLayout.LayoutParams ll = Ui.weight(1, false);
        ll.topMargin = Ui.u(6);
        body.addView(lr, ll);
        fill(c, results);
    }

    private static android.widget.ListView list;
    private static final java.util.Map<Character, Integer> letters = new java.util.HashMap<>();
    private static Runnable search;
    private static int fillSeq;

    private static void fill(Context c, LinearLayout results) {
        results.removeAllViews();
        if (list != null) list.setAdapter(null);
        final int seq = ++fillSeq;
        final String t = tab, q = query;
        // read contacts / call log off the screen thread, then show them
        Net.bg(() -> {
            List<String[]> rows = new ArrayList<>();
            String msg = load(c, rows, t, q);
            Ui.ui.post(() -> {
                if (seq != fillSeq || shown == null) return; // tab changed meanwhile
                show(c, results, rows, msg, t, q);
            });
        });
    }

    /** Fills rows; returns a message to show instead (permission or empty), or null. "perm:…" asks for a permission. */
    private static String load(Context c, List<String[]> rows, String tab, String query) {
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            return "perm:" + Manifest.permission.READ_CONTACTS + "|Allow contacts so they show here";
        if ("recents".equals(tab)) {
            if (c.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED)
                return "perm:" + Manifest.permission.READ_CALL_LOG + "|Allow call history so recent calls show here";
            try (Cursor cur = c.getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE}, null, null, CallLog.Calls.DATE + " DESC")) {
                while (cur != null && cur.moveToNext() && rows.size() < 60) {
                    String num = cur.getString(1);
                    String name = cur.getString(0);
                    int type = cur.getInt(2);
                    String kind = type == CallLog.Calls.MISSED_TYPE ? "Missed" : type == CallLog.Calls.OUTGOING_TYPE ? "Outgoing" : "Incoming";
                    rows.add(new String[]{name == null || name.isEmpty() ? num : name, kind + " · " + NewsTicker.ago(cur.getLong(3)), num, null,
                            type == CallLog.Calls.MISSED_TYPE ? "missed" : ""});
                }
            } catch (Exception ignored) {
            }
            if (rows.isEmpty()) return "No recent calls on this box. Recent calls only show here if your phone shares its call history with the box over Bluetooth.";
        } else {
            String sel = "favourites".equals(tab) ? ContactsContract.CommonDataKinds.Phone.STARRED + "=1" : null;
            String[] args = null;
            if ("contacts".equals(tab) && !query.isEmpty()) {
                sel = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ? OR " + ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?";
                args = new String[]{"%" + query + "%", "%" + query + "%"};
            }
            try (Cursor cur = c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.PHOTO_URI, ContactsContract.CommonDataKinds.Phone.LABEL},
                    sel, args, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE")) {
                while (cur != null && cur.moveToNext() && rows.size() < 800) {
                    CharSequence label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(c.getResources(), cur.getInt(2), cur.getString(4));
                    rows.add(new String[]{cur.getString(0) == null ? "" : cur.getString(0), label + " · " + cur.getString(1), cur.getString(1), cur.getString(3), ""});
                }
            } catch (Exception ignored) {
            }
            if (rows.isEmpty()) return "favourites".equals(tab)
                    ? "No favourites yet. Star contacts on your phone (or in the box's contacts) and they show here."
                    : query.isEmpty() ? "No contacts on this box yet. Most boxes copy them from your phone over Bluetooth once you allow it on the phone." : "No contacts match";
        }
        return null;
    }

    private static void show(Context c, LinearLayout results, List<String[]> rows, String msg, String tab, String query) {
        if (msg != null) {
            if (msg.startsWith("perm:")) {
                String[] pm = msg.substring(5).split("\\|", 2);
                permission(c, results, pm[1], pm[0]);
            } else note(c, results, msg);
        }
        letters.clear();
        if ("contacts".equals(tab) && query.isEmpty())
            for (int i = 0; i < rows.size(); i++) {
                String n = rows.get(i)[0];
                if (n.isEmpty()) continue;
                char f = Character.toUpperCase(n.charAt(0));
                if (f >= 'A' && f <= 'Z' && !letters.containsKey(f)) letters.put(f, i);
            }
        list.setAdapter(new android.widget.BaseAdapter() {
            @Override
            public int getCount() {
                return rows.size();
            }

            @Override
            public Object getItem(int i) {
                return rows.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View old, android.view.ViewGroup parent) {
                return row(c, rows.get(i));
            }
        });
    }

    private static View row(Context c, String[] r) {
        {
            LinearLayout row = Ui.row(c);
            row.setMinimumHeight(Ui.u(60));
            Ui.pad(row, 4, 6, 4, 6);
            Widgets.Person p = new Widgets.Person();
            p.name = r[0];
            p.number = r[2];
            p.photo = r[3];
            row.addView(Widgets.avatar(c, p, 40), Ui.lpu(40, 40));
            LinearLayout t = Ui.col(c);
            t.addView(Ui.line(c, r[0], 17, "missed".equals(r[4]) ? Ui.RED : Ui.TEXT, false));
            t.addView(Ui.line(c, r[1], 12, Ui.SUB, false), Widgets.top(2));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
            tl.leftMargin = Ui.u(14);
            row.addView(t, tl);
            FrameLayout call = new FrameLayout(c);
            call.setBackground(Ui.oval(Ui.DGREEN));
            call.addView(Ui.icon(c, R.drawable.ic_call, Ui.TEXT), Ui.flpu(22, 22, Gravity.CENTER));
            call.setOnClickListener(v -> dial(r[2]));
            Ui.press(call);
            row.addView(call, Ui.lpu(48, 48));
            row.setOnClickListener(v -> dial(r[2]));
            return row;
        }
    }

    private static void note(Context c, LinearLayout results, String s) {
        TextView t = Ui.text(c, s, 14, Ui.SUB, false);
        t.setPadding(0, Ui.u(16), 0, 0);
        results.addView(t);
    }

    private static void permission(Context c, LinearLayout results, String s, String perm) {
        note(c, results, s);
        TextView b = Ui.textButton(c, "Allow", true, () -> {
            if (MainActivity.I != null) MainActivity.I.requestPermissions(new String[]{perm}, 61);
        });
        LinearLayout.LayoutParams lp = Ui.lp(Ui.WRAP, Ui.WRAP);
        lp.topMargin = Ui.u(12);
        results.addView(b, lp);
    }

    /** Called when contacts or call-log permission is answered. */
    static void refresh() {
        if (shown != null) render();
    }

    private static View square(Context c, int icon, Runnable r) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(Themes.inner(Ui.CARD2, 12));
        f.addView(Ui.icon(c, icon, Ui.TEXT), Ui.flpu(22, 22, Gravity.CENTER));
        f.setOnClickListener(v -> r.run());
        return f;
    }

    // ------------------------------------------------------------------ keypad

    private static TextView display, match;
    private static Runnable matchLookup;

    private static void keypad(Context c, LinearLayout body) {
        LinearLayout col = Ui.col(c);

        // top: close, the number (and a contact match), backspace
        LinearLayout top = Ui.row(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(square(c, R.drawable.ic_x, PhoneScreen::hide), Ui.lpu(42, 42));
        LinearLayout num = Ui.col(c);
        num.setGravity(Gravity.CENTER);
        display = Ui.text(c, "", 26, Ui.TEXT, true);
        display.setGravity(Gravity.CENTER);
        display.setMaxLines(1);
        display.setHint("Enter a number");
        display.setHintTextColor(Ui.MUTED);
        display.setEllipsize(android.text.TextUtils.TruncateAt.START);
        num.addView(display, Ui.lp(Ui.MATCH, Ui.WRAP));
        match = Ui.line(c, "", 12, Ui.ACCENT_SOFT, false);
        match.setGravity(Gravity.CENTER);
        num.addView(match, Ui.lp(Ui.MATCH, Ui.WRAP));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        nl.setMargins(Ui.u(10), 0, Ui.u(10), 0);
        top.addView(num, nl);
        FrameLayout del = new FrameLayout(c);
        del.setBackground(Themes.inner(Ui.CARD2, 24));
        del.addView(Ui.icon(c, R.drawable.ic_backspace, Ui.TEXT2), Ui.flpu(26, 26, Gravity.CENTER));
        InAppKeyboard.repeat(del, () -> {
            if (number.length() > 0) number.setLength(number.length() - 1);
            updateNumber(c);
        });
        del.setOnLongClickListener(v -> { // hold to clear
            number.setLength(0);
            updateNumber(c);
            return true;
        });
        top.addView(del, Ui.lpu(48, 48));
        col.addView(top, Ui.lp(Ui.MATCH, Ui.WRAP));

        // below: a big 3 × 4 keypad filling the space, with the call button beside it
        LinearLayout lower = Ui.row(c);
        LinearLayout pad = Ui.col(c);
        String[][] keys = {{"1", " "}, {"2", "ABC"}, {"3", "DEF"}, {"4", "GHI"}, {"5", "JKL"}, {"6", "MNO"}, {"7", "PQRS"}, {"8", "TUV"}, {"9", "WXYZ"}, {"*", " "}, {"0", "+"}, {"#", " "}};
        for (int r = 0; r < 4; r++) {
            LinearLayout kr = Ui.row(c);
            for (int k = 0; k < 3; k++) {
                String[] key = keys[r * 3 + k];
                LinearLayout b = Ui.col(c);
                b.setGravity(Gravity.CENTER);
                b.setBackground(Themes.inner(Ui.CARD2, 18));
                TextView d = Ui.text(c, key[0], 26, Ui.TEXT, false);
                d.setGravity(Gravity.CENTER);
                b.addView(d, new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP));
                TextView l = Ui.text(c, key[1], 10, Ui.SUB, true);
                l.setGravity(Gravity.CENTER);
                l.setLetterSpacing(0.15f);
                b.addView(l, new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP));
                b.setOnClickListener(v -> {
                    number.append(key[0]);
                    updateNumber(c);
                });
                if ("0".equals(key[0])) b.setOnLongClickListener(v -> { // hold 0 for +
                    number.append('+');
                    updateNumber(c);
                    return true;
                });
                Ui.press(b);
                LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(0, Ui.MATCH, 1);
                kl.setMargins(Ui.u(3), Ui.u(3), Ui.u(3), Ui.u(3));
                kr.addView(b, kl);
            }
            pad.addView(kr, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1)); // the rows share the height
        }
        lower.addView(pad, new LinearLayout.LayoutParams(0, Ui.MATCH, 1));
        FrameLayout call = new FrameLayout(c);
        call.setBackground(Ui.oval(Ui.GREEN));
        call.addView(Ui.icon(c, R.drawable.ic_call, Ui.TEXT), Ui.flpu(36, 36, Gravity.CENTER));
        call.setOnClickListener(v -> {
            if (number.length() > 0) dial(number.toString());
        });
        Ui.press(call);
        FrameLayout callBox = new FrameLayout(c);
        callBox.addView(call, Ui.flpu(72, 72, Gravity.CENTER));
        lower.addView(callBox, Ui.lpu(96, Ui.MATCH));
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(Ui.MATCH, 0, 1);
        ll.topMargin = Ui.u(6);
        col.addView(lower, ll);
        body.addView(col, new LinearLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        updateNumber(c);
    }

    private static void updateNumber(Context c) {
        if (display == null) return;
        display.setText(number.length() == 0 ? "" : number.toString());
        int len = number.length(); // shrink long numbers so they fit on one line
        display.setSingleLine(true);
        display.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.uf(len == 0 ? 18 : len <= 10 ? 26 : len <= 14 ? 22 : 18));
        match.setText("");
        Ui.ui.removeCallbacks(matchLookup);
        if (number.length() >= 4 && c.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            final String n = number.toString();
            matchLookup = () -> Net.bg(() -> {
                String name = lookup(c, n);
                Ui.ui.post(() -> {
                    if (match != null && name != null && n.equals(number.toString())) match.setText("Matches: " + name);
                });
            });
            Ui.ui.postDelayed(matchLookup, 300);
        }
    }

    /** Contact name for a number, or null. */
    static String lookup(Context c, String num) {
        if (num == null || num.isEmpty()) return null;
        try (Cursor cur = c.getContentResolver().query(android.net.Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(num)),
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) {
        }
        return null;
    }

    static String photoFor(Context c, String num) {
        if (num == null || num.isEmpty()) return null;
        try (Cursor cur = c.getContentResolver().query(android.net.Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(num)),
                new String[]{ContactsContract.PhoneLookup.PHOTO_URI}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void dial(String num) {
        if (MainActivity.I == null || num == null) return;
        Actions.call(MainActivity.I, num);
    }

    static String upper(String s) {
        return s.toUpperCase(Locale.ROOT);
    }
}
