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

    private static FrameLayout shown;
    private static String tab = "contacts";
    private static String query = "";
    private static final StringBuilder number = new StringBuilder();

    static boolean open() {
        return shown != null;
    }

    static void show(String dialNumber) {
        MainActivity a = MainActivity.I;
        if (a == null) return;
        Sheets.close();
        if (dialNumber != null) {
            number.setLength(0);
            number.append(dialNumber.replaceAll("[^0-9+*#]", ""));
            tab = "keypad";
        }
        if (shown == null) {
            shown = new FrameLayout(a);
            shown.setClickable(true);
            shown.setBackgroundColor(U.BG);
            shown.setBackground(U.round(U.BG, 16));
            // inside the tile area, under any open panel
            a.tileArea.addView(shown, a.tileArea.indexOfChild(a.sheetHost), new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        }
        Store.put("phone_seen", true);
        render();
    }

    static boolean hide() {
        if (shown == null) return false;
        Keyboard.hide();
        if (shown.getParent() != null) ((ViewGroup) shown.getParent()).removeView(shown);
        shown = null;
        return true;
    }

    private static void render() {
        Context c = shown.getContext();
        shown.removeAllViews();
        LinearLayout row = U.row(c);
        row.setGravity(Gravity.TOP);
        U.pad(row, 0);

        // tabs
        LinearLayout tabs = U.col(c);
        String[][] t = {{"favourites", "Favourites"}, {"recents", "Recents"}, {"contacts", "Contacts"}, {"keypad", "Keypad"}};
        for (String[] x : t) {
            TextView b = U.text(c, x[1], 14, U.TEXT, x[0].equals(tab));
            b.setGravity(Gravity.CENTER_VERTICAL);
            U.pad(b, 12, 0, 6, 0);
            b.setBackground(U.round(x[0].equals(tab) ? U.ACCENT : U.CARD2, 14));
            b.setOnClickListener(v -> {
                tab = x[0];
                render();
            });
            LinearLayout.LayoutParams bl = U.lpu(U.MATCH, 52);
            bl.bottomMargin = U.u(6);
            tabs.addView(b, bl);
        }
        tabs.addView(new View(c), U.weight(1, false));
        String bt = Actions.btDialler();
        TextView via = U.text(c, "Calls go through\n" + (Store.str(Store.CALLS, "bt").equals("bt") ? (bt == null ? "Bluetooth" : Apps.label(bt)) : Widgets.callsLabel()), 11, U.SUB, false);
        via.setPadding(U.u(4), 0, 0, U.u(4));
        tabs.addView(via);
        row.addView(tabs, U.lpu(104, U.MATCH));

        LinearLayout body = U.col(c);
        body.setBackground(U.round(U.CARD, 18));
        U.pad(body, 12, 10, 12, 10);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, U.MATCH, 1);
        bl.leftMargin = U.u(8);
        row.addView(body, bl);
        shown.addView(row, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));

        if ("keypad".equals(tab)) keypad(c, body);
        else list(c, body);
    }

    // ------------------------------------------------------------------ lists

    private static void list(Context c, LinearLayout body) {
        LinearLayout top = U.row(c);
        LinearLayout results = U.col(c);
        if ("contacts".equals(tab)) {
            LinearLayout sb = U.row(c);
            sb.setBackground(U.round(U.CARD2, 12));
            U.pad(sb, 12, 0, 6, 0);
            sb.addView(U.icon(c, R.drawable.ic_search, U.SUB), U.lpu(20, 20));
            EditText e = Widgets.field("Search contacts", query, false, s -> {
                query = s;
                fill(c, results);
            });
            e.setBackground(null);
            sb.addView(e, new LinearLayout.LayoutParams(0, U.WRAP, 1));
            top.addView(sb, new LinearLayout.LayoutParams(0, U.u(44), 1));
            top.addView(U.gap(c, 10, 1));
        } else {
            top.addView(U.line(c, "favourites".equals(tab) ? "Favourites" : "Recent calls", 20, U.TEXT, true), new LinearLayout.LayoutParams(0, U.WRAP, 1));
        }
        View close = square(c, R.drawable.ic_x, PhoneScreen::hide);
        top.addView(close, U.lpu(44, 44));
        body.addView(top);

        LinearLayout lr = U.row(c);
        lr.setGravity(Gravity.TOP);
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(results);
        lr.addView(sv, new LinearLayout.LayoutParams(0, U.MATCH, 1));
        if ("contacts".equals(tab)) {
            // A–Z strip: tap a letter to jump
            LinearLayout az = U.col(c);
            az.setGravity(Gravity.CENTER_HORIZONTAL);
            for (char ch = 'A'; ch <= 'Z'; ch++) {
                final char L = ch;
                TextView tl = U.text(c, String.valueOf(ch), 10, U.ACCENT_SOFT, true);
                tl.setGravity(Gravity.CENTER);
                tl.setOnClickListener(v -> {
                    for (char x = L; x <= 'Z'; x++) { // no one under this letter: next letter that has someone
                        View target = results.findViewWithTag("L" + x);
                        if (target != null) {
                            sv.smoothScrollTo(0, target.getTop());
                            break;
                        }
                    }
                });
                az.addView(tl, U.weight(1, false));
            }
            LinearLayout.LayoutParams al = U.lpu(26, U.MATCH);
            al.leftMargin = U.u(8);
            lr.addView(az, al);
        }
        LinearLayout.LayoutParams ll = U.weight(1, false);
        ll.topMargin = U.u(6);
        body.addView(lr, ll);
        fill(c, results);
    }

    private static void fill(Context c, LinearLayout results) {
        results.removeAllViews();
        List<String[]> rows = new ArrayList<>(); // name, sub, number, photo
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permission(c, results, "Allow contacts so they show here", Manifest.permission.READ_CONTACTS);
            return;
        }
        if ("recents".equals(tab)) {
            if (c.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                permission(c, results, "Allow call history so recent calls show here", Manifest.permission.READ_CALL_LOG);
                return;
            }
            try (Cursor cur = c.getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE}, null, null, CallLog.Calls.DATE + " DESC")) {
                while (cur != null && cur.moveToNext() && rows.size() < 60) {
                    String num = cur.getString(1);
                    String name = cur.getString(0);
                    int type = cur.getInt(2);
                    String kind = type == CallLog.Calls.MISSED_TYPE ? "Missed" : type == CallLog.Calls.OUTGOING_TYPE ? "Outgoing" : "Incoming";
                    rows.add(new String[]{name == null || name.isEmpty() ? num : name, kind + " · " + Ticker.ago(cur.getLong(3)), num, null,
                            type == CallLog.Calls.MISSED_TYPE ? "missed" : ""});
                }
            } catch (Exception ignored) {
            }
            if (rows.isEmpty()) note(c, results, "No recent calls on this box. Recent calls only show here if your phone shares its call history with the box over Bluetooth.");
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
            if (rows.isEmpty()) note(c, results, "favourites".equals(tab)
                    ? "No favourites yet. Star contacts on your phone (or in the box's contacts) and they show here."
                    : query.isEmpty() ? "No contacts on this box yet. Most boxes copy them from your phone over Bluetooth once you allow it on the phone." : "No contacts match");
        }
        char last = 0;
        for (String[] r : rows) {
            LinearLayout row = U.row(c);
            row.setMinimumHeight(U.u(60));
            if ("contacts".equals(tab) && query.isEmpty() && !r[0].isEmpty()) {
                char f = Character.toUpperCase(r[0].charAt(0));
                if (f != last && f >= 'A' && f <= 'Z') {
                    row.setTag("L" + f);
                    last = f;
                }
            }
            Widgets.Person p = new Widgets.Person();
            p.name = r[0];
            p.number = r[2];
            p.photo = r[3];
            row.addView(Widgets.avatar(c, p, 40), U.lpu(40, 40));
            LinearLayout t = U.col(c);
            t.addView(U.line(c, r[0], 17, "missed".equals(r[4]) ? U.RED : U.TEXT, false));
            t.addView(U.line(c, r[1], 12, U.SUB, false), Widgets.top(2));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
            tl.leftMargin = U.u(14);
            row.addView(t, tl);
            FrameLayout call = new FrameLayout(c);
            call.setBackground(U.oval(U.DGREEN));
            call.addView(U.icon(c, R.drawable.ic_call, U.TEXT), U.flpu(22, 22, Gravity.CENTER));
            call.setOnClickListener(v -> dial(r[2]));
            U.press(call);
            row.addView(call, U.lpu(48, 48));
            row.setOnClickListener(v -> dial(r[2]));
            results.addView(row);
            results.addView(U.hr(c));
        }
    }

    private static void note(Context c, LinearLayout results, String s) {
        TextView t = U.text(c, s, 14, U.SUB, false);
        t.setPadding(0, U.u(16), 0, 0);
        results.addView(t);
    }

    private static void permission(Context c, LinearLayout results, String s, String perm) {
        note(c, results, s);
        TextView b = U.button(c, "Allow", true, () -> {
            if (MainActivity.I != null) MainActivity.I.requestPermissions(new String[]{perm}, 61);
        });
        LinearLayout.LayoutParams lp = U.lp(U.WRAP, U.WRAP);
        lp.topMargin = U.u(12);
        results.addView(b, lp);
    }

    /** Called when contacts or call-log permission is answered. */
    static void refresh() {
        if (shown != null) render();
    }

    private static View square(Context c, int icon, Runnable r) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(U.round(U.CARD2, 12));
        f.addView(U.icon(c, icon, U.TEXT), U.flpu(22, 22, Gravity.CENTER));
        f.setOnClickListener(v -> r.run());
        return f;
    }

    // ------------------------------------------------------------------ keypad

    private static TextView display, match;

    private static void keypad(Context c, LinearLayout body) {
        LinearLayout row = U.row(c);

        // left: the number, a contact match, then backspace and call
        LinearLayout left = U.col(c);
        left.setGravity(Gravity.CENTER);
        display = U.line(c, "", 26, U.TEXT, true);
        display.setGravity(Gravity.CENTER);
        display.setHint("Enter a number");
        display.setHintTextColor(U.MUTED);
        display.setEllipsize(android.text.TextUtils.TruncateAt.START);
        left.addView(display, U.lp(U.MATCH, U.WRAP));
        match = U.line(c, "", 14, U.ACCENT_SOFT, false);
        match.setGravity(Gravity.CENTER);
        left.addView(match, Widgets.top(6));

        LinearLayout actions = U.row(c);
        actions.setGravity(Gravity.CENTER);
        FrameLayout call = new FrameLayout(c);
        call.setBackground(U.oval(U.GREEN));
        call.addView(U.icon(c, R.drawable.ic_call, U.TEXT), U.flpu(34, 34, Gravity.CENTER));
        call.setOnClickListener(v -> {
            if (number.length() > 0) dial(number.toString());
        });
        U.press(call);
        actions.addView(call, U.lpu(64, 64));
        FrameLayout del = new FrameLayout(c);
        del.setBackground(U.oval(U.CARD2));
        del.addView(U.icon(c, R.drawable.ic_backspace, U.TEXT2), U.flpu(26, 26, Gravity.CENTER));
        Keyboard.repeat(del, () -> {
            if (number.length() > 0) number.setLength(number.length() - 1);
            updateNumber(c);
        });
        del.setOnLongClickListener(v -> { // hold to clear
            number.setLength(0);
            updateNumber(c);
            return true;
        });
        LinearLayout.LayoutParams dl = U.lpu(48, 48);
        dl.leftMargin = U.u(14);
        actions.addView(del, dl);
        left.addView(actions, Widgets.top(18));
        row.addView(left, new LinearLayout.LayoutParams(0, U.MATCH, 1));

        // right: a standard 3 × 4 keypad, digits centred with their letters underneath
        LinearLayout pad = U.col(c);
        pad.setGravity(Gravity.CENTER_VERTICAL);
        String[][] keys = {{"1", " "}, {"2", "ABC"}, {"3", "DEF"}, {"4", "GHI"}, {"5", "JKL"}, {"6", "MNO"}, {"7", "PQRS"}, {"8", "TUV"}, {"9", "WXYZ"}, {"*", " "}, {"0", "+"}, {"#", " "}};
        for (int r = 0; r < 4; r++) {
            LinearLayout kr = U.row(c);
            for (int k = 0; k < 3; k++) {
                String[] key = keys[r * 3 + k];
                LinearLayout b = U.col(c);
                b.setGravity(Gravity.CENTER);
                b.setBackground(U.round(U.CARD2, 18));
                TextView d = U.text(c, key[0], 22, U.TEXT, false);
                d.setGravity(Gravity.CENTER);
                b.addView(d, new LinearLayout.LayoutParams(U.WRAP, U.WRAP));
                TextView l = U.text(c, key[1], 10, U.SUB, true);
                l.setGravity(Gravity.CENTER);
                l.setLetterSpacing(0.15f);
                b.addView(l, new LinearLayout.LayoutParams(U.WRAP, U.WRAP));
                b.setOnClickListener(v -> {
                    number.append(key[0]);
                    updateNumber(c);
                });
                if ("0".equals(key[0])) b.setOnLongClickListener(v -> { // hold 0 for +
                    number.append('+');
                    updateNumber(c);
                    return true;
                });
                U.press(b);
                LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(0, U.u(56), 1);
                kl.setMargins(U.u(3), U.u(3), U.u(3), U.u(3));
                kr.addView(b, kl);
            }
            pad.addView(kr);
        }
        row.addView(pad, new LinearLayout.LayoutParams(0, U.MATCH, 1.25f));
        FrameLayout wrap = new FrameLayout(c);
        wrap.addView(row, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        View close = square(c, R.drawable.ic_x, PhoneScreen::hide);
        wrap.addView(close, U.flpu(36, 36, Gravity.TOP | Gravity.START));
        body.addView(wrap, new LinearLayout.LayoutParams(U.MATCH, U.MATCH));
        updateNumber(c);
    }

    private static void updateNumber(Context c) {
        if (display == null) return;
        display.setText(number.length() == 0 ? "" : number.toString());
        match.setText("");
        if (number.length() >= 4 && c.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            String name = lookup(c, number.toString());
            if (name != null) match.setText("Matches: " + name);
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
