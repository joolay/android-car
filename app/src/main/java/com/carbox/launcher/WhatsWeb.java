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
    /**
     * One pane at a time, like on a phone: first the chats list across the whole tile area; tap a chat
     * and it opens across the whole area; the back button in the bar returns to the list.
     */
    private static final String ONE_PANE = "(function(){if(window.__cl)return;window.__cl=1;"
            + "var st=document.createElement('style');st.textContent="
            + "'body[data-cl=list] #app div:has(> #side){flex:1 1 100% !important;max-width:100% !important;width:100% !important}"
            + " body[data-cl=list] div:has(> #main){display:none !important}"
            + " body[data-cl=chat] #app div:has(> #side){display:none !important}"
            + " body[data-cl=chat] div:has(> #main){flex:1 1 100% !important;max-width:100% !important;width:100% !important}"
            + " ::-webkit-scrollbar{width:4px !important;height:0 !important} html,body{overflow:hidden !important}';"
            + "document.head.appendChild(st);document.body.dataset.cl='list';"
            + "document.addEventListener('click',function(e){if(e.target.closest&&e.target.closest('#pane-side'))"
            + "setTimeout(function(){document.body.dataset.cl='chat';},150);},true);})();";

    /** Back to the chats list. */
    static void chats() {
        if (web != null) web.evaluateJavascript("document.body&&(document.body.dataset.cl='list');", null);
    }
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
        // the whole page drawn smaller (not just the text), so more chats fit: the page lays itself out
        // for a wider screen and is shrunk to fit; − / + change it
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);
        web.setInitialScale(Prefs.num("wa_scale", 75));
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript(ONE_PANE, null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, android.webkit.WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.contains("whatsapp.com") || u.contains("whatsapp.net")) return false;
                NewsTicker.openLink(v.getContext(), u); // links in chats open in the browser
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

    /** WhatsApp is on screen (so it isn't closed for inactivity). */
    static boolean isOpen() {
        return web != null && web.isAttachedToWindow() && web.isShown();
    }

    /** Open it in the tile area (or full screen, the last way it was used). */
    static void open() {
        if (full) Panels.showFull(WhatsWeb::page);
        else Panels.show(WhatsWeb::page);
    }

    private static View page() {
        Context c = Panels.ctx();
        LinearLayout card = Ui.col(c);
        card.setBackground(Themes.card(Ui.CARD, 16));
        Ui.pad(card, 8, 6, 8, 8);
        if (full) card.setLayoutParams(new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        // a slim bar: name, text size, reload, full screen / back to the tiles, close
        LinearLayout head = Ui.row(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Ui.pad(head, 6, 0, 2, 0);
        head.addView(btn(c, R.drawable.ic_back, "Back to the chats", WhatsWeb::chats));
        head.addView(Ui.icon(c, R.drawable.ic_chat, Ui.GREEN), Ui.lpu(18, 18));
        TextView t = Ui.line(c, "WhatsApp", 14, Ui.TEXT, true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        tl.leftMargin = Ui.u(8);
        head.addView(t, tl);
        head.addView(btn(c, R.drawable.ic_minus, "Smaller", () -> zoom(-10)));
        head.addView(btn(c, R.drawable.ic_plus, "Bigger", () -> zoom(10)));
        head.addView(btn(c, R.drawable.ic_refresh, "Reload", () -> web(c).reload()));
        head.addView(btn(c, R.drawable.ic_maximize, full ? "Back to the tiles" : "Full screen", () -> {
            full = !full;
            Panels.close();
            open();
        }));
        head.addView(btn(c, R.drawable.ic_x, "Close", Panels::close));
        // in the Centre Stage the bar goes up into the News Ticker's place; full screen keeps it here
        if (full || MainActivity.I == null || !MainActivity.I.titleToTicker(head)) card.addView(head, Ui.lp(Ui.MATCH, Ui.u(34)));
        WebView w = web(c);
        if (w.getParent() != null) ((ViewGroup) w.getParent()).removeView(w);
        LinearLayout.LayoutParams wl = Ui.weight(1, false);
        wl.topMargin = Ui.u(4);
        FrameLayout clip = new FrameLayout(c);
        Maps.clip(clip, 12);
        clip.addView(w, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        card.addView(clip, wl);
        return card;
    }

    private static View btn(Context c, int icon, String desc, Runnable r) {
        View v = Ui.icon(c, icon, Ui.TEXT2);
        Ui.pad(v, 7, 7, 7, 7);
        v.setContentDescription(desc);
        v.setOnClickListener(x -> r.run());
        Ui.press(v);
        v.setLayoutParams(Ui.lpu(34, 34));
        return v;
    }

    private static void zoom(int step) {
        int z = Math.max(45, Math.min(130, Prefs.num("wa_scale", 75) + step));
        Prefs.put("wa_scale", z);
        if (web != null) { // the size only takes on a fresh layout: reload (the link to the phone stays)
            web.setInitialScale(z);
            web.reload();
        }
    }
}
