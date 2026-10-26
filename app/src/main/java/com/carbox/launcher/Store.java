package com.carbox.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/** Saves folders, dock slots and the chosen navigation app. */
final class Store {

    static final int DOCK_SIZE = 6;

    static final class Folder {
        String name;
        final ArrayList<String> apps = new ArrayList<>();

        Folder(String name) {
            this.name = name;
        }
    }

    private final SharedPreferences sp;
    final ArrayList<Folder> folders = new ArrayList<>();
    final ArrayList<String> dock = new ArrayList<>();
    String navPkg;

    Store(Context context) {
        sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    boolean isFirstRun() {
        return !sp.contains("folders");
    }

    void load() {
        folders.clear();
        dock.clear();
        try {
            JSONArray fa = new JSONArray(sp.getString("folders", "[]"));
            for (int i = 0; i < fa.length(); i++) {
                JSONObject o = fa.optJSONObject(i);
                if (o == null) continue;
                Folder f = new Folder(o.optString("name", "Folder"));
                JSONArray apps = o.optJSONArray("apps");
                if (apps != null) {
                    for (int j = 0; j < apps.length(); j++) {
                        String k = apps.optString(j, "");
                        if (!k.isEmpty()) f.apps.add(k);
                    }
                }
                folders.add(f);
            }
        } catch (JSONException ignored) {
        }
        try {
            JSONArray da = new JSONArray(sp.getString("dock", "[]"));
            for (int i = 0; i < da.length() && i < DOCK_SIZE; i++) {
                dock.add(da.optString(i, ""));
            }
        } catch (JSONException ignored) {
        }
        while (dock.size() < DOCK_SIZE) dock.add("");
        navPkg = sp.getString("nav", null);
    }

    void save() {
        JSONArray fa = new JSONArray();
        for (Folder f : folders) {
            JSONObject o = new JSONObject();
            try {
                o.put("name", f.name);
                o.put("apps", new JSONArray(f.apps));
            } catch (JSONException ignored) {
            }
            fa.put(o);
        }
        SharedPreferences.Editor e = sp.edit()
                .putString("folders", fa.toString())
                .putString("dock", new JSONArray(dock).toString());
        if (navPkg == null) e.remove("nav"); else e.putString("nav", navPkg);
        e.apply();
    }
}
