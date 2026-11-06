package com.carbox.launcher;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Works BT Phone for you through the accessibility service, because this box's BT Phone app
 * ignores numbers sent to it and posts no call notifications. It only ever looks at BT Phone's
 * own windows: it reads the caller from its incoming-call pop-up (so Car Launcher's call card
 * shows), presses its Answer / Decline / Hang up buttons when you tap ours, and types a number
 * into its keypad and presses call when you dial from Car Launcher.
 */
final class BtAuto {
    private static final String[] INCOMING = {"incoming", "来电", "來電", "ringing"};
    private static final String[] IN_CALL = {"hang up", "hangup", "挂断", "掛斷", "mute", "静音", "keypad"};
    private static final String[] ANSWER_ID = {"answer", "accept", "pickup", "pick_up", "jieting", "receive"};
    private static final String[] DECLINE_ID = {"reject", "decline", "refuse", "hangup", "hang_up", "end", "guaduan", "cancel"};

    static String pendingNumber;     // number to type in once BT Phone's keypad is up
    private static long pendingAt;
    private static boolean typing;
    private static boolean seenCall; // we're showing a call we read from BT Phone

    private BtAuto() {
    }

    static boolean active() {
        return BackButtonService.I != null;
    }

    // ------------------------------------------------------------------ reading BT Phone

    static void onEvent(AccessibilityService s, AccessibilityEvent e) {
        String bt = Actions.btDialler();
        if (bt == null || e.getPackageName() == null || !bt.equals(e.getPackageName().toString())) {
            // BT Phone's call window gone (another app came up): end a call we were showing
            if (seenCall && e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) Ui.ui.postDelayed(() -> recheck(s), 600);
            return;
        }
        if (pendingNumber != null && !typing && System.currentTimeMillis() - pendingAt < 15000) {
            typing = true;
            Ui.ui.postDelayed(() -> typeNumber(s), 700); // let the keypad finish drawing
        }
        Ui.ui.post(() -> recheck(s));
    }

    /** Look at BT Phone's windows and update the call shown by Car Launcher. */
    private static void recheck(AccessibilityService s) {
        AccessibilityNodeInfo root = btRoot(s);
        List<String> texts = new ArrayList<>();
        if (root != null) collect(root, texts);
        String all = String.join(" | ", texts).toLowerCase(Locale.ROOT);
        boolean ringing = has(all, INCOMING);
        boolean inCall = !ringing && (has(all, IN_CALL) || all.matches(".*\\b\\d{1,2}:\\d{2}\\b.*") && seenCall);
        if (!ringing && !inCall && !all.isEmpty()) linkFromScreen(all);
        if (ringing || inCall) {
            String name = callerName(texts);
            seenCall = true;
            CallState.fromScreen(s, name, ringing);
        } else if (seenCall) {
            seenCall = false;
            CallState.screenGone();
        }
    }

    // ------------------------------------------------------------------ is the phone linked to BT Phone?
    // The box's BT Phone has its own Bluetooth, apart from Android's, so the only way to know is its screen.
    static int link = -1;      // -1 not known yet, 0 not linked, 1 linked (as last seen on BT Phone's screen)
    static long linkAt;
    private static String lastDump = "";

    private static void linkFromScreen(String all) {
        if (!all.equals(lastDump)) { // every new BT Phone screen goes in the log (read it to learn the exact wording)
            lastDump = all;
            Trace.event("BT Phone screen: " + (all.length() > 600 ? all.substring(0, 600) : all));
        }
        if (all.contains("not connected") || all.contains("disconnected") || all.contains("no device") || all.contains("not paired")
                || all.contains("connect phone") || all.contains("waiting")) {
            link = 0;
            linkAt = System.currentTimeMillis();
        } else if (all.contains("connected")) {
            link = 1;
            linkAt = System.currentTimeMillis();
        }
    }

    /** True / false when BT Phone's screen was seen to say so in the last 6 hours, else null (not known). */
    static Boolean linked() {
        return link < 0 || System.currentTimeMillis() - linkAt > 6L * 3600 * 1000 ? null : link == 1;
    }

    private static String callerName(List<String> texts) {
        String best = null;
        for (String t : texts) {
            String l = t.toLowerCase(Locale.ROOT).trim();
            if (l.isEmpty() || has(l, INCOMING) || has(l, IN_CALL) || l.matches("[\\d:]+") || l.length() > 40) continue;
            if (best == null || t.length() > best.length()) best = t.trim();
        }
        return best == null ? "Unknown" : best;
    }

    // ------------------------------------------------------------------ pressing BT Phone's buttons

    /** "answer", "decline" or "hangup". Returns false if no button could be found. */
    static boolean press(String what) {
        boolean ok = press0(what);
        Trace.event("Call: pressed " + what + " in BT Phone: " + (ok ? "done" : "couldn't (no button found / Call helper off)"));
        return ok;
    }

