package com.carbox.launcher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * WhatsApp Web inside Car Launcher: one page kept alive the whole time (so the link to your phone and
 * your chats stay), opened in the tile area or full screen. Link it once by scanning the QR code with
 * WhatsApp on your phone (Linked devices). No driving limits for now, while it's being tried out.
 */
final class WhatsWeb {
    static final String URL = "https://web.whatsapp.com/";
    // WhatsApp Web only works in desktop browsers, so it's told this is Chrome on a computer
    private static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static WebView web;
    private static boolean full;

    private WhatsWeb() {
    }

    @SuppressLint("SetJavaScriptEnabled")
    static WebView web(Context c) {
        if (web != null) return web;
        Context a = MainActivity.I != null ? MainActivity.I : c;
        web = new WebView(a);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUserAgentString(DESKTOP_UA);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(Store.num("wa_zoom", 100));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, android.webkit.WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.contains("whatsapp.com") || u.contains("whatsapp.net")) return false;
                Ticker.openLink(v.getContext(), u); // links in chats open in the browser
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest r) { // voice notes and calls want the microphone
                try {
                    r.grant(r.getResources());
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onReceivedTitle(WebView v, String title) {
                Trace.event("WhatsApp Web: " + title);
            }
        });
        web.loadUrl(URL);
        return web;
    }

    /** Unread chats, from the page title ("(3) WhatsApp"); 0 when none or not loaded. */
    static int unread() {
        if (web == null || web.getTitle() == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\((\\d+)\\)").matcher(web.getTitle());
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** Open it in the tile area (or full screen, the last way it was used). */
    static void open() {
        if (full) Sheets.showFull(WhatsWeb::page);
        else Sheets.show(WhatsWeb::page);
    }

    private static View page() {
        Context c = Sheets.ctx();
        LinearLayout card = U.col(c);
        card.setBackground(U.round(U.CARD, 16));
        U.pad(card, 8, 6, 8, 8);
        if (full) card.setLayoutParams(new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        // a slim bar: name, text size, reload, full screen / back to the tiles, close
        LinearLayout head = U.row(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        U.pad(head, 6, 0, 2, 0);
        head.addView(U.icon(c, R.drawable.ic_chat, U.GREEN), U.lpu(18, 18));
        TextView t = U.line(c, "WhatsApp", 14, U.TEXT, true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
        tl.leftMargin = U.u(8);
        head.addView(t, tl);
        head.addView(btn(c, R.drawable.ic_minus, "Smaller text", () -> zoom(-10)));
        head.addView(btn(c, R.drawable.ic_plus, "Bigger text", () -> zoom(10)));
        head.addView(btn(c, R.drawable.ic_refresh, "Reload", () -> web(c).reload()));
        head.addView(btn(c, R.drawable.ic_maximize, full ? "Back to the tiles" : "Full screen", () -> {
            full = !full;
            Sheets.close();
            open();
        }));
        head.addView(btn(c, R.drawable.ic_x, "Close", Sheets::close));
        card.addView(head, U.lp(U.MATCH, U.u(34)));
        WebView w = web(c);
        if (w.getParent() != null) ((ViewGroup) w.getParent()).removeView(w);
        LinearLayout.LayoutParams wl = U.weight(1, false);
        wl.topMargin = U.u(4);
        FrameLayout clip = new FrameLayout(c);
        MapPanel.clip(clip, 12);
        clip.addView(w, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        card.addView(clip, wl);
        return card;
    }

    private static View btn(Context c, int icon, String desc, Runnable r) {
        View v = U.icon(c, icon, U.TEXT2);
        U.pad(v, 7, 7, 7, 7);
        v.setContentDescription(desc);
        v.setOnClickListener(x -> r.run());
        U.press(v);
        v.setLayoutParams(U.lpu(34, 34));
        return v;
    }

    private static void zoom(int step) {
        int z = Math.max(60, Math.min(200, Store.num("wa_zoom", 100) + step));
        Store.put("wa_zoom", z);
        if (web != null) web.getSettings().setTextZoom(z);
    }
}
