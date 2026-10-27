package com.carbox.launcher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * The launcher's own large-key keyboard, so typing works on the car screen without the
 * box's (often tiny or missing) system keyboard. Tap a text box to open it; Done closes it.
 */
final class Keyboard {
    private Keyboard() {
    }

    private static FrameLayout shown;
    private static EditText target;
    private static StringBuilder text;
    private static boolean symbols, shift, secret, reveal;
    private static String label;
    private static TextView preview;

    /** Make an EditText open this keyboard instead of the system one. */
    static void attach(EditText e, String fieldLabel) {
        e.setShowSoftInputOnFocus(false);
        e.setFocusable(false);
        e.setCursorVisible(false);
        e.setOnClickListener(v -> show(e, fieldLabel));
    }

    static boolean open() {
        return shown != null;
    }

    static void show(EditText e, String fieldLabel) {
        if (MainActivity.I == null) return;
        hide();
        target = e;
        label = fieldLabel == null ? "" : fieldLabel;
        text = new StringBuilder(e.getText().toString());
        int it = e.getInputType();
        secret = (it & InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD;
        reveal = false;
        symbols = false;
        shift = false;
        ViewGroup root = MainActivity.I.findViewById(android.R.id.content);
        shown = new FrameLayout(MainActivity.I);
        root.addView(shown, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        render();
    }

    static boolean hide() {
        if (shown == null) return false;
        if (shown.getParent() != null) ((ViewGroup) shown.getParent()).removeView(shown);
        shown = null;
        target = null;
        return true;
    }

    private static void commit() {
        if (target != null) target.setText(text.toString()); // fires the field's change listener
        updatePreview();
    }

    private static void updatePreview() {
        if (preview == null) return;
        String s = text.toString();
        if (secret && !reveal && s.length() > 0) {
            StringBuilder m = new StringBuilder();
            for (int i = 0; i < s.length() - 1; i++) m.append('•');
            s = m.append(s.charAt(s.length() - 1)).toString();
        }
        preview.setText(s + "▏");
    }

    private static void render() {
        Context c = shown.getContext();
        shown.removeAllViews();
        View scrim = new View(c);
        scrim.setBackgroundColor(0x8C000000);
        scrim.setOnClickListener(v -> hide());
        shown.addView(scrim, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));

        LinearLayout kb = U.col(c);
        kb.setClickable(true);
        kb.setBackgroundColor(U.CARD);
        U.pad(kb, 12, 10, 12, 10);

        // the field being edited, with Paste / Clear
        LinearLayout top = U.row(c);
        LinearLayout box = U.col(c);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(U.outline(U.BG, U.ACCENT, 2, 10));
        U.pad(box, 12, 0, 12, 0);
        TextView lab = U.line(c, label.toUpperCase(Locale.ROOT), 10, U.SUB, false);
        lab.setLetterSpacing(0.04f);
        box.addView(lab);
        preview = U.line(c, "", 16, U.TEXT, false);
        preview.setEllipsize(android.text.TextUtils.TruncateAt.START);
        box.addView(preview);
        top.addView(box, new LinearLayout.LayoutParams(0, U.u(44), 1));
        if (secret) top.addView(action(c, reveal ? "Hide" : "Show", () -> {
            reveal = !reveal;
            render();
        }));
        top.addView(action(c, "Paste", () -> {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData d = cm == null ? null : cm.getPrimaryClip();
            if (d != null && d.getItemCount() > 0) {
                CharSequence t = d.getItemAt(0).coerceToText(c);
                if (t != null) {
                    text.append(t.toString().trim());
                    commit();
                }
            }
        }));
        top.addView(action(c, "Clear", () -> {
            text.setLength(0);
            commit();
        }));
        kb.addView(top, U.lp(U.MATCH, U.WRAP));

        String[][] rows = symbols
                ? new String[][]{"1234567890".split(""), {"@", "#", "&", "_", "-", "/", ":", ";", "(", ")"}, {"=", "+", "%", "'", "\"", "!", "?", "*"}}
                : new String[][]{"qwertyuiop".split(""), "asdfghjkl".split(""), "zxcvbnm".split("")};
        for (int r = 0; r < 3; r++) {
            LinearLayout row = U.row(c);
            if (r == 1 && !symbols) U.pad(row, 30, 0, 30, 0);
            if (r == 2) row.addView(special(c, symbols ? "=" : null, symbols ? 0 : R.drawable.ic_shift, () -> {
                if (symbols) type("=");
                else {
                    shift = !shift;
                    render();
                }
            }, shift && !symbols), weightLp(1.5f));
            for (String k : rows[r]) {
                if (k.isEmpty() || (r == 2 && symbols && "=".equals(k))) continue;
                String shownKey = shift ? k.toUpperCase(Locale.ROOT) : k;
                row.addView(key(c, shownKey, () -> type(shownKey)), weightLp(1));
            }
            if (r == 2) {
                View bs = special(c, null, R.drawable.ic_backspace, Keyboard::backspace, false);
                repeat(bs);
                row.addView(bs, weightLp(1.5f));
            }
            LinearLayout.LayoutParams rl = U.lpu(U.MATCH, 46);
            rl.topMargin = U.u(6);
            kb.addView(row, rl);
        }
        LinearLayout last = U.row(c);
        last.addView(special(c, symbols ? "ABC" : "?123", 0, () -> {
            symbols = !symbols;
            render();
        }, false), weightLp(1.5f));
        last.addView(key(c, ",", () -> type(",")), weightLp(1));
        last.addView(key(c, "space", () -> type(" ")), weightLp(5));
        last.addView(key(c, ".", () -> type(".")), weightLp(1));
        TextView done = U.text(c, "Done", 15, U.TEXT, true);
        done.setGravity(Gravity.CENTER);
        done.setBackground(U.round(U.ACCENT, 9));
        done.setOnClickListener(v -> hide());
        last.addView(done, weightLp(2));
        LinearLayout.LayoutParams ll = U.lpu(U.MATCH, 46);
        ll.topMargin = U.u(6);
        kb.addView(last, ll);

        shown.addView(kb, new FrameLayout.LayoutParams(U.MATCH, U.WRAP, Gravity.BOTTOM));
        updatePreview();
    }

    private static void type(String s) {
        text.append(s);
        if (shift) {
            shift = false;
            commit();
            render();
            return;
        }
        commit();
    }

    private static void backspace() {
        if (text.length() > 0) {
            text.setLength(text.length() - 1);
            commit();
        }
    }

    /** Hold backspace to keep deleting. */
    private static void repeat(View v) {
        final Runnable[] r = new Runnable[1];
        r[0] = () -> {
            backspace();
            U.ui.postDelayed(r[0], 70);
        };
        v.setOnTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                view.setAlpha(0.7f);
                backspace();
                U.ui.postDelayed(r[0], 450);
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                view.setAlpha(1f);
                U.ui.removeCallbacks(r[0]);
            }
            return true;
        });
    }

    private static LinearLayout.LayoutParams weightLp(float w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, U.MATCH, w);
        lp.setMargins(U.u(3), 0, U.u(3), 0);
        return lp;
    }

    private static TextView key(Context c, String s, Runnable r) {
        TextView t = U.text(c, s, s.length() > 1 ? 15 : 20, U.TEXT, false);
        t.setGravity(Gravity.CENTER);
        t.setBackground(U.round(0xFF3A4456, 9));
        t.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) v.setBackground(U.round(0xFF55627A, 9));
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)
                v.setBackground(U.round(0xFF3A4456, 9));
            return false;
        });
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private static View special(Context c, String label, int icon, Runnable r, boolean on) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(U.round(on ? U.ACCENT : U.CARD3, 9));
        if (icon != 0) {
            ImageView iv = U.icon(c, icon, U.TEXT);
            f.addView(iv, U.flpu(22, 22, Gravity.CENTER));
        } else {
            TextView t = U.text(c, label, 15, U.TEXT, false);
            f.addView(t, U.flp(U.WRAP, U.WRAP, Gravity.CENTER));
        }
        f.setOnClickListener(v -> r.run());
        return f;
    }

    private static TextView action(Context c, String s, Runnable r) {
        TextView t = U.button(c, s, false, r);
        t.setMinHeight(U.u(44));
        LinearLayout.LayoutParams lp = U.lp(U.WRAP, U.u(44));
        lp.leftMargin = U.u(10);
        t.setLayoutParams(lp);
        return t;
    }
}