    private static boolean press0(String what) {
        AccessibilityService s = BackButtonService.I;
        if (s == null) return false;
        AccessibilityNodeInfo root = btRoot(s);
        if (root == null) return false;
        List<AccessibilityNodeInfo> btns = new ArrayList<>();
        clickables(root, btns);
        String[] ids = "answer".equals(what) ? ANSWER_ID : DECLINE_ID;
        for (AccessibilityNodeInfo n : btns) {
            String id = n.getViewIdResourceName() == null ? "" : n.getViewIdResourceName().toLowerCase(Locale.ROOT);
            String d = (n.getContentDescription() == null ? "" : n.getContentDescription().toString()) + " " + (n.getText() == null ? "" : n.getText());
            if (has(id, ids) || has(d.toLowerCase(Locale.ROOT), ids)) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        // no names on the buttons: on the incoming pop-up, green Answer is on the left and red Decline on the right
        if (btns.size() >= 2) {
            btns.sort((a, b) -> Integer.compare(left(a), left(b)));
            AccessibilityNodeInfo n = "answer".equals(what) ? btns.get(0) : btns.get(btns.size() - 1);
            return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        if (btns.size() == 1 && !"answer".equals(what)) return btns.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        return false;
    }

    // ------------------------------------------------------------------ dialling

    /** Open BT Phone and type the number in, then press its call button. */
    static void dial(android.app.Activity a, String number, String btPkg) {
        Trace.event("Call: dialling a number through " + btPkg);
        pendingNumber = number.replaceAll("[^0-9+*#]", "");
        pendingAt = System.currentTimeMillis();
        typing = false;
        Apps.launchPkg(a, btPkg);
    }

    private static void typeNumber(AccessibilityService s) {
        String num = pendingNumber;
        pendingNumber = null;
        if (num == null) {
            typing = false;
            return;
        }
        AccessibilityNodeInfo root = btRoot(s);
        if (root == null) {
            typing = false;
            log("dial: BT Phone window not found");
            return;
        }
        List<Runnable> steps = new ArrayList<>();
        int found = 0;
        for (char ch : num.toCharArray()) {
            String key = ch == '+' ? "0" : String.valueOf(ch);
            AccessibilityNodeInfo k = findKey(root, key);
            if (k == null) continue;
            found++;
            final boolean plus = ch == '+';
            steps.add(() -> k.performAction(plus ? AccessibilityNodeInfo.ACTION_LONG_CLICK : AccessibilityNodeInfo.ACTION_CLICK));
        }
        if (found < num.replace("+", "").length()) {
            typing = false;
            log("dial: keypad keys not found (" + found + " of " + num.length() + ")");
            return;
        }
        AccessibilityNodeInfo call = callButton(root);
        for (int i = 0; i < steps.size(); i++) Ui.ui.postDelayed(steps.get(i), 150L * i);
        Ui.ui.postDelayed(() -> {
            typing = false;
            if (call != null) call.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            else log("dial: number typed, call button not found");
        }, 150L * steps.size() + 300);
    }

    /** The key for a digit: a node whose text (or first line) is that digit, or its clickable parent. */
    private static AccessibilityNodeInfo findKey(AccessibilityNodeInfo root, String digit) {
        List<AccessibilityNodeInfo> hits = root.findAccessibilityNodeInfosByText(digit);
        for (AccessibilityNodeInfo n : hits) {
            CharSequence t = n.getText() != null ? n.getText() : n.getContentDescription();
            if (t == null) continue;
            String first = t.toString().trim().split("\\s+")[0];
            if (!first.equals(digit)) continue;
            AccessibilityNodeInfo c = n;
            while (c != null && !c.isClickable()) c = c.getParent();
            if (c != null) return c;
        }
        return null;
    }

    /** The green call button: by name, else the biggest clickable without text at the bottom right. */
    private static AccessibilityNodeInfo callButton(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> btns = new ArrayList<>();
        clickables(root, btns);
        AccessibilityNodeInfo best = null;
        int bestScore = -1;
        for (AccessibilityNodeInfo n : btns) {
            String id = n.getViewIdResourceName() == null ? "" : n.getViewIdResourceName().toLowerCase(Locale.ROOT);
            String d = n.getContentDescription() == null ? "" : n.getContentDescription().toString().toLowerCase(Locale.ROOT);
            if ((id.contains("call") || id.contains("dial") || d.contains("call") || d.contains("dial")) && !id.contains("log") && !id.contains("record")) return n;
            if (n.getText() != null && n.getText().length() > 0) continue;
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            int score = r.width() * r.height() + r.right + r.bottom;
            if (score > bestScore) {
                bestScore = score;
                best = n;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ helpers

    private static AccessibilityNodeInfo btRoot(AccessibilityService s) {
        String bt = Actions.btDialler();
        if (bt == null) return null;
        try {
            for (AccessibilityWindowInfo w : s.getWindows()) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null && r.getPackageName() != null && bt.equals(r.getPackageName().toString())) return r;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void collect(AccessibilityNodeInfo n, List<String> out) {
        if (n == null || out.size() > 80) return;
        if (n.getText() != null && n.getText().length() > 0) out.add(n.getText().toString());
        else if (n.getContentDescription() != null && n.getContentDescription().length() > 0) out.add(n.getContentDescription().toString());
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), out);
    }

    private static void clickables(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out) {
        if (n == null || out.size() > 60) return;
        if (n.isClickable() && n.isVisibleToUser()) out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) clickables(n.getChild(i), out);
    }

    private static int left(AccessibilityNodeInfo n) {
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        return r.left;
    }

    private static boolean has(String s, String[] words) {
        for (String w : words) if (s.contains(w)) return true;
        return false;
    }

    private static void log(String msg) {
        java.util.List<String> l = new ArrayList<>(Prefs.list("call_log"));
        l.add(0, new java.text.SimpleDateFormat("HH:mm", Locale.US).format(new java.util.Date()) + "  " + msg);
        while (l.size() > 6) l.remove(l.size() - 1);
        Prefs.putList("call_log", l);
    }
}
