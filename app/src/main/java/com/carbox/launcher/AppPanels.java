package com.carbox.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** All apps, folders, app pickers, Car Connect, the widget gallery and auto start. */
final class AppPanels {
    private AppPanels() {
    }

    static LinearLayout.LayoutParams top(float m) {
        return Widgets.top(m);
    }

    /** An app icon with its name underneath. */
    static View appCell(Context c, Apps.App a, boolean selected, boolean badge) {
        return appCell(c, a, selected, badge, 52);
    }

    static View appCell(Context c, Apps.App a, boolean selected, boolean badge, float icon) {
        LinearLayout l = U.col(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        U.pad(l, 2, 8, 2, 6);
        if (selected) l.setBackground(U.round(0x333D8BFD, 12));
        FrameLayout f = new FrameLayout(c);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(a.icon);
        f.addView(iv, U.flpu(icon, icon, Gravity.CENTER));
        if (badge) {
            FrameLayout b = new FrameLayout(c);
            b.setBackground(U.oval(U.GREEN));
            b.addView(U.icon(c, R.drawable.ic_bolt, U.TEXT), U.flpu(10, 10, Gravity.CENTER));
            f.addView(b, U.flpu(16, 16, Gravity.TOP | Gravity.END));
        }
        if (selected) {
            FrameLayout b = new FrameLayout(c);
            b.setBackground(U.oval(U.ACCENT));
            b.addView(U.icon(c, R.drawable.ic_check, U.TEXT), U.flpu(11, 11, Gravity.CENTER));
            f.addView(b, U.flpu(18, 18, Gravity.TOP | Gravity.START));
        }
        l.addView(f, U.lpu(icon + 6, icon + 4));
        TextView t = U.line(c, a.label, icon < 48 ? 11 : 12, U.TEXT2, false);
        t.setGravity(Gravity.CENTER);
        l.addView(t, top(4));
        return l;
    }

    /** Car Launcher Settings as an app-style cell: gear on a rounded tile, same size as app icons. */
    static View settingsCell(Context c) {
        LinearLayout l = U.col(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        U.pad(l, 2, 8, 2, 6);
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(U.round(U.CARD3, 12));
        ic.addView(U.icon(c, R.drawable.ic_gear, U.TEXT), U.flpu(28, 28, Gravity.CENTER));
        FrameLayout f = new FrameLayout(c);
        f.addView(ic, U.flpu(52, 52, Gravity.CENTER));
        l.addView(f, U.lpu(58, 56));
        TextView t = U.line(c, "Settings", 12, U.TEXT2, false);
        t.setGravity(Gravity.CENTER);
        l.addView(t, top(4));
        U.press(l);
        return l;
    }

    /** Lay views out in rows of `cols`. */
    static LinearLayout grid(Context c, List<View> cells, int cols, float gap) {
        LinearLayout g = U.col(c);
        for (int i = 0; i < cells.size(); i += cols) {
            LinearLayout r = U.row(c);
            r.setGravity(Gravity.TOP);
            for (int k = 0; k < cols; k++) {
                View v = i + k < cells.size() ? cells.get(i + k) : new View(c);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, U.WRAP, 1);
                if (k > 0) lp.leftMargin = U.u(gap);
                r.addView(v, lp);
            }
            LinearLayout.LayoutParams rl = U.lp(U.MATCH, U.WRAP);
            if (i > 0) rl.topMargin = U.u(gap);
            g.addView(r, rl);
        }
        return g;
    }

    // ------------------------------------------------------------------ all apps

    private static final Set<String> selection = new HashSet<>();
    private static boolean selecting;
    private static String query = "";

    static void allApps(boolean select) {
        selecting = select;
        selection.clear();
        query = "";
        Sheets.showFull(AppPanels::allAppsPage); // a full-screen card
    }

    private static View allAppsPage() {
        Context c = Sheets.ctx();
        LinearLayout card = U.col(c);
        card.setBackground(U.round(U.CARD, 16));
        U.pad(card, 16, 12, 16, 12);
        card.setLayoutParams(Sheets.centered(760, 452));

        LinearLayout head = U.row(c);
        // title with the Settings gear beside it
        LinearLayout tbox = U.row(c);
        tbox.addView(U.line(c, selecting ? selection.size() + " selected" : "All apps", 20, U.TEXT, true));
        if (!selecting) {
            android.widget.FrameLayout gear = new android.widget.FrameLayout(c);
            gear.setBackground(U.oval(U.CARD2));
            gear.addView(U.icon(c, R.drawable.ic_gear, U.TEXT), U.flpu(20, 20, Gravity.CENTER));
            gear.setOnClickListener(v -> {
                Sheets.close();
                SettingsPanels.main();
            });
            gear.setContentDescription("Settings");
            U.press(gear);
            LinearLayout.LayoutParams gl = U.lpu(36, 36);
            gl.leftMargin = U.u(12);
            tbox.addView(gear, gl);
        }
        head.addView(tbox, U.lpu(210, U.WRAP));
        LinearLayout searchBox = U.row(c);
        searchBox.setBackground(U.round(U.CARD2, 12));
        U.pad(searchBox, 10, 0, 6, 0);
        searchBox.addView(U.icon(c, R.drawable.ic_search, U.SUB), U.lpu(18, 18));
        LinearLayout results = U.col(c);
        EditText search = Widgets.field("Search apps", query, false, t -> {
            query = t;
            fillApps(c, results);
        });
        search.setBackground(null);
        searchBox.addView(search, new LinearLayout.LayoutParams(0, U.WRAP, 1));
        head.addView(searchBox, new LinearLayout.LayoutParams(0, U.u(38), 1));
        // zoom: − smaller icons (more across), + bigger icons (fewer across); remembered
        head.addView(U.gap(c, 10, 1));
        head.addView(zoomBtn(c, R.drawable.ic_minus, "Smaller icons", -1, results), U.lpu(36, 36));
        head.addView(U.gap(c, 6, 1));
        head.addView(zoomBtn(c, R.drawable.ic_plus, "Bigger icons", 1, results), U.lpu(36, 36));
        head.addView(U.gap(c, 10, 1));
        head.addView(U.seg(c, selecting ? "Cancel" : "Select", selecting, () -> {
            selecting = !selecting;
            selection.clear();
            Sheets.refresh();
        }));
        head.addView(U.gap(c, 10, 1));
        View x = U.icon(c, R.drawable.ic_x, U.SUB);
        x.setOnClickListener(v -> Sheets.close());
        head.addView(x, U.lpu(24, 24));
        card.addView(head);

        LinearLayout.LayoutParams sl = U.weight(1, false); // pages of apps (slide sideways), dots underneath
        sl.topMargin = U.u(12);
        card.addView(results, sl);
        fillApps(c, results);

        if (selecting) {
            LinearLayout bar = U.row(c);
            bar.setGravity(Gravity.CENTER);
            boolean any = !selection.isEmpty();
            bar.addView(action(c, "New folder", any, AppPanels::newFolderFromSelection));
            bar.addView(action(c, "Add to folder", any, AppPanels::addSelectionToFolder));
            bar.addView(action(c, "Auto start", any, () -> {
                for (String k : selection) Actions.addAutostart(k);
                selection.clear();
                selecting = false;
                autostart();
            }));
            bar.addView(action(c, "Pin", any, () -> {
                for (String k : selection) Apps.setPinned(k, true);
                selection.clear();
                selecting = false;
                Sheets.refresh();
            }));
            bar.addView(action(c, "Hide", any, () -> {
                for (String k : selection) Apps.setHidden(k, true);
                selection.clear();
                selecting = false;
                Sheets.refresh();
            }));
            card.addView(bar, top(10));
        }
        return card;
    }

    private static View action(Context c, String label, boolean enabled, Runnable r) {
        TextView t = U.button(c, label, enabled, enabled ? r : null);
        t.setAlpha(enabled ? 1f : 0.45f);
        LinearLayout.LayoutParams lp = U.lp(U.WRAP, U.WRAP);
        lp.setMargins(U.u(5), 0, U.u(5), 0);
        t.setLayoutParams(lp);
        return t;
    }

    private static void fillApps(Context c, LinearLayout results) {
        results.removeAllViews();
        List<View> cells = new ArrayList<>();
        // Car Launcher's own Settings, first in the grid (also found by searching "settings")
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        if (!selecting && !q.isEmpty() && ("settings".startsWith(q) || "car launcher settings".contains(q))) { // only when searched; the gear is by the title
            View s = settingsCell(c);
            s.setOnClickListener(v -> {
                Sheets.close();
                SettingsPanels.main();
            });
            cells.add(s);
        }
        for (Apps.App a : Apps.search(query)) {
            View cell = appCell(c, a, selecting && selection.contains(a.key), Actions.isAutostart(a.key), ZOOM_ICON[zoom()]);
            cell.setOnClickListener(v -> {
                if (selecting) {
                    if (!selection.remove(a.key)) selection.add(a.key);
                    Sheets.refresh();
                } else {
                    Sheets.close();
                    Apps.launch(c, a);
                }
            });
            cell.setOnLongClickListener(v -> {
                appMenu(a, Sheets::refresh);
                return true;
            });
            cells.add(cell);
        }
        if (cells.isEmpty()) results.addView(U.text(c, "No apps match", 14, U.SUB, false));
        else { // zoom sets how many across; rows fit the window (fewer when the Select bar shows)
            float icon = ZOOM_ICON[zoom()], avail = selecting ? 300 : 346;
            int rows = Math.max(1, (int) (avail / (icon + 46)));
            results.addView(new Pager(c, cells, ZOOM_COLS[zoom()], rows, 6), U.lp(U.MATCH, U.WRAP));
        }
    }

    // zoom levels for All apps: icons across and icon size
    private static final int[] ZOOM_COLS = {9, 7, 5, 4};
    private static final float[] ZOOM_ICON = {40, 52, 66, 80};

    private static int zoom() {
        return Math.max(0, Math.min(ZOOM_COLS.length - 1, Store.num("apps_zoom", 1)));
    }

    private static View zoomBtn(Context c, int icon, String desc, int step, LinearLayout results) {
        android.widget.FrameLayout b = new android.widget.FrameLayout(c);
        b.setBackground(U.oval(U.CARD2));
        b.addView(U.icon(c, icon, U.TEXT), U.flpu(18, 18, Gravity.CENTER));
        b.setContentDescription(desc);
        b.setOnClickListener(v -> {
            int z = Math.max(0, Math.min(ZOOM_COLS.length - 1, zoom() + step));
            if (z == zoom()) return;
            Store.put("apps_zoom", z);
            fillApps(c, results); // keeps the search text and keyboard as they are
        });
        U.press(b);
        return b;
    }

    private static void newFolderFromSelection() {
        final List<String> keys = new ArrayList<>(selection);
        Sheets.push(() -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            final String[] name = {"Folder"};
            b.addView(Sheets.section("Folder name"));
            b.addView(Widgets.field("Folder name", "", false, t -> name[0] = t.isEmpty() ? "Folder" : t), U.lp(U.MATCH, U.WRAP));
            b.addView(Sheets.section("Put it in place of"));
            JSONArray specs = Tiles.specs();
            List<View> cells = new ArrayList<>();
            for (int i = 0; i < Tiles.count(); i++) {
                final int idx = i;
                JSONObject s = specs.optJSONObject(i);
                String label = s == null ? "Empty" : "f".equals(s.optString("t")) ? s.optString("name") + " folder" : Widgets.name(s.optString("id"));
                TextView t = U.button(c, (i + 1) + ". " + label, false, () -> {
                    JSONArray apps = new JSONArray();
                    for (String k : keys) apps.put(k);
                    Tiles.setSpec(idx, Tiles.folder(name[0], apps));
                    selection.clear();
                    selecting = false;
                    Sheets.close();
                });
                cells.add(t);
            }
            b.addView(grid(c, cells, 2, 8));
            return Sheets.panel("New folder", b);
        });
    }

