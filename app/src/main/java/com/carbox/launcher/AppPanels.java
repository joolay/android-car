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
        LinearLayout l = Ui.col(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.pad(l, 2, 8, 2, 6);
        if (selected) l.setBackground(Ui.round(0x333D8BFD, 12));
        FrameLayout f = new FrameLayout(c);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(a.icon);
        f.addView(iv, Ui.flpu(icon, icon, Gravity.CENTER));
        if (badge) {
            FrameLayout b = new FrameLayout(c);
            b.setBackground(Ui.oval(Ui.GREEN));
            b.addView(Ui.icon(c, R.drawable.ic_bolt, Ui.TEXT), Ui.flpu(10, 10, Gravity.CENTER));
            f.addView(b, Ui.flpu(16, 16, Gravity.TOP | Gravity.END));
        }
        if (selected) {
            FrameLayout b = new FrameLayout(c);
            b.setBackground(Ui.oval(Ui.ACCENT));
            b.addView(Ui.icon(c, R.drawable.ic_check, Ui.TEXT), Ui.flpu(11, 11, Gravity.CENTER));
            f.addView(b, Ui.flpu(18, 18, Gravity.TOP | Gravity.START));
        }
        l.addView(f, Ui.lpu(icon + 6, icon + 4));
        TextView t = Ui.line(c, a.label, icon < 48 ? 11 : 12, Ui.TEXT2, false);
        t.setGravity(Gravity.CENTER);
        l.addView(t, top(4));
        return l;
    }

    /** Car Launcher Settings as an app-style cell: gear on a rounded tile, same size as app icons. */
    static View settingsCell(Context c) {
        LinearLayout l = Ui.col(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.pad(l, 2, 8, 2, 6);
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.round(Ui.CARD3, 12));
        ic.addView(Ui.icon(c, R.drawable.ic_gear, Ui.TEXT), Ui.flpu(28, 28, Gravity.CENTER));
        FrameLayout f = new FrameLayout(c);
        f.addView(ic, Ui.flpu(52, 52, Gravity.CENTER));
        l.addView(f, Ui.lpu(58, 56));
        TextView t = Ui.line(c, "Settings", 12, Ui.TEXT2, false);
        t.setGravity(Gravity.CENTER);
        l.addView(t, top(4));
        Ui.press(l);
        return l;
    }

    /** Lay views out in rows of `cols`. */
    static LinearLayout grid(Context c, List<View> cells, int cols, float gap) {
        LinearLayout g = Ui.col(c);
        for (int i = 0; i < cells.size(); i += cols) {
            LinearLayout r = Ui.row(c);
            r.setGravity(Gravity.TOP);
            for (int k = 0; k < cols; k++) {
                View v = i + k < cells.size() ? cells.get(i + k) : new View(c);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
                if (k > 0) lp.leftMargin = Ui.u(gap);
                r.addView(v, lp);
            }
            LinearLayout.LayoutParams rl = Ui.lp(Ui.MATCH, Ui.WRAP);
            if (i > 0) rl.topMargin = Ui.u(gap);
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
        Panels.showFull(AppPanels::allAppsPage); // a full-screen card
    }

    private static View allAppsPage() {
        Context c = Panels.ctx();
        LinearLayout card = Ui.col(c);
        card.setBackground(Themes.card(Ui.CARD, 16));
        Ui.pad(card, 16, 12, 16, 12);
        card.setLayoutParams(Panels.centered(760, 452));

        LinearLayout head = Ui.row(c);
        // title with the Settings gear beside it
        LinearLayout tbox = Ui.row(c);
        tbox.addView(Ui.line(c, selecting ? selection.size() + " selected" : "All apps", 20, Ui.TEXT, true));
        if (!selecting) {
            android.widget.FrameLayout gear = new android.widget.FrameLayout(c);
            gear.setBackground(Themes.inner(Ui.CARD2, 24));
            gear.addView(Ui.icon(c, R.drawable.ic_gear, Ui.TEXT), Ui.flpu(20, 20, Gravity.CENTER));
            gear.setOnClickListener(v -> {
                Panels.close();
                SettingsPanels.main();
            });
            gear.setContentDescription("Settings");
            Ui.press(gear);
            LinearLayout.LayoutParams gl = Ui.lpu(36, 36);
            gl.leftMargin = Ui.u(12);
            tbox.addView(gear, gl);
        }
        head.addView(tbox, Ui.lpu(210, Ui.WRAP));
        LinearLayout searchBox = Ui.row(c);
        searchBox.setBackground(Themes.inner(Ui.CARD2, 12));
        Ui.pad(searchBox, 10, 0, 6, 0);
        searchBox.addView(Ui.icon(c, R.drawable.ic_search, Ui.SUB), Ui.lpu(18, 18));
        LinearLayout results = Ui.col(c);
        EditText search = Widgets.field("Search apps", query, false, t -> {
            query = t;
            fillApps(c, results);
        });
        search.setBackground(null);
        searchBox.addView(search, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        head.addView(searchBox, new LinearLayout.LayoutParams(0, Ui.u(38), 1));
        // zoom: − smaller icons (more across), + bigger icons (fewer across); remembered
        head.addView(Ui.gap(c, 10, 1));
        head.addView(zoomBtn(c, R.drawable.ic_minus, "Smaller icons", -1, results), Ui.lpu(36, 36));
        head.addView(Ui.gap(c, 6, 1));
        head.addView(zoomBtn(c, R.drawable.ic_plus, "Bigger icons", 1, results), Ui.lpu(36, 36));
        head.addView(Ui.gap(c, 10, 1));
        head.addView(Ui.seg(c, selecting ? "Cancel" : "Select", selecting, () -> {
            selecting = !selecting;
            selection.clear();
            Panels.refresh();
        }));
        head.addView(Ui.gap(c, 10, 1));
        View x = Ui.icon(c, R.drawable.ic_x, Ui.SUB);
        x.setOnClickListener(v -> Panels.close());
        head.addView(x, Ui.lpu(28, 28));
        card.addView(head);

        LinearLayout.LayoutParams sl = Ui.weight(1, false); // pages of apps (slide sideways), dots underneath
        sl.topMargin = Ui.u(12);
        card.addView(results, sl);
        fillApps(c, results);

        if (selecting) {
            LinearLayout bar = Ui.row(c);
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
                Panels.refresh();
            }));
            bar.addView(action(c, "Hide", any, () -> {
                for (String k : selection) Apps.setHidden(k, true);
                selection.clear();
                selecting = false;
                Panels.refresh();
            }));
            card.addView(bar, top(10));
        }
        return card;
    }

    private static View action(Context c, String label, boolean enabled, Runnable r) {
        TextView t = Ui.textButton(c, label, enabled, enabled ? r : null);
        t.setAlpha(enabled ? 1f : 0.45f);
        LinearLayout.LayoutParams lp = Ui.lp(Ui.WRAP, Ui.WRAP);
        lp.setMargins(Ui.u(5), 0, Ui.u(5), 0);
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
                Panels.close();
                SettingsPanels.main();
            });
            cells.add(s);
        }
        for (Apps.App a : Apps.search(query)) {
            View cell = appCell(c, a, selecting && selection.contains(a.key), Actions.isAutostart(a.key), ZOOM_ICON[zoom()]);
            cell.setOnClickListener(v -> {
                if (selecting) {
                    if (!selection.remove(a.key)) selection.add(a.key);
                    Panels.refresh();
                } else {
                    Panels.close();
                    Apps.launch(c, a);
                }
            });
            cell.setOnLongClickListener(v -> {
                appMenu(a, Panels::refresh);
                return true;
            });
            cells.add(cell);
        }
        if (cells.isEmpty()) results.addView(Ui.text(c, "No apps match", 14, Ui.SUB, false));
        else // zoom sets how many across; as many rows as fit, page dots at the bottom
            results.addView(new Pager(c, cells, ZOOM_COLS[zoom()], 0, 6), new LinearLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
    }

    // zoom levels for All apps: icons across and icon size
    private static final int[] ZOOM_COLS = {9, 7, 5, 4};
    private static final float[] ZOOM_ICON = {40, 52, 66, 80};

    private static int zoom() {
        return Math.max(0, Math.min(ZOOM_COLS.length - 1, Prefs.num("apps_zoom", 1)));
    }

    private static View zoomBtn(Context c, int icon, String desc, int step, LinearLayout results) {
        android.widget.FrameLayout b = new android.widget.FrameLayout(c);
        b.setBackground(Themes.inner(Ui.CARD2, 24));
        b.addView(Ui.icon(c, icon, Ui.TEXT), Ui.flpu(18, 18, Gravity.CENTER));
        b.setContentDescription(desc);
        b.setOnClickListener(v -> {
            int z = Math.max(0, Math.min(ZOOM_COLS.length - 1, zoom() + step));
            if (z == zoom()) return;
            Prefs.put("apps_zoom", z);
            fillApps(c, results); // keeps the search text and keyboard as they are
        });
        Ui.press(b);
        return b;
    }

    private static void newFolderFromSelection() {
        final List<String> keys = new ArrayList<>(selection);
        Panels.push(() -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            final String[] name = {"Folder"};
            b.addView(Panels.section("Folder name"));
            b.addView(Widgets.field("Folder name", "", false, t -> name[0] = t.isEmpty() ? "Folder" : t), Ui.lp(Ui.MATCH, Ui.WRAP));
            b.addView(Panels.section("Put it in place of"));
            JSONArray specs = TileDrawer.specs();
            List<View> cells = new ArrayList<>();
            for (int i = 0; i < TileDrawer.count(); i++) {
                final int idx = i;
                JSONObject s = specs.optJSONObject(i);
                String label = s == null ? "Empty" : "f".equals(s.optString("t")) ? s.optString("name") + " folder" : Widgets.name(s.optString("id"));
                TextView t = Ui.textButton(c, (i + 1) + ". " + label, false, () -> {
                    JSONArray apps = new JSONArray();
                    for (String k : keys) apps.put(k);
                    TileDrawer.setSpec(idx, TileDrawer.folder(name[0], apps));
                    selection.clear();
                    selecting = false;
                    Panels.close();
                });
                cells.add(t);
            }
            b.addView(grid(c, cells, 2, 8));
            return Panels.panel("New folder", b);
        });
    }

    private static void addSelectionToFolder() {
        final List<String> keys = new ArrayList<>(selection);
        Panels.push(() -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            JSONArray specs = TileDrawer.specs();
            boolean any = false;
            for (int i = 0; i < TileDrawer.count(); i++) {
                final int idx = i;
                JSONObject s = specs.optJSONObject(i);
                if (s == null || !"f".equals(s.optString("t"))) continue;
                any = true;
                JSONArray apps = s.optJSONArray("apps");
                b.addView(Panels.item(s.optString("name"), (apps == null ? 0 : apps.length()) + " apps", () -> {
                    JSONArray a = s.optJSONArray("apps");
                    if (a == null) a = new JSONArray();
                    for (String k : keys) if (HA.indexOf(a, k) < 0) a.put(k);
                    Widgets.put(s, "apps", a);
                    TileDrawer.setSpec(idx, s);
                    selection.clear();
                    selecting = false;
                    Panels.close();
                }));
            }
            if (!any) b.addView(Ui.text(c, "There are no folders on the home screen yet. Use New folder instead.", 14, Ui.SUB, false));
            return Panels.panel("Add to folder", b, 520, 380);
        });
    }

    // ------------------------------------------------------------------ app menu (long-press)

    static void appMenu(Apps.App a, Runnable after) {
        Runnable done = () -> {
            if (after != null) after.run();
            if (MainActivity.I != null) MainActivity.I.dock.render();
        };
        Panels.Page page = () -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            LinearLayout head = Ui.row(c);
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(a.icon);
            head.addView(iv, Ui.lpu(40, 40));
            TextView t = Ui.line(c, a.label, 18, Ui.TEXT, true);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
            tl.leftMargin = Ui.u(12);
            head.addView(t, tl);
            View x = Ui.icon(c, R.drawable.ic_x, Ui.SUB);
            x.setOnClickListener(v -> Panels.close());
            head.addView(x, Ui.lpu(26, 26));
            b.addView(head);
            b.addView(Panels.switchRow(R.drawable.ic_pin, Ui.ACCENT, "Pin to dock", null, Apps.pinned(a.key), on -> {
                Apps.setPinned(a.key, on);
                done.run();
            }));
            b.addView(Panels.switchRow(R.drawable.ic_bolt, Ui.GREEN, "Auto start", "Opens when the car starts", Actions.isAutostart(a.key), on -> {
                if (on) Actions.addAutostart(a.key);
                else removeAutostart(a.key);
                done.run();
            }));
            b.addView(Panels.switchRow(R.drawable.ic_hide, Ui.SUB, "Hide from All apps", "Unhide in Settings → Hidden from All apps", Apps.hidden().contains(a.key), on -> {
                Apps.setHidden(a.key, on);
                if (MainActivity.I != null) MainActivity.I.dock.render();
            }));
            b.addView(Panels.switchRow(R.drawable.ic_hide, Ui.SUB, "Hide from Dock", "Still in All apps", Apps.dockHidden().contains(a.key), on -> {
                Apps.setDockHidden(a.key, on);
                if (MainActivity.I != null) MainActivity.I.dock.render();
            }));
            LinearLayout btns = Ui.row(c);
            btns.addView(Ui.button(c, "App info", false, () -> {
                Apps.appInfo(c, a.pkg);
                Panels.close();
            }));
            b.addView(btns, top(10));
            b.setBackground(Themes.card(Ui.CARD, 20));
            Ui.pad(b, 22, 18, 22, 18);
            b.setLayoutParams(Panels.centered(400, -2));
            return b;
        };
        if (Panels.open()) Panels.push(page);
        else Panels.showCard(page);
    }

    static void removeAutostart(String key) {
        JSONArray a = Actions.autostart(), out = new JSONArray();
        for (int i = 0; i < a.length(); i++) if (!key.equals(a.optJSONObject(i).optString("key"))) out.put(a.optJSONObject(i));
        Prefs.put(Prefs.AUTOSTART, out);
    }

    // ------------------------------------------------------------------ pickers

    static void appPicker(String title, Ui.Cb<Apps.App> cb) {
        final String[] q = {""};
        Panels.Page page = () -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            LinearLayout list = Ui.col(c);
            b.addView(Widgets.field("Search apps", q[0], false, t -> {
                q[0] = t;
                fillPicker(c, list, t, cb);
            }), Ui.lp(Ui.MATCH, Ui.WRAP));
            b.addView(list, top(10));
            fillPicker(c, list, q[0], cb);
            return Panels.panel(title, b, 680, 440);
        };
        if (Panels.open()) Panels.push(page);
        else Panels.show(page);
    }

    private static void fillPicker(Context c, LinearLayout list, String q, Ui.Cb<Apps.App> cb) {
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
    static void btPhonePicker(Ui.Cb<Apps.App> cb) {
        Panels.Page page = () -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "Calls go out through your phone over Bluetooth. Which app on this box is the Bluetooth phone?", 13, Ui.SUB, false));
            String cur = Actions.btDialler();
            int n = 0;
            for (Apps.App a : Apps.visible()) {
                if (!Actions.looksLikeBtPhone(a)) continue;
                n++;
                b.addView(SettingsPanels.appRow(c, a, a.pkg.equals(cur), () -> cb.on(a)));
            }
            if (n == 0) b.addView(Ui.text(c, "No Bluetooth phone app recognised.", 14, Ui.TEXT2, false), top(10));
            b.addView(Panels.item("Other app…", null, () -> appPicker("Bluetooth phone app", cb)), top(4));
            return Panels.panel("Bluetooth phone app", b, 560, 420);
        };
        if (Panels.open()) Panels.push(page);
        else Panels.show(page);
    }

    // ------------------------------------------------------------------ Car Connect

    static void connect() {
        Panels.show(() -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            Object[][] lines = {
                    {"carplay", R.drawable.ic_phone, 0xFF1A2740, 0xFF85B7EB, "iPhone, through your bridge app"},
                    {"aa", R.drawable.ic_navarrow, 0xFF143B2A, Ui.TEAL, "Android phone, through your bridge app"},
                    {"mirror", R.drawable.ic_cast, 0xFF2A1F38, 0xFFC7A6FF, "Shows your phone's screen as it is"},
                    {"native", R.drawable.ic_car, 0xFF3A2A1A, Ui.AMBER, "Back to the car's own system"}};
            // two by two, sized to the tile area so all four fit without scrolling
            int area = MainActivity.I != null && MainActivity.I.tileArea.getHeight() > 0 ? MainActivity.I.tileArea.getHeight() : Ui.u(300);
            int cardH = Math.max(Ui.u(78), (area - Ui.u(14 + 28 + 14 + 14) - Ui.u(10) - Ui.u(30)) / 2);
            LinearLayout row = null;
            for (int i = 0; i < lines.length; i++) {
                final String which = (String) lines[i][0];
                Apps.App app = Actions.connectApp(which);
                View card = optionCard(c, (Integer) lines[i][1], (Integer) lines[i][2], (Integer) lines[i][3], Actions.connectTitle(which), (String) lines[i][4],
                        app == null ? "Choose app" : app.label,
                        () -> Actions.carConnect(c, which), () -> bridgePicker(Actions.connectTitle(which) + " app", Actions.connectKey(which)));
                if (i % 2 == 0) {
                    row = Ui.row(c);
                    LinearLayout.LayoutParams rl = Ui.lp(Ui.MATCH, cardH);
                    if (i > 0) rl.topMargin = Ui.u(10);
                    b.addView(row, rl);
                }
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, Ui.MATCH, 1);
                if (i % 2 == 1) cl.leftMargin = Ui.u(10);
                row.addView(card, cl);
            }
            b.addView(Ui.line(c, "Tap a card to open it · tap the app name to change it", 12, Ui.SUB, false), top(8));
            return Panels.panel("Car Connect", b);
        });
    }

    /** A Car Connect choice as a card: icon and title on top, one line of text, the app to change at the bottom. */
    private static View optionCard(Context c, int icon, int bg, int tint, String title, String sub, String app, Runnable open, Runnable change) {
        LinearLayout r = Ui.col(c);
        r.setBackground(Themes.inner(Ui.CARD2, 16));
        Ui.pad(r, 12, 10, 10, 6);
        LinearLayout top = Ui.row(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.round(bg, 11));
        ic.addView(Ui.icon(c, icon, tint), Ui.flpu(22, 22, Gravity.CENTER));
        top.addView(ic, Ui.lpu(40, 40));
        TextView tt = Ui.line(c, title, 15, Ui.TEXT, true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        tl.leftMargin = Ui.u(10);
        top.addView(tt, tl);
        r.addView(top);
        TextView st = Ui.line(c, sub, 12, Ui.SUB, false);
        r.addView(st, top(6));
        r.addView(new View(c), Ui.weight(1, false));
        TextView ch = Ui.line(c, app + " ›", 12, Ui.ACCENT_SOFT, true);
        Ui.pad(ch, 0, 6, 8, 4);
        ch.setOnClickListener(v -> change.run());
        r.addView(ch);
        r.setOnClickListener(v -> open.run());
        Ui.press(r);
        return r;
    }

    private static View option(Context c, int icon, int bg, int tint, String title, String sub, String app, Runnable open, Runnable change) {
        LinearLayout r = Ui.row(c);
        r.setBackground(Themes.inner(Ui.CARD2, 16));
        Ui.pad(r, 16, 0, 12, 0);
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.round(bg, 13));
        ic.addView(Ui.icon(c, icon, tint), Ui.flpu(26, 26, Gravity.CENTER));
        r.addView(ic, Ui.lpu(48, 48));
        LinearLayout t = Ui.col(c);
        t.addView(Ui.line(c, title, 16, Ui.TEXT, true));
        t.addView(Ui.line(c, sub, 12, Ui.SUB, false), top(3));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        tl.leftMargin = Ui.u(16);
        r.addView(t, tl);
        TextView ch = Ui.line(c, app + " ›", 13, Ui.ACCENT_SOFT, true);
        Ui.pad(ch, 12, 14, 4, 14);
        ch.setOnClickListener(v -> change.run());
        r.addView(ch);
        r.setOnClickListener(v -> open.run());
        Ui.press(r);
        return r;
    }

    /** Choose the bridge / mirror / native app. Known bridge apps are listed first. */
    static void bridgePicker(String title, String storeKey) {
        Panels.Page page = () -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            String cur = Prefs.str(storeKey, null);
            List<Apps.App> likely = new ArrayList<>();
            boolean bridge = Prefs.BRIDGE_PKG.equals(storeKey) || Prefs.CARPLAY_PKG.equals(storeKey) || Prefs.AA_PKG.equals(storeKey);
            for (Apps.App a : Apps.visible()) {
                String l = a.label.toLowerCase(java.util.Locale.ROOT);
                if (bridge ? Actions.looksLikeBridge(a) : Prefs.MIRROR_PKG.equals(storeKey) ? l.contains("mirror") : (l.equals("car") || l.contains("car")))
                    likely.add(a);
            }
            if (bridge) {
                b.addView(Ui.text(c, "Speed Play, ZLink, AutoKit, CarbitLink and TLink are found automatically.", 12, Ui.SUB, false));
                if (likely.isEmpty()) b.addView(Ui.text(c, "No bridge app found on this box.", 14, Ui.TEXT2, false), top(10));
            }
            for (Apps.App a : likely) {
                LinearLayout r = Ui.row(c);
                r.setMinimumHeight(Ui.u(50));
                ImageView iv = new ImageView(c);
                iv.setImageDrawable(a.icon);
                r.addView(iv, Ui.lpu(34, 34));
                TextView t = Ui.line(c, a.label, 15, Ui.TEXT, a.pkg.equals(cur));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
                tl.leftMargin = Ui.u(12);
                r.addView(t, tl);
                if (a.pkg.equals(cur)) r.addView(Ui.icon(c, R.drawable.ic_check, Ui.ACCENT), Ui.lpu(20, 20));
                r.setOnClickListener(v -> {
                    Prefs.put(storeKey, a.pkg);
                    Panels.close();
                    Apps.launch(c, a);
                });
                b.addView(r);
            }
            b.addView(Panels.item("Other app…", null, () -> appPicker(title, a -> {
                Prefs.put(storeKey, a.pkg);
                Panels.close();
                Apps.launch(c, a);
            })), top(4));
            return Panels.panel(title, b, 520, 400);
        };
        if (Panels.open()) Panels.push(page);
        else Panels.show(page);
    }

    // ------------------------------------------------------------------ folders

    // a tile folder's expanded view works like the Now Playing folder: hold an app and they all jiggle;
    // drag to move, the red − takes one out, + adds one; tap an app or an empty spot to finish
    private static int editingFolder = -1;
    private static final List<android.animation.Animator> jiggles = new ArrayList<>();

    private static void jiggle(View v, int i) {
        android.animation.ObjectAnimator an = android.animation.ObjectAnimator.ofFloat(v, "rotation", -2.5f, 2.5f);
        an.setDuration(120 + (i % 2) * 15);
        an.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        an.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        an.setStartDelay(i * 30L);
        an.start();
        jiggles.add(an);
    }

    private static void saveFolder(int index, List<String> keys) {
        JSONArray na = new JSONArray();
        for (String k : keys) na.put(k);
        JSONObject sp = TileDrawer.specs().optJSONObject(index);
        if (sp == null) return;
        Widgets.put(sp, "apps", na);
        TileDrawer.setSpec(index, sp);
    }

    /** The red − on an app's top-right corner (same as the Now Playing folder's). */
    private static View minusBadge(Context c, Runnable remove, String name) {
        FrameLayout m = new FrameLayout(c);
        m.setBackground(Ui.oval(Ui.RED));
        m.setElevation(Ui.uf(2));
        m.addView(Ui.icon(c, R.drawable.ic_minus, Ui.TEXT), Ui.flpu(17, 17, Gravity.CENTER));
        m.setContentDescription("Remove " + name);
        m.setOnClickListener(v -> remove.run());
        return m;
    }

    static void folder(int index) {
        editingFolder = -1;
        Ui.ui.post(() -> Panels.whenClosed(() -> { // after it opens, so opening doesn't count as closing
            editingFolder = -1;
            for (android.animation.Animator an : jiggles) an.cancel();
            jiggles.clear();
        }));
        Panels.show(() -> {
            Context c = Panels.ctx();
            for (android.animation.Animator an : jiggles) an.cancel();
            jiggles.clear();
            boolean editing = editingFolder == index;
            JSONObject spec = TileDrawer.specs().optJSONObject(index);
            JSONArray apps = spec.optJSONArray("apps");
            List<View> cells = new ArrayList<>();
            List<String> keys = new ArrayList<>();
            if (apps != null) for (int i = 0; i < apps.length(); i++) {
                Apps.App a = Apps.get(apps.optString(i));
                if (a == null) continue;
                View inner = appCell(c, a, false, Actions.isAutostart(a.key));
                FrameLayout cell = new FrameLayout(c);
                cell.addView(inner, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
                final String key = a.key;
                if (editing) {
                    FrameLayout.LayoutParams ml = Ui.flpu(28, 28, Gravity.TOP | Gravity.END);
                    ml.setMargins(0, 0, Ui.u(6), 0);
                    cell.addView(minusBadge(c, () -> {
                        List<String> l = new ArrayList<>(keys);
                        l.remove(key);
                        saveFolder(index, l);
                        Panels.refresh();
                    }, a.label), ml);
                }
                cell.setOnClickListener(v -> {
                    if (editingFolder == index) { // editing: a tap finishes
                        editingFolder = -1;
                        Panels.refresh();
                        return;
                    }
                    Panels.close();
                    Apps.launch(c, a);
                });
                cells.add(cell);
                keys.add(a.key);
            }
            for (int i = 0; i < cells.size(); i++) {
                if (editing) jiggle(cells.get(i), i);
                Reorder.attach(cells.get(i), i, cells, (from, to) -> {
                    List<String> order = new ArrayList<>(keys);
                    order.add(to, order.remove(from));
                    saveFolder(index, order);
                    Panels.refresh();
                }, editing ? null : () -> { // a hold without moving: edit mode
                    editingFolder = index;
                    Panels.refresh();
                }, null, null, editing); // while editing: drag straight away, no second hold
            }
            if (editing) { // + adds an app
                FrameLayout plus = new FrameLayout(c);
                FrameLayout ring = new FrameLayout(c);
                ring.setBackground(Ui.outline(0, Ui.LINE, 1.5f, 12));
                ring.addView(Ui.icon(c, R.drawable.ic_plus, Ui.SUB), Ui.flpu(26, 26, Gravity.CENTER));
                plus.addView(ring, Ui.flpu(56, 56, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
                plus.setContentDescription("Add an app");
                plus.setOnClickListener(v -> appPicker("Add to " + spec.optString("name", "folder"), a -> {
                    List<String> l = new ArrayList<>(keys);
                    if (!l.contains(a.key)) l.add(a.key);
                    saveFolder(index, l);
                    Panels.back();
                }));
                Ui.press(plus);
                cells.add(plus);
            }
            LinearLayout b = Ui.col(c);
            if (cells.isEmpty()) b.addView(Ui.text(c, "This folder is empty. Hold here to add apps.", 14, Ui.SUB, false));
            else b.addView(new Pager(c, cells, 6, 2, 6), Ui.lp(Ui.MATCH, Ui.WRAP)); // pages of 12, slide sideways
            b.setMinimumHeight(Ui.u(200));
            b.setOnClickListener(v -> { // editing: a tap on an empty spot finishes
                if (editingFolder == index) {
                    editingFolder = -1;
                    Panels.refresh();
                }
            });
            b.setOnLongClickListener(v -> {
                editingFolder = index;
                Panels.refresh();
                return true;
            });
            // edit (apps and name) is an icon in the title bar; while editing it's a tick to finish
            View ed = Ui.icon(c, editing ? R.drawable.ic_check : R.drawable.ic_edit, Ui.ACCENT_SOFT);
            Ui.pad(ed, 4, 4, 4, 4);
            ed.setContentDescription(editing ? "Done" : "Edit folder");
            ed.setOnClickListener(v -> {
                if (editingFolder == index) {
                    editingFolder = -1;
                    Panels.refresh();
                } else editFolder(index);
            });
            Ui.press(ed);
            return Panels.panel(null, spec.optString("name", "Folder"), b, 640, 400, ed);
        });
    }

    static void editFolder(int index) {
        Panels.push(() -> {
            Context c = Panels.ctx();
            JSONObject spec = TileDrawer.specs().optJSONObject(index);
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
                    TileDrawer.setSpec(index, spec);
                    Panels.refresh();
                });
                cells.add(cell);
            }
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "Tap apps to add or remove them. Tap the name above to rename the folder.", 13, Ui.SUB, false));
            b.addView(grid(c, cells, 6, 6), top(10));
            // the folder's name is editable right in the title (saved as you type, if it's free)
            android.widget.EditText name = Widgets.field("Folder name", spec.optString("name"), false, t -> {
                String n = t.trim();
                if (n.isEmpty() || folderNames(index).contains(n.toLowerCase(java.util.Locale.ROOT))) return; // empty or taken: not saved
                JSONObject sp = TileDrawer.specs().optJSONObject(index);
                if (sp == null) return;
                Widgets.put(sp, "name", n);
                TileDrawer.setSpec(index, sp);
            });
            name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.uf(16));
            name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
            Ui.pad(name, 10, 4, 10, 4);
            return Panels.panel(name, null, b, 700, 440, null);
        });
    }

    static void renameFolder(int index) {
        Panels.push(() -> {
            Context c = Panels.ctx();
            JSONObject spec = TileDrawer.specs().optJSONObject(index);
            final String[] name = {spec.optString("name")};
            LinearLayout b = Ui.col(c);
            b.addView(Widgets.field("Folder name", name[0], false, t -> name[0] = t), Ui.lp(Ui.MATCH, Ui.WRAP));
            TextView err = Ui.text(c, "", 12, Ui.RED, false);
            b.addView(err, top(6));
            b.addView(Ui.button(c, "Save", true, () -> {
                String n = name[0].trim().isEmpty() ? "Folder" : name[0].trim();
                if (folderNames(index).contains(n.toLowerCase(java.util.Locale.ROOT))) {
                    err.setText("There's already a folder called " + n);
                    return;
                }
                Widgets.put(spec, "name", n);
                TileDrawer.setSpec(index, spec);
                Panels.close();
            }), top(16));
            return Panels.panel("Rename folder", b, 440, 230);
        });
    }

    // ------------------------------------------------------------------ tile menu & widget gallery

    static void tileMenu(int index) {
        Panels.Page p = () -> {
            Context c = Panels.ctx();
            JSONObject spec = TileDrawer.specs().optJSONObject(index);
            boolean isFolder = spec == null || "f".equals(spec.optString("t"));
            String id = spec == null ? "" : spec.optString("id");
            LinearLayout b = Ui.col(c);
            b.addView(Panels.item("Replace with another tile", null, () -> gallery(index)));
            if (isFolder) {
                b.addView(Panels.item("Edit apps", null, () -> editFolder(index)));
                b.addView(Panels.item("Rename folder", null, () -> renameFolder(index)));
            } else {
                if (Widgets.configurable(id)) b.addView(Panels.item("Tile settings", null, () -> Widgets.configure(index)));
                String kind = Alerts.kindOf(id);
                if (kind != null) b.addView(Panels.switchRow(0, 0, "Expand alerts into the tile area", Prefs.flag(Alerts.GLOBAL, false) ? "Off: the alert shows inside this tile" : "All expanding is off in Settings → Alerts",
                        Prefs.flag("expand_" + kind, true), v -> Prefs.put("expand_" + kind, v)));
                b.addView(Panels.item("Replace with a folder", null, () -> {
                    TileDrawer.setSpec(index, TileDrawer.folder("Apps", new JSONArray()));
                    Panels.close();
                    folder(index);
                }));
            }
            b.addView(Panels.item("Remove tile", null, () -> {
                TileDrawer.removeTile(index);
                Panels.back(); // back to the tile editor (or closed)
            }));
            b.addView(Ui.button(c, "Done editing", true, () -> {
                Panels.close();
                if (MainActivity.I != null) {
                    MainActivity.I.tiles.setEditing(false);
                    MainActivity.I.tiles.refresh();
                }
            }), top(14));
            String title = isFolder ? spec.optString("name", "Folder") + " folder" : Widgets.name(id);
            return Panels.panel(title, b, 440, 360);
        };
        // from the tile editor: open on top of it, so Back returns there
        if (Panels.open()) Panels.push(p);
        else Panels.show(p);
    }

    /**
     * Full-screen widget chooser: live previews in two rows that scroll sideways, with Folder as one
     * of the choices. index >= 0: replace that tile. index < 0: manage the home screen — widgets
     * already on it have a blue border; tap to add one, tap a blue one to take it off.
     */
    static Panels.Page galleryRef; // the open widget chooser (it closes on Done or when idle)

    private static int galleryX; // where the widget list was scrolled to (kept while it refreshes)

    static void gallery(int index) {
        galleryX = 0;
        Panels.Page page = galleryRef = () -> galleryPage(index);
        if (Panels.open()) Panels.pushFull(page);
        else Panels.showFull(page);
    }

    private static View galleryPage(int index) {
        Context c = Panels.ctx();
        boolean manage = index < 0;
        LinearLayout card = Ui.col(c);
        card.setBackground(Themes.card(Ui.CARD, 18));
        Ui.pad(card, 20, 14, 20, 14);
        card.setLayoutParams(Panels.centered(784, 464));

        LinearLayout head = Ui.row(c);
        LinearLayout titles = Ui.col(c);
        titles.addView(Ui.line(c, manage ? "Home screen tiles" : "Choose a tile", 20, Ui.TEXT, true));
        titles.addView(Ui.line(c, manage ? "Highlighted ones are on your home screen · tap a tile to add it or take it off" : "Tap one to put it in this spot · swipe for more", 12, Ui.SUB, false), top(2));
        head.addView(titles, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        if (manage) {
            head.addView(Ui.button(c, "Add all", false, () -> { // every widget not on the home screen yet
                JSONArray s = TileDrawer.specs();
                java.util.Set<String> have = new java.util.HashSet<>();
                for (int i = 0; i < s.length(); i++) {
                    JSONObject o = s.optJSONObject(i);
                    if (o != null) have.add(o.optString("id"));
                }
                for (String[] w : Widgets.CATALOG) if (Faith.widgetAllowed(w[0]) && Addons.unlocked(w[0]) && have.add(w[0])) s.put(TileDrawer.widget(w[0]));
                TileDrawer.save(s);
                if (MainActivity.I != null) MainActivity.I.tiles.refresh();
                Panels.refresh();
            }));
            head.addView(Ui.gap(c, 10, 1));
        }
        head.addView(Ui.button(c, "Add folder", false, () -> newFolder(index)));
        head.addView(Ui.gap(c, 10, 1));
        head.addView(Ui.button(c, "Done", true, () -> {
            Panels.back();
            if (MainActivity.I != null) MainActivity.I.tiles.refresh();
        }));
        card.addView(head);

        // what's on the home screen now
        JSONArray specs = TileDrawer.specs();
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
        for (String[] w : Widgets.CATALOG) if (Faith.widgetAllowed(w[0]) && Addons.unlocked(w[0])) choices.add(w);

        // group by category, each with a heading; chips at the top jump to a category
        String[][] cats = Widgets.CATS;
        LinearLayout blocks = Ui.row(c);
        blocks.setGravity(Gravity.TOP);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        LinearLayout chips = Ui.row(c);
        java.util.Set<String> placed = new java.util.HashSet<>();
        for (int ci = 0; ci <= cats.length; ci++) {
            List<String[]> group = new ArrayList<>();
            String cat = ci < cats.length ? cats[ci][0] : "More";
            for (String[] w : choices) {
                boolean in = ci < cats.length ? ("," + cats[ci][1] + ",").contains("," + w[0] + ",") : !placed.contains(w[0]);
                if (in && placed.add(w[0])) group.add(w);
            }
            if (group.isEmpty()) continue;
            LinearLayout block = Ui.col(c);
            TextView ht = Ui.line(c, cat.toUpperCase(java.util.Locale.ROOT), 11, Ui.SUB, true);
            ht.setLetterSpacing(0.08f);
            Ui.pad(ht, 6, 0, 0, 0);
            block.addView(ht);
            LinearLayout cols = Ui.row(c);
            cols.setGravity(Gravity.TOP);
            for (int k = 0; k < group.size(); k += 2) {
                LinearLayout col = Ui.col(c);
                for (int r = 0; r < 2; r++) {
                    if (k + r < group.size()) col.addView(cell(c, group.get(k + r), index, manage, on, finalCurrent), cellLp());
                    else col.addView(new View(c), cellLp()); // keeps a lone widget the same size as the rest
                }
                cols.addView(col, new LinearLayout.LayoutParams(Ui.WRAP, Ui.MATCH));
            }
            block.addView(cols, new LinearLayout.LayoutParams(Ui.WRAP, 0, 1));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.WRAP, Ui.MATCH);
            bl.rightMargin = Ui.u(14);
            blocks.addView(block, bl);
            TextView chip = Ui.seg(c, cat, false, () -> hs.smoothScrollTo(block.getLeft(), 0));
            LinearLayout.LayoutParams chl = Ui.lp(Ui.WRAP, Ui.WRAP);
            chl.rightMargin = Ui.u(8);
            chips.addView(chip, chl);
        }
        card.addView(chips, top(10));
        hs.setHorizontalScrollBarEnabled(false);
        hs.setFillViewport(true);
        hs.addView(blocks, new android.widget.FrameLayout.LayoutParams(Ui.WRAP, Ui.MATCH));
        // stay where you were after adding or removing one (the page is rebuilt each time)
        hs.setOnScrollChangeListener((v, x, y, ox, oy) -> galleryX = x);
        final int keep = galleryX;
        hs.post(() -> hs.scrollTo(keep, 0));
        LinearLayout.LayoutParams hl = Ui.weight(1, false);
        hl.topMargin = Ui.u(8);
        card.addView(hs, hl);
        return card;
    }

    private static LinearLayout.LayoutParams cellLp() {
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.u(116), 0, 1); // every cell the same size
        cl.setMargins(Ui.u(5), Ui.u(5), Ui.u(5), Ui.u(5));
        return cl;
    }

    /** One choice: live preview + name; blue border when it's on the home screen (or on this tile). */
    private static View cell(Context c, String[] w, int index, boolean manage, java.util.Map<String, Integer> on, String current) {
        boolean sel = manage ? on.containsKey(w[0]) : w[0].equals(current);
        LinearLayout cell = Ui.col(c);
        Ui.pad(cell, 5, 5, 5, 6);
        cell.setBackground(sel ? Ui.outline(0xFF15233A, Ui.ACCENT, 3, 16) : Themes.inner(Ui.CARD2, 16));
        cell.addView(Widgets.preview(c, TileDrawer.widget(w[0])), new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
        Integer cnt = on.get(w[0]);
        String label = Widgets.multiple(w[0]) && manage ? w[1] + (cnt != null ? " (" + cnt + ")" : "") + " · tap to add" : w[1];
        TextView nm = Ui.line(c, label, 12, sel ? Ui.ACCENT_SOFT : Ui.TEXT, true);
        nm.setGravity(Gravity.CENTER);
        cell.addView(nm, top(5));
        cell.setOnClickListener(v -> {
            if (manage) pick(index, w[0], on.containsKey(w[0]) && !Widgets.multiple(w[0])); // a contact adds another
            else if (on.containsKey(w[0]) && !w[0].equals(current) && !Widgets.multiple(w[0])) // each widget only once (contacts can repeat)
                android.widget.Toast.makeText(c, Widgets.name(w[0]) + " is already on your home screen", android.widget.Toast.LENGTH_SHORT).show();
            else pick(index, w[0], false);
        });
        Ui.press(cell);
        return cell;
    }

    /** Names of the folders on the home screen (lower case), except the one at `skip`. */
    private static java.util.Set<String> folderNames(int skip) {
        java.util.Set<String> n = new java.util.HashSet<>();
        JSONArray s = TileDrawer.specs();
        for (int i = 0; i < s.length(); i++) {
            JSONObject o = s.optJSONObject(i);
            if (i != skip && o != null && "f".equals(o.optString("t"))) n.add(o.optString("name").trim().toLowerCase(java.util.Locale.ROOT));
        }
        return n;
    }

    /** Ask for a name (must be new), then add the folder (or put it on the tile being replaced) and choose its apps. */
    private static void newFolder(int index) {
        Panels.pushFull(() -> {
            Context c = Panels.ctx();
            final String[] name = {""};
            LinearLayout b = Ui.col(c);
            TextView err = Ui.text(c, "", 12, Ui.RED, false);
            b.addView(Widgets.field("Folder name, e.g. Music", "", false, t -> {
                name[0] = t;
                err.setText("");
            }), Ui.lp(Ui.MATCH, Ui.WRAP));
            b.addView(err, top(6));
            b.addView(Ui.button(c, "Add folder", true, () -> {
                String n = name[0].trim();
                if (n.isEmpty()) {
                    err.setText("Give the folder a name");
                    return;
                }
                if (folderNames(index).contains(n.toLowerCase(java.util.Locale.ROOT))) {
                    err.setText("There's already a folder called " + n);
                    return;
                }
                JSONObject f = TileDrawer.folder(n, new JSONArray());
                int at;
                if (index >= 0) {
                    TileDrawer.setSpec(index, f);
                    at = index;
                } else {
                    JSONArray s = TileDrawer.specs();
                    s.put(f);
                    TileDrawer.save(s);
                    if (MainActivity.I != null) MainActivity.I.tiles.refresh();
                    at = s.length() - 1;
                }
                Panels.back(); // close this name box
                editFolder(at);
            }), top(10));
            LinearLayout card = (LinearLayout) Panels.panel("New folder", b, 440, 250);
            return card;
        });
    }

    /** What a new folder tile looks like. */
    private static View folderPreview(Context c) {
        LinearLayout l = Ui.col(c);
        l.setGravity(Gravity.CENTER);
        l.setBackground(Themes.card(Ui.CARD, 14));
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.oval(Ui.CARD3));
        ic.addView(Ui.icon(c, R.drawable.ic_folder, Ui.TEXT), Ui.flpu(26, 26, Gravity.CENTER));
        l.addView(ic, Ui.lpu(52, 52));
        l.addView(Ui.line(c, "Apps", 14, Ui.TEXT, true), top(8));
        return l;
    }

    /** Is this widget on the home screen (in the Tile Drawer)? */
    static boolean onHome(String id) {
        JSONArray s = TileDrawer.specs();
        for (int i = 0; i < s.length(); i++) {
            JSONObject o = s.optJSONObject(i);
            if (o != null && id.equals(o.optString("id")) && "w".equals(o.optString("t"))) return true;
        }
        return false;
    }

    /** Put a widget on the home screen or take it off (Settings → Faith). */
    static void homeTile(String id, boolean on) {
        if (on != onHome(id)) pick(-1, id, !on);
    }

    /** Act on a choice: replace the tile, or (manage) add it / take it off the home screen. */
    private static void pick(int index, String id, boolean takeOff) {
        boolean folder = "folder".equals(id);
        JSONObject spec = folder ? TileDrawer.folder("Apps", new JSONArray()) : TileDrawer.widget(id);
        int at;
        if (index >= 0) {
            TileDrawer.setSpec(index, spec);
            at = index;
        } else if (takeOff) {
            JSONArray s = TileDrawer.specs();
            for (int i = s.length() - 1; i >= 0; i--) {
                JSONObject o = s.optJSONObject(i);
                if (o != null && id.equals(o.optString("id")) && "w".equals(o.optString("t"))) {
                    TileDrawer.removeTile(i);
                    break;
                }
            }
            Panels.refresh();
            return;
        } else {
            JSONArray s = TileDrawer.specs();
            s.put(spec);
            TileDrawer.save(s);
            if (MainActivity.I != null) MainActivity.I.tiles.refresh();
            at = s.length() - 1;
        }
        if (folder) {
            editFolder(at);
            return;
        }
        Panels.refresh(); // stay open: the new choice shows in blue; Done (or leaving it idle) closes
    }

    // ------------------------------------------------------------------ auto start

    static void autostart() {
        Panels.Page page = () -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            b.addView(Panels.switchRow(R.drawable.ic_bolt, Ui.GREEN, "Auto start apps", "Opens these apps in order when the car starts",
                    Prefs.flag(Prefs.AUTOSTART_ON, true), on -> Prefs.put(Prefs.AUTOSTART_ON, on)));
            JSONArray list = Actions.autostart();
            for (int i = 0; i < list.length(); i++) {
                final int idx = i;
                JSONObject o = list.optJSONObject(i);
                Apps.App a = Apps.get(o.optString("key"));
                if (a == null) continue;
                LinearLayout r = Ui.row(c);
                Ui.pad(r, 0, 8, 0, 8);
                r.addView(Ui.text(c, (i + 1) + ".", 14, Ui.SUB, true), Ui.lpu(22, Ui.WRAP));
                ImageView iv = new ImageView(c);
                iv.setImageDrawable(a.icon);
                r.addView(iv, Ui.lpu(32, 32));
                TextView n = Ui.line(c, a.label, 14, Ui.TEXT, false);
                LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
                nl.leftMargin = Ui.u(10);
                r.addView(n, nl);
                int wait = o.optInt("wait", 3);
                r.addView(Ui.seg(c, "Wait " + wait + " s", false, () -> {
                    int[] steps = {0, 3, 5, 10, 20};
                    int next = steps[0];
                    for (int k = 0; k < steps.length; k++) if (steps[k] == wait) next = steps[(k + 1) % steps.length];
                    Widgets.put(o, "wait", next);
                    Prefs.put(Prefs.AUTOSTART, list);
                    Panels.refresh();
                }));
                r.addView(Ui.gap(c, 6, 1));
                boolean stay = "stay".equals(o.optString("mode"));
                r.addView(Ui.seg(c, stay ? "Stay open" : "Background", stay, () -> {
                    Widgets.put(o, "mode", stay ? "bg" : "stay");
                    Prefs.put(Prefs.AUTOSTART, list);
                    Panels.refresh();
                }));
                r.addView(Ui.gap(c, 6, 1));
                TextView up = Ui.text(c, "▲", 14, idx == 0 ? Ui.MUTED : Ui.TEXT2, false);
                Ui.pad(up, 8, 6, 8, 6);
                up.setOnClickListener(v -> {
                    if (idx == 0) return;
                    JSONArray out = new JSONArray();
                    for (int k = 0; k < list.length(); k++) {
                        if (k == idx - 1) out.put(list.optJSONObject(idx));
                        else if (k == idx) out.put(list.optJSONObject(idx - 1));
                        else out.put(list.optJSONObject(k));
                    }
                    Prefs.put(Prefs.AUTOSTART, out);
                    Panels.refresh();
                });
                r.addView(up);
                View rm = Ui.icon(c, R.drawable.ic_x, Ui.SUB);
                rm.setOnClickListener(v -> {
                    removeAutostart(a.key);
                    Panels.refresh();
                });
                r.addView(rm, Ui.lpu(20, 20));
                b.addView(r);
            }
            b.addView(Panels.item("Add app", null, () -> appPicker("Add to auto start", a -> {
                Actions.addAutostart(a.key);
                Panels.back();
                Panels.refresh();
            })));
            String show = Prefs.str(Prefs.AUTOSTART_SHOW, "home");
            b.addView(Panels.row("When they've started, show", Ui.seg(c, "Home screen", "home".equals(show), () -> {
                Prefs.put(Prefs.AUTOSTART_SHOW, "home");
                Panels.refresh();
            }), Ui.seg(c, "Last app", "last".equals(show), () -> {
                Prefs.put(Prefs.AUTOSTART_SHOW, "last");
                Panels.refresh();
            })));
            return Panels.panel("Auto start", b, 680, 440);
        };
        if (Panels.open()) Panels.push(page);
        else Panels.show(page);
    }
}
