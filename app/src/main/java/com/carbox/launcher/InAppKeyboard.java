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
final class InAppKeyboard {
    private InAppKeyboard() {
    }

    private static FrameLayout shown;
    private static EditText target;
    private static StringBuilder text;
    private static boolean symbols, shift, secret, reveal;
    private static String label;
    private static TextView preview;

    /** Make an EditText open this keyboard instead of the system one. */
    static void attach(EditText e, String fieldLabel) {
        if (!Addons.unlocked("keyboard")) return; // the keyboard add-on isn't unlocked: the box's own keyboard types
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
        bubbleHost = null;
        root.addView(shown, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        render();
    }

    static boolean hide() {
        if (shown == null) return false;
        stopVoice();
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
        shown.addView(scrim, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        LinearLayout kb = Ui.col(c);
        kb.setClickable(true);
        kb.setBackground(kbBackground(true));
        Ui.pad(kb, 8, 10, 8, 6);
        Predictor.warm(c);

        // the field being edited, with Paste / Clear
        LinearLayout top = Ui.row(c);
        LinearLayout box = Ui.col(c);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(Ui.outline(Ui.BG, Ui.ACCENT, 2, 10));
        Ui.pad(box, 12, 0, 12, 0);
        TextView lab = Ui.line(c, label.toUpperCase(Locale.ROOT), 10, Ui.SUB, false);
        lab.setLetterSpacing(0.04f);
        box.addView(lab);
        preview = Ui.line(c, "", 18, Ui.TEXT, false);
        preview.setEllipsize(android.text.TextUtils.TruncateAt.START);
        box.addView(preview);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, Ui.u(48), 1);
        bl.setMargins(Ui.u(4), 0, 0, Ui.u(4));
        top.addView(box, bl);
        if (listening) {
            box.setBackground(Ui.outline(Ui.BG, Ui.RED, 2, 10));
            lab.setText(label.toUpperCase(Locale.ROOT) + " · LISTENING");
            top.addView(action(c, "Stop", InAppKeyboard::stopVoice));
        } else if (secret) top.addView(action(c, reveal ? "Hide" : "Show", () -> {
            reveal = !reveal;
            render();
        }));
        if (!listening) top.addView(action(c, "Paste", () -> {
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
        if (!listening) top.addView(action(c, "Clear", () -> {
            text.setLength(0);
            commit();
        }));
        kb.addView(top, Ui.lp(Ui.MATCH, Ui.WRAP));
        suggestRow = Ui.row(c);
        if (!secret) kb.addView(suggestRow, Ui.lpu(Ui.MATCH, 44)); // no guessing passwords
        suggest();

        String[][] rows = symbols
                ? new String[][]{"1234567890".split(""), {"@", "#", "&", "_", "-", "/", ":", ";", "(", ")"}, {"=", ",", "+", "%", "'", "\"", "!", "?"}}
                : new String[][]{"qwertyuiop".split(""), "asdfghjkl".split(""), "zxcvbnm".split("")};
        for (int r = 0; r < 3; r++) {
            LinearLayout row = Ui.row(c);
            if (r == 1 && !symbols) Ui.pad(row, 36, 0, 36, 0);
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
                View bs = special(c, null, R.drawable.ic_backspace, InAppKeyboard::backspace, false);
                repeat(bs);
                row.addView(bs, weightLp(1.5f));
            }
            LinearLayout.LayoutParams rl = Ui.lpu(Ui.MATCH, KEY_H);
            kb.addView(row, rl);
        }
        LinearLayout last = Ui.row(c);
        last.addView(special(c, symbols ? "ABC" : "?123", 0, () -> {
            symbols = !symbols;
            render();
        }, false), weightLp(1.5f));
        View mic = special(c, null, R.drawable.ic_mic, InAppKeyboard::voice, false);
        mic.findViewWithTag("face").setBackground(Ui.round(listening ? Ui.RED : 0xFF1F4E9A, 11));
        last.addView(mic, weightLp(1.5f));
        last.addView(key(c, "space", () -> type(" ")), weightLp(5));
        last.addView(key(c, ".", () -> type(".")), weightLp(1));
        View done = special(c, "Done", 0, InAppKeyboard::hide, false);
        done.findViewWithTag("face").setBackground(Ui.round(Ui.ACCENT, 11));
        last.addView(done, weightLp(2));
        LinearLayout.LayoutParams ll = Ui.lpu(Ui.MATCH, KEY_H);
        kb.addView(last, ll);

        shown.addView(kb, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM));
        updatePreview();
    }

    private static void type(String s) {
        if (!secret && !s.isEmpty() && !Character.isLetter(s.charAt(0)) && s.charAt(0) != '\'')
            Predictor.learn(Predictor.current(text), Predictor.previous(text)); // a word was finished
        text.append(s);
        suggest();
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
            suggest();
        }
    }

    /** Hold backspace to keep deleting. */
    private static void repeat(View v) {
        repeat(v, InAppKeyboard::backspace);
    }

    /** Hold to repeat (backspace). */
    static void repeat(View v, Runnable action) {
        final Runnable[] r = new Runnable[1];
        r[0] = () -> {
            action.run();
            Ui.ui.postDelayed(r[0], 70);
        };
        v.setOnTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                view.setAlpha(0.7f);
                action.run();
                Ui.ui.postDelayed(r[0], 450);
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                view.setAlpha(1f);
                Ui.ui.removeCallbacks(r[0]);
            }
            return true;
        });
    }

    /** Key colour for the theme: solid in Modern, see-through glass in Glass. */
    static int keyColor(boolean pressed) {
        if (Themes.on()) return pressed ? 0x66FFFFFF : 0x33FFFFFF;
        return pressed ? 0xFF55627A : 0xFF3A4456;
    }

    /** The keyboard's own background for the theme (the launcher's glass card inside it, frosted over other apps). */
    static android.graphics.drawable.Drawable kbBackground(boolean inLauncher) {
        if (!Themes.on()) return new android.graphics.drawable.ColorDrawable(Ui.CARD);
        if (inLauncher) return Themes.card(Ui.CARD, 0);
        return new android.graphics.drawable.ColorDrawable(0xC0141A24);
    }

    // ---- predictive text: up to 3 suggestions above the keys
    private static LinearLayout suggestRow;

    /** Fill a row with suggestion chips for the text so far; tapping one swaps it in for the word being typed. */
    static void fillSuggestions(LinearLayout row, CharSequence before, U2 pick) {
        row.removeAllViews();
        java.util.List<String> l = Predictor.suggest(before);
        for (int i = 0; i < 3; i++) {
            final String w = i < l.size() ? l.get(i) : null;
            TextView t = Ui.line(row.getContext(), w == null ? "" : w, 17, Ui.TEXT, i == 0 && w != null);
            t.setGravity(Gravity.CENTER);
            if (w != null) {
                t.setBackground(Ui.round(Themes.on() ? 0x1FFFFFFF : Ui.CARD2, 10));
                t.setOnClickListener(v -> pick.run(w));
                Ui.press(t);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.MATCH, 1);
            lp.setMargins(Ui.u(4), Ui.u(2), Ui.u(4), Ui.u(2));
            row.addView(t, lp);
        }
    }

    interface U2 {
        void run(String word);
    }

    private static void suggest() {
        if (suggestRow == null || secret) return;
        fillSuggestions(suggestRow, text, w -> {
            String cur = Predictor.current(text), prev = Predictor.previous(text);
            text.setLength(text.length() - cur.length());
            text.append(w).append(' ');
            Predictor.learn(w, prev);
            commit();
            suggest();
        });
    }

    static final float KEY_H = 66; // 58 du key + 8 du gap, and the gap is touchable too

    static LinearLayout.LayoutParams weightLp(float w) {
        return new LinearLayout.LayoutParams(0, Ui.MATCH, w);
    }

    /** A cell that is touchable edge to edge, with the visible key face inset by 4 du. */
    static FrameLayout cell(Context c, View face, int color) {
        FrameLayout f = new FrameLayout(c);
        face.setTag("face");
        face.setBackground(Ui.round(color, 11));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH);
        lp.setMargins(Ui.u(4), Ui.u(4), Ui.u(4), Ui.u(4));
        f.addView(face, lp);
        f.setClipChildren(false);
        return f;
    }

    static View key(Context c, String s, Runnable r) {
        TextView face = Ui.text(c, s, s.length() > 1 ? 17 : 25, Ui.TEXT, false);
        face.setGravity(Gravity.CENTER);
        FrameLayout f = cell(c, face, keyColor(false));
        f.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                face.setBackground(Ui.round(keyColor(true), 11));
                if (s.length() == 1) bubble(v, s);
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                face.setBackground(Ui.round(keyColor(false), 11));
                hideBubble();
            }
            return false;
        });
        f.setOnClickListener(v -> r.run());
        return f;
    }

    private static TextView bubbleView;
    /** Where key bubbles are drawn: the launcher overlay, or the system keyboard's own view. */
    static FrameLayout bubbleHost;

    /** Enlarged copy of the key above your finger, so you can see what you pressed. */
    private static void bubble(View keyView, String s) {
        hideBubble();
        FrameLayout host = bubbleHost != null && bubbleHost.isAttachedToWindow() ? bubbleHost : shown;
        if (host == null) return;
        Context c = host.getContext();
        bubbleView = Ui.text(c, s, 38, Ui.TEXT, false);
        bubbleView.setGravity(Gravity.CENTER);
        bubbleView.setBackground(Ui.round(0xFF6B7A95, 14));
        bubbleView.setElevation(Ui.uf(8));
        int[] k = new int[2], o = new int[2];
        keyView.getLocationInWindow(k);
        host.getLocationInWindow(o);
        int w = Ui.u(64), h = Ui.u(76);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.START);
        lp.leftMargin = Math.max(0, k[0] - o[0] + keyView.getWidth() / 2 - w / 2);
        lp.topMargin = Math.max(0, k[1] - o[1] - h + Ui.u(10));
        host.addView(bubbleView, lp);
    }

    private static void hideBubble() {
        if (bubbleView != null && bubbleView.getParent() != null) ((ViewGroup) bubbleView.getParent()).removeView(bubbleView);
        bubbleView = null;
    }

    static View special(Context c, String label, int icon, Runnable r, boolean on) {
        View face;
        if (icon != 0) {
            FrameLayout ff = new FrameLayout(c);
            ff.addView(Ui.icon(c, icon, Ui.TEXT), Ui.flpu(26, 26, Gravity.CENTER));
            face = ff;
        } else {
            TextView t = Ui.text(c, label, 17, Ui.TEXT, "Done".equals(label));
            t.setGravity(Gravity.CENTER);
            face = t;
        }
        FrameLayout f = cell(c, face, on ? Ui.ACCENT : Themes.on() ? 0x24FFFFFF : Ui.CARD3);
        f.setOnClickListener(v -> r.run());
        return f;
    }

    // ------------------------------------------------------------------ voice input

    private static boolean listening;
    private static android.speech.SpeechRecognizer recognizer;
    private static int voiceStart;
    static final int REQ_VOICE = 52, REQ_MIC = 53;

    private static void voice() {
        if (listening) {
            stopVoice();
            return;
        }
        MainActivity a = MainActivity.I;
        if (a == null) return;
        if (android.speech.SpeechRecognizer.isRecognitionAvailable(a)) {
            if (a.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                a.requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                return;
            }
            startRecognizer(a);
        } else {
            // no in-app recogniser: use the system voice typing screen
            android.content.Intent i = new android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, label);
            try {
                a.startActivityForResult(i, REQ_VOICE);
            } catch (Exception e) {
                android.widget.Toast.makeText(a, "Voice typing isn't available on this box", android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** Called by MainActivity when the microphone permission is granted. */
    static void micGranted() {
        if (shown != null && MainActivity.I != null) startRecognizer(MainActivity.I);
    }

    /** Called by MainActivity with the result of the system voice typing screen. */
    static void voiceResult(android.content.Intent data) {
        if (shown == null || data == null) return;
        java.util.ArrayList<String> r = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
        if (r != null && !r.isEmpty()) appendSpoken(r.get(0));
    }

    private static void appendSpoken(String t) {
        if (t == null || t.isEmpty()) return;
        if (text.length() > 0 && text.charAt(text.length() - 1) != ' ') text.append(' ');
        text.append(t);
        commit();
    }

    private static void startRecognizer(MainActivity a) {
        stopVoice();
        recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(a);
        voiceStart = text.length();
        final String before = text.toString();
        recognizer.setRecognitionListener(new android.speech.RecognitionListener() {
            @Override public void onReadyForSpeech(android.os.Bundle p) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float v) { }
            @Override public void onBufferReceived(byte[] b) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onEvent(int t, android.os.Bundle p) { }

            @Override
            public void onError(int err) {
                stopVoice();
                if (err != android.speech.SpeechRecognizer.ERROR_NO_MATCH && err != android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT && MainActivity.I != null)
                    android.widget.Toast.makeText(MainActivity.I, "Voice typing didn't work (needs internet or offline speech)", android.widget.Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPartialResults(android.os.Bundle r) {
                show(r, false);
            }

            @Override
            public void onResults(android.os.Bundle r) {
                show(r, true);
                stopVoice();
            }

            private void show(android.os.Bundle r, boolean fin) {
                java.util.ArrayList<String> l = r.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                if (l == null || l.isEmpty() || text == null) return;
                text.setLength(0);
                text.append(before);
                appendSpoken(l.get(0));
            }
        });
        android.content.Intent i = new android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        listening = true;
        render();
        try {
            recognizer.startListening(i);
        } catch (Exception e) {
            stopVoice();
        }
    }

    private static void stopVoice() {
        boolean was = listening;
        listening = false;
        if (recognizer != null) {
            try {
                recognizer.destroy();
            } catch (Exception ignored) {
            }
            recognizer = null;
        }
        if (was && shown != null) render();
    }

    private static TextView action(Context c, String s, Runnable r) {
        TextView t = Ui.textButton(c, s, false, r);
        t.setMinHeight(Ui.u(48));
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.uf(15));
        LinearLayout.LayoutParams lp = Ui.lp(Ui.WRAP, Ui.u(48));
        lp.setMargins(Ui.u(10), 0, Ui.u(4), Ui.u(4));
        t.setLayoutParams(lp);
        return t;
    }
}