    private static void addSelectionToFolder() {
        final List<String> keys = new ArrayList<>(selection);
        Sheets.push(() -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            JSONArray specs = Tiles.specs();
            boolean any = false;
            for (int i = 0; i < Tiles.count(); i++) {
                final int idx = i;
                JSONObject s = specs.optJSONObject(i);
                if (s == null || !"f".equals(s.optString("t"))) continue;
                any = true;
                JSONArray apps = s.optJSONArray("apps");
                b.addView(Sheets.item(s.optString("name"), (apps == null ? 0 : apps.length()) + " apps", () -> {
                    JSONArray a = s.optJSONArray("apps");
                    if (a == null) a = new JSONArray();
                    for (String k : keys) if (HA.indexOf(a, k) < 0) a.put(k);
                    Widgets.put(s, "apps", a);
                    Tiles.setSpec(idx, s);
                    selection.clear();
                    selecting = false;
                    Sheets.close();
                }));
            }
            if (!any) b.addView(U.text(c, "There are no folders on the home screen yet. Use New folder instead.", 14, U.SUB, false));
            return Sheets.panel("Add to folder", b, 520, 380);
        });
    }

    // ------------------------------------------------------------------ app menu (long-press)

    static void appMenu(Apps.App a, Runnable after) {
        Runnable done = () -> {
            if (after != null) after.run();
            if (MainActivity.I != null) MainActivity.I.dock.render();
        };
        Sheets.Page page = () -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            LinearLayout head = U.row(c);
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(a.icon);
            head.addView(iv, U.lpu(40, 40));
            TextView t = U.line(c, a.label, 18, U.TEXT, true);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
            tl.leftMargin = U.u(12);
            head.addView(t, tl);
            View x = U.icon(c, R.drawable.ic_x, U.SUB);
            x.setOnClickListener(v -> Sheets.close());
            head.addView(x, U.lpu(22, 22));
            b.addView(head);
            b.addView(Sheets.switchRow(R.drawable.ic_pin, U.ACCENT, "Pin to dock", null, Apps.pinned(a.key), on -> {
                Apps.setPinned(a.key, on);
                done.run();
            }));
            b.addView(Sheets.switchRow(R.drawable.ic_bolt, U.GREEN, "Auto start", "Opens when the car starts", Actions.isAutostart(a.key), on -> {
                if (on) Actions.addAutostart(a.key);
                else removeAutostart(a.key);
                done.run();
            }));
            b.addView(Sheets.switchRow(R.drawable.ic_hide, U.SUB, "Hide from All apps", "Unhide in Settings → Hidden from All apps", Apps.hidden().contains(a.key), on -> {
                Apps.setHidden(a.key, on);
                if (MainActivity.I != null) MainActivity.I.dock.render();
            }));
            b.addView(Sheets.switchRow(R.drawable.ic_hide, U.SUB, "Hide from Dock", "Still in All apps", Apps.dockHidden().contains(a.key), on -> {
                Apps.setDockHidden(a.key, on);
                if (MainActivity.I != null) MainActivity.I.dock.render();
            }));
            LinearLayout btns = U.row(c);
            btns.addView(U.button(c, "App info", false, () -> {
                Apps.appInfo(c, a.pkg);
                Sheets.close();
            }));
            b.addView(btns, top(10));
            b.setBackground(U.round(U.CARD, 20));
            U.pad(b, 22, 18, 22, 18);
            b.setLayoutParams(Sheets.centered(400, -2));
            return b;
        };
        if (Sheets.open()) Sheets.push(page);
        else Sheets.showCard(page);
    }

    static void removeAutostart(String key) {
        JSONArray a = Actions.autostart(), out = new JSONArray();
        for (int i = 0; i < a.length(); i++) if (!key.equals(a.optJSONObject(i).optString("key"))) out.put(a.optJSONObject(i));
        Store.put(Store.AUTOSTART, out);
    }

    // ------------------------------------------------------------------ pickers

    static void appPicker(String title, U.Cb<Apps.App> cb) {
        final String[] q = {""};
        Sheets.Page page = () -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            LinearLayout list = U.col(c);
            b.addView(Widgets.field("Search apps", q[0], false, t -> {
                q[0] = t;
                fillPicker(c, list, t, cb);
            }), U.lp(U.MATCH, U.WRAP));
            b.addView(list, top(10));
            fillPicker(c, list, q[0], cb);
            return Sheets.panel(title, b, 680, 440);
        };
        if (Sheets.open()) Sheets.push(page);
        else Sheets.show(page);
    }

    private static void fillPicker(Context c, LinearLayout list, String q, U.Cb<Apps.App> cb) {
        list.removeAllViews();
        List<View> cells = new ArrayList<>();
        for (Apps.App a : Apps.search(q)) {
            View cell = appCell(c, a, false, false);
            cell.setOnClickListener(v -> cb.on(a));
            cells.add(cell);
        }
        list.addView(grid(c, cells, 6, 6));
    }

    /** Pick the Bluetooth phone app, with likely ones first. */
    static void btPhonePicker(U.Cb<Apps.App> cb) {
        Sheets.Page page = () -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            b.addView(U.text(c, "Calls go out through your phone over Bluetooth. Which app on this box is the Bluetooth phone?", 13, U.SUB, false));
            String cur = Actions.btDialler();
            int n = 0;
            for (Apps.App a : Apps.visible()) {
                if (!Actions.looksLikeBtPhone(a)) continue;
                n++;
                b.addView(SettingsPanels.appRow(c, a, a.pkg.equals(cur), () -> cb.on(a)));
            }
            if (n == 0) b.addView(U.text(c, "No Bluetooth phone app recognised.", 14, U.TEXT2, false), top(10));
            b.addView(Sheets.item("Other app…", null, () -> appPicker("Bluetooth phone app", cb)), top(4));
            return Sheets.panel("Bluetooth phone app", b, 560, 420);
        };
        if (Sheets.open()) Sheets.push(page);
        else Sheets.show(page);
    }

    // ------------------------------------------------------------------ Car Connect

    static void connect() {
        Sheets.show(() -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            Object[][] lines = {
                    {"carplay", R.drawable.ic_phone, 0xFF1A2740, 0xFF85B7EB, "iPhone, through your bridge app"},
                    {"aa", R.drawable.ic_navarrow, 0xFF143B2A, U.TEAL, "Android phone, through your bridge app"},
                    {"mirror", R.drawable.ic_cast, 0xFF2A1F38, 0xFFC7A6FF, "Shows your phone's screen as it is"},
                    {"native", R.drawable.ic_car, 0xFF3A2A1A, U.AMBER, "Back to the car's own system"}};
            // two by two, sized to the tile area so all four fit without scrolling
            int area = MainActivity.I != null && MainActivity.I.tileArea.getHeight() > 0 ? MainActivity.I.tileArea.getHeight() : U.u(300);
            int cardH = Math.max(U.u(78), (area - U.u(14 + 28 + 14 + 14) - U.u(10) - U.u(30)) / 2);
            LinearLayout row = null;
            for (int i = 0; i < lines.length; i++) {
                final String which = (String) lines[i][0];
                Apps.App app = Actions.connectApp(which);
                View card = optionCard(c, (Integer) lines[i][1], (Integer) lines[i][2], (Integer) lines[i][3], Actions.connectTitle(which), (String) lines[i][4],
                        app == null ? "Choose app" : app.label,
                        () -> Actions.carConnect(c, which), () -> bridgePicker(Actions.connectTitle(which) + " app", Actions.connectKey(which)));
                if (i % 2 == 0) {
                    row = U.row(c);
                    LinearLayout.LayoutParams rl = U.lp(U.MATCH, cardH);
                    if (i > 0) rl.topMargin = U.u(10);
                    b.addView(row, rl);
                }
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, U.MATCH, 1);
                if (i % 2 == 1) cl.leftMargin = U.u(10);
                row.addView(card, cl);
            }
            b.addView(U.line(c, "Tap a card to open it · tap the app name to change it", 12, U.SUB, false), top(8));
            return Sheets.panel("Car Connect", b);
        });
    }

    /** A Car Connect choice as a card: icon and title on top, one line of text, the app to change at the bottom. */
    private static View optionCard(Context c, int icon, int bg, int tint, String title, String sub, String app, Runnable open, Runnable change) {
        LinearLayout r = U.col(c);
        r.setBackground(U.round(U.CARD2, 16));
        U.pad(r, 12, 10, 10, 6);
        LinearLayout top = U.row(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(U.round(bg, 11));
        ic.addView(U.icon(c, icon, tint), U.flpu(22, 22, Gravity.CENTER));
        top.addView(ic, U.lpu(40, 40));
        TextView tt = U.line(c, title, 15, U.TEXT, true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
        tl.leftMargin = U.u(10);
        top.addView(tt, tl);
        r.addView(top);
        TextView st = U.line(c, sub, 12, U.SUB, false);
        r.addView(st, top(6));
        r.addView(new View(c), U.weight(1, false));
        TextView ch = U.line(c, app + " ›", 12, U.ACCENT_SOFT, true);
        U.pad(ch, 0, 6, 8, 4);
        ch.setOnClickListener(v -> change.run());
        r.addView(ch);
        r.setOnClickListener(v -> open.run());
        U.press(r);
        return r;
    }

    private static View option(Context c, int icon, int bg, int tint, String title, String sub, String app, Runnable open, Runnable change) {
        LinearLayout r = U.row(c);
        r.setBackground(U.round(U.CARD2, 16));
        U.pad(r, 16, 0, 12, 0);
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(U.round(bg, 13));
        ic.addView(U.icon(c, icon, tint), U.flpu(26, 26, Gravity.CENTER));
        r.addView(ic, U.lpu(48, 48));
        LinearLayout t = U.col(c);
        t.addView(U.line(c, title, 16, U.TEXT, true));
        t.addView(U.line(c, sub, 12, U.SUB, false), top(3));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
        tl.leftMargin = U.u(16);
        r.addView(t, tl);
        TextView ch = U.line(c, app + " ›", 13, U.ACCENT_SOFT, true);
        U.pad(ch, 12, 14, 4, 14);
        ch.setOnClickListener(v -> change.run());
        r.addView(ch);
        r.setOnClickListener(v -> open.run());
        U.press(r);
        return r;
    }

    /** Choose the bridge / mirror / native app. Known bridge apps are listed first. */
    static void bridgePicker(String title, String storeKey) {
        Sheets.Page page = () -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            String cur = Store.str(storeKey, null);
            List<Apps.App> likely = new ArrayList<>();
            boolean bridge = Store.BRIDGE_PKG.equals(storeKey) || Store.CARPLAY_PKG.equals(storeKey) || Store.AA_PKG.equals(storeKey);
            for (Apps.App a : Apps.visible()) {
                String l = a.label.toLowerCase(java.util.Locale.ROOT);
                if (bridge ? Actions.looksLikeBridge(a) : Store.MIRROR_PKG.equals(storeKey) ? l.contains("mirror") : (l.equals("car") || l.contains("car")))
                    likely.add(a);
            }
            if (bridge) {
                b.addView(U.text(c, "Speed Play, ZLink, AutoKit, CarbitLink and TLink are found automatically.", 12, U.SUB, false));
                if (likely.isEmpty()) b.addView(U.text(c, "No bridge app found on this box.", 14, U.TEXT2, false), top(10));
            }
            for (Apps.App a : likely) {
                LinearLayout r = U.row(c);
                r.setMinimumHeight(U.u(50));
                ImageView iv = new ImageView(c);
                iv.setImageDrawable(a.icon);
                r.addView(iv, U.lpu(34, 34));
                TextView t = U.line(c, a.label, 15, U.TEXT, a.pkg.equals(cur));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
                tl.leftMargin = U.u(12);
                r.addView(t, tl);
                if (a.pkg.equals(cur)) r.addView(U.icon(c, R.drawable.ic_check, U.ACCENT), U.lpu(20, 20));
                r.setOnClickListener(v -> {
                    Store.put(storeKey, a.pkg);
                    Sheets.close();
                    Apps.launch(c, a);
                });
                b.addView(r);
            }
            b.addView(Sheets.item("Other app…", null, () -> appPicker(title, a -> {
                Store.put(storeKey, a.pkg);
                Sheets.close();
                Apps.launch(c, a);
            })), top(4));
            return Sheets.panel(title, b, 520, 400);
        };
        if (Sheets.open()) Sheets.push(page);
        else Sheets.show(page);
    }

    // ------------------------------------------------------------------ folders

    static void folder(int index) {
        Sheets.show(() -> {
            Context c = Sheets.ctx();
            JSONObject spec = Tiles.specs().optJSONObject(index);
            JSONArray apps = spec.optJSONArray("apps");
            List<View> cells = new ArrayList<>();
            List<String> keys = new ArrayList<>();
            if (apps != null) for (int i = 0; i < apps.length(); i++) {
                Apps.App a = Apps.get(apps.optString(i));
                if (a == null) continue;
                View cell = appCell(c, a, false, Actions.isAutostart(a.key));
                cell.setOnClickListener(v -> {
                    Sheets.close();
                    Apps.launch(c, a);
                });
                cells.add(cell);
                keys.add(a.key);
            }
            // hold and drag an app onto another's place to move it; a hold without moving opens its menu
            for (int i = 0; i < cells.size(); i++) {
                final Apps.App a = Apps.get(keys.get(i));
                Reorder.attach(cells.get(i), i, cells, (from, to) -> {
                    List<String> order = new ArrayList<>(keys);
                    order.add(to, order.remove(from));
                    JSONArray na = new JSONArray();
                    for (String k : order) na.put(k);
                    JSONObject sp = Tiles.specs().optJSONObject(index);
                    if (sp == null) return;
                    Widgets.put(sp, "apps", na);
                    Tiles.setSpec(index, sp);
                    Sheets.refresh();
                }, () -> appMenu(a, null));
            }
            LinearLayout b = U.col(c);
            if (cells.isEmpty()) b.addView(U.text(c, "This folder is empty.", 14, U.SUB, false));
            else b.addView(new Pager(c, cells, 6, 2, 6), U.lp(U.MATCH, U.WRAP)); // pages of 12, slide sideways
            LinearLayout btns = U.row(c);
            btns.addView(U.button(c, "Edit apps", false, () -> editFolder(index)));
            btns.addView(U.gap(c, 10, 1));
            btns.addView(U.button(c, "Rename", false, () -> renameFolder(index)));
            b.addView(btns, top(16));
            return Sheets.panel(spec.optString("name", "Folder"), b, 640, 400);
        });
    }

    static void editFolder(int index) {
        Sheets.push(() -> {
            Context c = Sheets.ctx();
            JSONObject spec = Tiles.specs().optJSONObject(index);
            JSONArray apps = spec.optJSONArray("apps");
            if (apps == null) apps = new JSONArray();
            final JSONArray fa = apps;
            List<View> cells = new ArrayList<>();
            List<Apps.App> all = new ArrayList<>();
            for (int i = 0; i < fa.length(); i++) {
                Apps.App a = Apps.get(fa.optString(i));
                if (a != null) all.add(a);
            }
            for (Apps.App a : Apps.visible()) if (!all.contains(a)) all.add(a);
            for (Apps.App a : all) {
                boolean in = HA.indexOf(fa, a.key) >= 0;
                View cell = appCell(c, a, in, false);
                cell.setOnClickListener(v -> {
                    if (in) HA.remove(fa, a.key);
                    else fa.put(a.key);
                    Widgets.put(spec, "apps", fa);
                    Tiles.setSpec(index, spec);
                    Sheets.refresh();
                });
                cells.add(cell);
            }
            LinearLayout b = U.col(c);
            b.addView(U.text(c, "Tap apps to add or remove them.", 13, U.SUB, false));
            b.addView(grid(c, cells, 6, 6), top(10));
            return Sheets.panel("Apps in " + spec.optString("name"), b, 700, 440);
        });
    }

    static void renameFolder(int index) {
        Sheets.push(() -> {
            Context c = Sheets.ctx();
            JSONObject spec = Tiles.specs().optJSONObject(index);
            final String[] name = {spec.optString("name")};
            LinearLayout b = U.col(c);
            b.addView(Widgets.field("Folder name", name[0], false, t -> name[0] = t), U.lp(U.MATCH, U.WRAP));
            TextView err = U.text(c, "", 12, U.RED, false);
            b.addView(err, top(6));
            b.addView(U.button(c, "Save", true, () -> {
                String n = name[0].trim().isEmpty() ? "Folder" : name[0].trim();
                if (folderNames(index).contains(n.toLowerCase(java.util.Locale.ROOT))) {
                    err.setText("There's already a folder called " + n);
                    return;
                }
                Widgets.put(spec, "name", n);
                Tiles.setSpec(index, spec);
                Sheets.close();
            }), top(16));
            return Sheets.panel("Rename folder", b, 440, 230);
        });
    }

    // ------------------------------------------------------------------ tile menu & widget gallery

    static void tileMenu(int index) {
        Sheets.Page p = () -> {
            Context c = Sheets.ctx();
            JSONObject spec = Tiles.specs().optJSONObject(index);
            boolean isFolder = spec == null || "f".equals(spec.optString("t"));
            String id = spec == null ? "" : spec.optString("id");
            LinearLayout b = U.col(c);
            b.addView(Sheets.item("Replace with widget", null, () -> gallery(index)));
            if (isFolder) {
                b.addView(Sheets.item("Edit apps", null, () -> editFolder(index)));
                b.addView(Sheets.item("Rename folder", null, () -> renameFolder(index)));
            } else {
                if (Widgets.configurable(id)) b.addView(Sheets.item("Widget settings", null, () -> Widgets.configure(index)));
                String kind = Alerts.kindOf(id);
                if (kind != null) b.addView(Sheets.switchRow(0, 0, "Expand alerts into the tile area", Store.flag(Alerts.GLOBAL, false) ? "Off: the alert shows inside this tile" : "All expanding is off in Settings → Alerts",
                        Store.flag("expand_" + kind, true), v -> Store.put("expand_" + kind, v)));
                b.addView(Sheets.item("Replace with a folder", null, () -> {
                    Tiles.setSpec(index, Tiles.folder("Apps", new JSONArray()));
                    Sheets.close();
                    folder(index);
                }));
            }
            b.addView(Sheets.item("Remove tile", null, () -> {
                Tiles.removeTile(index);
                Sheets.back(); // back to the tile editor (or closed)
            }));
            b.addView(U.button(c, "Done editing", true, () -> {
                Sheets.close();
                if (MainActivity.I != null) {
                    MainActivity.I.tiles.setEditing(false);
                    MainActivity.I.tiles.refresh();
                }
            }), top(14));
            String title = isFolder ? spec.optString("name", "Folder") + " folder" : Widgets.name(id);
            return Sheets.panel(title, b, 440, 360);
        };
        // from the tile editor: open on top of it, so Back returns there
        if (Sheets.open()) Sheets.push(p);
        else Sheets.show(p);
    }

    /**
     * Full-screen widget chooser: live previews in two rows that scroll sideways, with Folder as one
     * of the choices. index >= 0: replace that tile. index < 0: manage the home screen — widgets
     * already on it have a blue border; tap to add one, tap a blue one to take it off.
     */
    static Sheets.Page galleryRef; // the open widget chooser (it closes on Done or when idle)

    private static int galleryX; // where the widget list was scrolled to (kept while it refreshes)

    static void gallery(int index) {
        galleryX = 0;
        Sheets.Page page = galleryRef = () -> galleryPage(index);
        if (Sheets.open()) Sheets.pushFull(page);
        else Sheets.showFull(page);
    }

    private static View galleryPage(int index) {
        Context c = Sheets.ctx();
        boolean manage = index < 0;
        LinearLayout card = U.col(c);
        card.setBackground(U.round(U.CARD, 18));
        U.pad(card, 20, 14, 20, 14);
        card.setLayoutParams(Sheets.centered(784, 464));

        LinearLayout head = U.row(c);
        LinearLayout titles = U.col(c);
        titles.addView(U.line(c, manage ? "Home screen widgets" : "Choose a widget", 20, U.TEXT, true));
        titles.addView(U.line(c, manage ? "Highlighted ones are on your home screen · tap a widget to add it or take it off" : "Tap one to put it on this tile · swipe for more", 12, U.SUB, false), top(2));
        head.addView(titles, new LinearLayout.LayoutParams(0, U.WRAP, 1));
        if (manage) {
            head.addView(U.button(c, "Add all", false, () -> { // every widget not on the home screen yet
                JSONArray s = Tiles.specs();
                java.util.Set<String> have = new java.util.HashSet<>();
                for (int i = 0; i < s.length(); i++) {
                    JSONObject o = s.optJSONObject(i);
                    if (o != null) have.add(o.optString("id"));
                }
                for (String[] w : Widgets.CATALOG) if (Faith.widgetAllowed(w[0]) && have.add(w[0])) s.put(Tiles.widget(w[0]));
                Tiles.save(s);
                if (MainActivity.I != null) MainActivity.I.tiles.refresh();
                Sheets.refresh();
            }));
            head.addView(U.gap(c, 10, 1));
        }
        head.addView(U.button(c, "Add folder", false, () -> newFolder(index)));
        head.addView(U.gap(c, 10, 1));
        head.addView(U.button(c, "Done", true, () -> {
            Sheets.back();
            if (MainActivity.I != null) MainActivity.I.tiles.refresh();
        }));
        card.addView(head);

        // what's on the home screen now
        JSONArray specs = Tiles.specs();
        java.util.Map<String, Integer> on = new java.util.HashMap<>();
        for (int i = 0; i < specs.length(); i++) {
            JSONObject o = specs.optJSONObject(i);
            if (o == null) continue;
            String k = "f".equals(o.optString("t")) ? "folder" : o.optString("id");
            Integer n = on.get(k);
            on.put(k, n == null ? 1 : n + 1);
        }
        String current = null;
        if (!manage) {
            JSONObject o = specs.optJSONObject(index);
            if (o != null) current = "f".equals(o.optString("t")) ? "folder" : o.optString("id");
        }

        final String finalCurrent = current;
        List<String[]> choices = new ArrayList<>();
        for (String[] w : Widgets.CATALOG) if (Faith.widgetAllowed(w[0])) choices.add(w);

        // group by category, each with a heading; chips at the top jump to a category
        String[][] cats = {{"Driving", "speed,trip,rest,park,safety,logbook,heading,goto"}, {"Phone", "phone,quickdial,contact"},
                {"Car & home", "carconnect,ha"}, {"Weather", "weather"}, {"Faith", "nextprayer,prayertimes,qibla"}};
        LinearLayout blocks = U.row(c);
        blocks.setGravity(Gravity.TOP);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        LinearLayout chips = U.row(c);
        java.util.Set<String> placed = new java.util.HashSet<>();
        for (int ci = 0; ci <= cats.length; ci++) {
            List<String[]> group = new ArrayList<>();
            String cat = ci < cats.length ? cats[ci][0] : "More";
            for (String[] w : choices) {
                boolean in = ci < cats.length ? ("," + cats[ci][1] + ",").contains("," + w[0] + ",") : !placed.contains(w[0]);
                if (in && placed.add(w[0])) group.add(w);
            }
            if (group.isEmpty()) continue;
            LinearLayout block = U.col(c);
            TextView ht = U.line(c, cat.toUpperCase(java.util.Locale.ROOT), 11, U.SUB, true);
            ht.setLetterSpacing(0.08f);
            U.pad(ht, 6, 0, 0, 0);
            block.addView(ht);
            LinearLayout cols = U.row(c);
            cols.setGravity(Gravity.TOP);
            for (int k = 0; k < group.size(); k += 2) {
                LinearLayout col = U.col(c);
                for (int r = 0; r < 2; r++) {
                    if (k + r < group.size()) col.addView(cell(c, group.get(k + r), index, manage, on, finalCurrent), cellLp());
                    else col.addView(new View(c), cellLp()); // keeps a lone widget the same size as the rest
                }
                cols.addView(col, new LinearLayout.LayoutParams(U.WRAP, U.MATCH));
            }
            block.addView(cols, new LinearLayout.LayoutParams(U.WRAP, 0, 1));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(U.WRAP, U.MATCH);
            bl.rightMargin = U.u(14);
            blocks.addView(block, bl);
            TextView chip = U.seg(c, cat, false, () -> hs.smoothScrollTo(block.getLeft(), 0));
            LinearLayout.LayoutParams chl = U.lp(U.WRAP, U.WRAP);
            chl.rightMargin = U.u(8);
            chips.addView(chip, chl);
        }
        card.addView(chips, top(10));
        hs.setHorizontalScrollBarEnabled(false);
        hs.setFillViewport(true);
        hs.addView(blocks, new android.widget.FrameLayout.LayoutParams(U.WRAP, U.MATCH));
        // stay where you were after adding or removing one (the page is rebuilt each time)
        hs.setOnScrollChangeListener((v, x, y, ox, oy) -> galleryX = x);
        final int keep = galleryX;
        hs.post(() -> hs.scrollTo(keep, 0));
        LinearLayout.LayoutParams hl = U.weight(1, false);
        hl.topMargin = U.u(8);
        card.addView(hs, hl);
        return card;
    }

    private static LinearLayout.LayoutParams cellLp() {
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(U.u(116), 0, 1); // every cell the same size
        cl.setMargins(U.u(5), U.u(5), U.u(5), U.u(5));
        return cl;
    }

    /** One choice: live preview + name; blue border when it's on the home screen (or on this tile). */
    private static View cell(Context c, String[] w, int index, boolean manage, java.util.Map<String, Integer> on, String current) {
        boolean sel = manage ? on.containsKey(w[0]) : w[0].equals(current);
        LinearLayout cell = U.col(c);
        U.pad(cell, 5, 5, 5, 6);
        cell.setBackground(sel ? U.outline(0xFF15233A, U.ACCENT, 3, 16) : U.round(U.CARD2, 16));
        cell.addView(Widgets.preview(c, Tiles.widget(w[0])), new LinearLayout.LayoutParams(U.MATCH, 0, 1));
        Integer cnt = on.get(w[0]);
        String label = Widgets.multiple(w[0]) && manage ? w[1] + (cnt != null ? " (" + cnt + ")" : "") + " · tap to add" : w[1];
        TextView nm = U.line(c, label, 12, sel ? U.ACCENT_SOFT : U.TEXT, true);
        nm.setGravity(Gravity.CENTER);
        cell.addView(nm, top(5));
        cell.setOnClickListener(v -> {
            if (manage) pick(index, w[0], on.containsKey(w[0]) && !Widgets.multiple(w[0])); // a contact adds another
            else if (on.containsKey(w[0]) && !w[0].equals(current) && !Widgets.multiple(w[0])) // each widget only once (contacts can repeat)
                android.widget.Toast.makeText(c, Widgets.name(w[0]) + " is already on your home screen", android.widget.Toast.LENGTH_SHORT).show();
            else pick(index, w[0], false);
        });
        U.press(cell);
        return cell;
    }

    /** Names of the folders on the home screen (lower case), except the one at `skip`. */
    private static java.util.Set<String> folderNames(int skip) {
        java.util.Set<String> n = new java.util.HashSet<>();
        JSONArray s = Tiles.specs();
        for (int i = 0; i < s.length(); i++) {
            JSONObject o = s.optJSONObject(i);
            if (i != skip && o != null && "f".equals(o.optString("t"))) n.add(o.optString("name").trim().toLowerCase(java.util.Locale.ROOT));
        }
        return n;
    }

    /** Ask for a name (must be new), then add the folder (or put it on the tile being replaced) and choose its apps. */
    private static void newFolder(int index) {
        Sheets.pushFull(() -> {
            Context c = Sheets.ctx();
            final String[] name = {""};
            LinearLayout b = U.col(c);
            TextView err = U.text(c, "", 12, U.RED, false);
            b.addView(Widgets.field("Folder name, e.g. Music", "", false, t -> {
                name[0] = t;
                err.setText("");
            }), U.lp(U.MATCH, U.WRAP));
            b.addView(err, top(6));
            b.addView(U.button(c, "Add folder", true, () -> {
                String n = name[0].trim();
                if (n.isEmpty()) {
                    err.setText("Give the folder a name");
                    return;
                }
                if (folderNames(index).contains(n.toLowerCase(java.util.Locale.ROOT))) {
                    err.setText("There's already a folder called " + n);
                    return;
                }
                JSONObject f = Tiles.folder(n, new JSONArray());
                int at;
                if (index >= 0) {
                    Tiles.setSpec(index, f);
                    at = index;
                } else {
                    JSONArray s = Tiles.specs();
                    s.put(f);
                    Tiles.save(s);
                    if (MainActivity.I != null) MainActivity.I.tiles.refresh();
                    at = s.length() - 1;
                }
                Sheets.back(); // close this name box
                editFolder(at);
            }), top(10));
            LinearLayout card = (LinearLayout) Sheets.panel("New folder", b, 440, 250);
            return card;
        });
    }

    /** What a new folder tile looks like. */
    private static View folderPreview(Context c) {
        LinearLayout l = U.col(c);
        l.setGravity(Gravity.CENTER);
        l.setBackground(U.round(U.CARD, 14));
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(U.oval(U.CARD3));
        ic.addView(U.icon(c, R.drawable.ic_folder, U.TEXT), U.flpu(26, 26, Gravity.CENTER));
        l.addView(ic, U.lpu(52, 52));
        l.addView(U.line(c, "Apps", 14, U.TEXT, true), top(8));
        return l;
    }

    /** Act on a choice: replace the tile, or (manage) add it / take it off the home screen. */
    private static void pick(int index, String id, boolean takeOff) {
        boolean folder = "folder".equals(id);
        JSONObject spec = folder ? Tiles.folder("Apps", new JSONArray()) : Tiles.widget(id);
        int at;
        if (index >= 0) {
            Tiles.setSpec(index, spec);
            at = index;
        } else if (takeOff) {
            JSONArray s = Tiles.specs();
            for (int i = s.length() - 1; i >= 0; i--) {
                JSONObject o = s.optJSONObject(i);
                if (o != null && id.equals(o.optString("id")) && "w".equals(o.optString("t"))) {
                    Tiles.removeTile(i);
                    break;
                }
            }
            Sheets.refresh();
            return;
        } else {
            JSONArray s = Tiles.specs();
            s.put(spec);
            Tiles.save(s);
            if (MainActivity.I != null) MainActivity.I.tiles.refresh();
            at = s.length() - 1;
        }
        if (folder) {
            editFolder(at);
            return;
        }
        Sheets.refresh(); // stay open: the new choice shows in blue; Done (or leaving it idle) closes
    }

    // ------------------------------------------------------------------ auto start

    static void autostart() {
        Sheets.Page page = () -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            b.addView(Sheets.switchRow(R.drawable.ic_bolt, U.GREEN, "Auto start apps", "Opens these apps in order when the car starts",
                    Store.flag(Store.AUTOSTART_ON, true), on -> Store.put(Store.AUTOSTART_ON, on)));
            JSONArray list = Actions.autostart();
            for (int i = 0; i < list.length(); i++) {
                final int idx = i;
                JSONObject o = list.optJSONObject(i);
                Apps.App a = Apps.get(o.optString("key"));
                if (a == null) continue;
                LinearLayout r = U.row(c);
                U.pad(r, 0, 8, 0, 8);
                r.addView(U.text(c, (i + 1) + ".", 14, U.SUB, true), U.lpu(22, U.WRAP));
                ImageView iv = new ImageView(c);
                iv.setImageDrawable(a.icon);
                r.addView(iv, U.lpu(32, 32));
                TextView n = U.line(c, a.label, 14, U.TEXT, false);
                LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
                nl.leftMargin = U.u(10);
                r.addView(n, nl);
                int wait = o.optInt("wait", 3);
                r.addView(U.seg(c, "Wait " + wait + " s", false, () -> {
                    int[] steps = {0, 3, 5, 10, 20};
                    int next = steps[0];
                    for (int k = 0; k < steps.length; k++) if (steps[k] == wait) next = steps[(k + 1) % steps.length];
                    Widgets.put(o, "wait", next);
                    Store.put(Store.AUTOSTART, list);
                    Sheets.refresh();
                }));
                r.addView(U.gap(c, 6, 1));
                boolean stay = "stay".equals(o.optString("mode"));
                r.addView(U.seg(c, stay ? "Stay open" : "Background", stay, () -> {
                    Widgets.put(o, "mode", stay ? "bg" : "stay");
                    Store.put(Store.AUTOSTART, list);
                    Sheets.refresh();
                }));
                r.addView(U.gap(c, 6, 1));
                TextView up = U.text(c, "▲", 14, idx == 0 ? U.MUTED : U.TEXT2, false);
                U.pad(up, 8, 6, 8, 6);
                up.setOnClickListener(v -> {
                    if (idx == 0) return;
                    JSONArray out = new JSONArray();
                    for (int k = 0; k < list.length(); k++) {
                        if (k == idx - 1) out.put(list.optJSONObject(idx));
                        else if (k == idx) out.put(list.optJSONObject(idx - 1));
                        else out.put(list.optJSONObject(k));
                    }
                    Store.put(Store.AUTOSTART, out);
                    Sheets.refresh();
                });
                r.addView(up);
                View rm = U.icon(c, R.drawable.ic_x, U.SUB);
                rm.setOnClickListener(v -> {
                    removeAutostart(a.key);
                    Sheets.refresh();
                });
                r.addView(rm, U.lpu(20, 20));
                b.addView(r);
            }
            b.addView(Sheets.item("Add app", null, () -> appPicker("Add to auto start", a -> {
                Actions.addAutostart(a.key);
                Sheets.back();
                Sheets.refresh();
            })));
            String show = Store.str(Store.AUTOSTART_SHOW, "home");
            b.addView(Sheets.row("When they've started, show", U.seg(c, "Home screen", "home".equals(show), () -> {
                Store.put(Store.AUTOSTART_SHOW, "home");
                Sheets.refresh();
            }), U.seg(c, "Last app", "last".equals(show), () -> {
                Store.put(Store.AUTOSTART_SHOW, "last");
                Sheets.refresh();
            })));
            return Sheets.panel("Auto start", b, 680, 440);
        };
        if (Sheets.open()) Sheets.push(page);
        else Sheets.show(page);
    }
}
