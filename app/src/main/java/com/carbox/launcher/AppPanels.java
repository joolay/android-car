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
        LinearLayout l = U.col(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        U.pad(l, 2, 8, 2, 6);
        if (selected) l.setBackground(U.round(0x333D8BFD, 12));
        FrameLayout f = new FrameLayout(c);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(a.icon);
        f.addView(iv, U.flpu(46, 46, Gravity.CENTER));
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
        l.addView(f, U.lpu(52, 50));
        TextView t = U.line(c, a.label, 11, U.TEXT2, false);
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
        f.addView(ic, U.flpu(46, 46, Gravity.CENTER));
        l.addView(f, U.lpu(52, 50));
        TextView t = U.line(c, "Settings", 11, U.TEXT2, false);
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
        Sheets.show(AppPanels::allAppsPage);
    }

    private static View allAppsPage() {
        Context c = Sheets.ctx();
        LinearLayout card = U.col(c);
        card.setBackground(U.round(U.CARD, 22));
        U.pad(card, 24, 18, 24, 16);
        card.setLayoutParams(Sheets.centered(760, 452));

        LinearLayout head = U.row(c);
        head.addView(U.line(c, selecting ? selection.size() + " selected" : "All apps", 20, U.TEXT, true), U.lpu(170, U.WRAP));
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

        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(results);
        LinearLayout.LayoutParams sl = U.weight(1, false);
        sl.topMargin = U.u(12);
        card.addView(sv, sl);
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
        if (!selecting && (q.isEmpty() || "settings".startsWith(q) || "car launcher settings".contains(q))) {
            View s = settingsCell(c);
            s.setOnClickListener(v -> {
                Sheets.close();
                SettingsPanels.main();
            });
            cells.add(s);
        }
        for (Apps.App a : Apps.search(query)) {
            View cell = appCell(c, a, selecting && selection.contains(a.key), Actions.isAutostart(a.key));
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
        else results.addView(grid(c, cells, 7, 6));
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
            b.addView(Sheets.switchRow(R.drawable.ic_hide, U.SUB, "Hide app", "Unhide in Settings → Hidden apps", Apps.hidden().contains(a.key), on -> {
                Apps.setHidden(a.key, on);
                done.run();
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
            for (int i = 0; i < lines.length; i++) {
                final String which = (String) lines[i][0];
                Apps.App app = Actions.connectApp(which);
                View row = option(c, (Integer) lines[i][1], (Integer) lines[i][2], (Integer) lines[i][3], Actions.connectTitle(which), (String) lines[i][4],
                        app == null ? "Choose app" : app.label,
                        () -> Actions.carConnect(c, which), () -> bridgePicker(Actions.connectTitle(which) + " app", Actions.connectKey(which)));
                b.addView(row, U.lpu(U.MATCH, 72));
                ((LinearLayout.LayoutParams) row.getLayoutParams()).topMargin = U.u(i == 0 ? 0 : 10);
            }
            b.addView(U.text(c, "Tap a line to open it. Tap the app name on the right to change which app it opens.", 12, U.SUB, false), top(12));
            return Sheets.panel("Car Connect", b, 640, 430);
        });
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
            if (apps != null) for (int i = 0; i < apps.length(); i++) {
                Apps.App a = Apps.get(apps.optString(i));
                if (a == null) continue;
                View cell = appCell(c, a, false, Actions.isAutostart(a.key));
                cell.setOnClickListener(v -> {
                    Sheets.close();
                    Apps.launch(c, a);
                });
                cell.setOnLongClickListener(v -> {
                    appMenu(a, null);
                    return true;
                });
                cells.add(cell);
            }
            LinearLayout b = U.col(c);
            if (cells.isEmpty()) b.addView(U.text(c, "This folder is empty.", 14, U.SUB, false));
            else b.addView(grid(c, cells, 6, 6));
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
            b.addView(U.button(c, "Save", true, () -> {
                Widgets.put(spec, "name", name[0].isEmpty() ? "Folder" : name[0]);
                Tiles.setSpec(index, spec);
                Sheets.close();
            }), top(16));
            return Sheets.panel("Rename folder", b, 440, 230);
        });
    }

    // ------------------------------------------------------------------ tile menu & widget gallery

    static void tileMenu(int index) {
        Sheets.show(() -> {
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
                b.addView(Sheets.item("Replace with a folder", null, () -> {
                    Tiles.setSpec(index, Tiles.folder("Apps", new JSONArray()));
                    Sheets.close();
                    folder(index);
                }));
            }
            b.addView(U.button(c, "Done editing", true, () -> {
                Sheets.close();
                if (MainActivity.I != null) MainActivity.I.tiles.setEditing(false);
            }), top(14));
            String title = isFolder ? spec.optString("name", "Folder") + " folder" : Widgets.name(id);
            return Sheets.panel(title, b, 440, 360);
        });
    }

    static void gallery(int index) {
        Sheets.Page page = () -> {
            Context c = Sheets.ctx();
            List<View> cells = new ArrayList<>();
            for (String[] w : Widgets.CATALOG) {
                LinearLayout card = U.col(c);
                card.setBackground(U.round(U.CARD2, 14));
                U.pad(card, 14, 12, 14, 12);
                card.addView(U.line(c, w[1], 15, U.TEXT, true));
                TextView d = U.text(c, w[2], 12, U.SUB, false);
                d.setMaxLines(2);
                card.addView(d, top(4));
                card.setMinimumHeight(U.u(70));
                card.setOnClickListener(v -> {
                    Tiles.setSpec(index, Tiles.widget(w[0]));
                    Sheets.close();
                    if (Widgets.configurable(w[0])) Widgets.configure(index);
                });
                U.press(card);
                cells.add(card);
            }
            return Sheets.panel("Widgets", grid(c, cells, 3, 10), 700, 440);
        };
        if (Sheets.open()) Sheets.push(page);
        else Sheets.show(page);
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
