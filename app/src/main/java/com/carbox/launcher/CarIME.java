package com.carbox.launcher;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/** "Car keyboard": the launcher's big-key keyboard, available to every app as an Android keyboard. */
public class CarIME extends InputMethodService {
    private FrameLayout root;
    private boolean symbols, shift, listening;
    private String enterLabel = "↵";
    private int enterAction = EditorInfo.IME_ACTION_NONE;
    private SpeechRecognizer recognizer;

    @Override
    public void onCreate() {
        super.onCreate();
        U.init((Context) this);
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false; // never take over the whole screen in landscape
    }

    @Override
    public View onCreateInputView() {
        U.init((Context) this);
        root = new FrameLayout(this);
        root.setClipChildren(false);
        render();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        symbols = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME;
        shift = false;
        boolean multiLine = (info.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        if (multiLine || (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) action = EditorInfo.IME_ACTION_NONE;
        enterAction = action;
        switch (action) {
            case EditorInfo.IME_ACTION_GO:
                enterLabel = "Go";
                break;
            case EditorInfo.IME_ACTION_SEARCH:
                enterLabel = "Search";
                break;
            case EditorInfo.IME_ACTION_SEND:
                enterLabel = "Send";
                break;
            case EditorInfo.IME_ACTION_NEXT:
                enterLabel = "Next";
                break;
            case EditorInfo.IME_ACTION_DONE:
                enterLabel = "Done";
                break;
            default:
                enterLabel = "↵";
        }
        if (root != null) render();
    }

    @Override
    public void onFinishInputView(boolean finishing) {
        stopVoice();
        super.onFinishInputView(finishing);
    }

    private void render() {
        Context c = this;
        root.removeAllViews();
        Keyboard.bubbleHost = root;
        LinearLayout kb = U.col(c);
        kb.setBackgroundColor(U.CARD);
        U.pad(kb, 8, 8, 8, 6);

        // strip: voice, paste, hide
        LinearLayout strip = U.row(c);
        TextView voice = U.text(c, listening ? "  Listening… tap to stop" : "  Voice", 14, U.TEXT, false);
        android.graphics.drawable.Drawable micIcon = getDrawable(R.drawable.ic_mic).mutate();
        micIcon.setTint(U.TEXT);
        micIcon.setBounds(0, 0, U.u(20), U.u(20));
        voice.setCompoundDrawablesRelative(micIcon, null, null, null);
        voice.setGravity(Gravity.CENTER_VERTICAL);
        voice.setBackground(U.round(listening ? U.RED : 0xFF1F4E9A, 10));
        U.pad(voice, 14, 0, 16, 0);
        voice.setOnClickListener(v -> voice());
        LinearLayout.LayoutParams vl = U.lp(U.WRAP, U.u(38));
        vl.setMargins(U.u(4), 0, U.u(8), 0);
        strip.addView(voice, vl);
        TextView paste = U.button(c, "Paste", false, this::paste);
        fit(paste);
        strip.addView(paste, U.lp(U.WRAP, U.u(38)));
        TextView name = U.line(c, "CAR KEYBOARD", 11, U.MUTED, false);
        name.setGravity(Gravity.CENTER);
        name.setLetterSpacing(0.05f);
        strip.addView(name, new LinearLayout.LayoutParams(0, U.WRAP, 1));
        TextView hide = U.button(c, "⌄  Hide", false, () -> requestHideSelf(0));
        fit(hide);
        LinearLayout.LayoutParams hl = U.lp(U.WRAP, U.u(38));
        hl.rightMargin = U.u(4);
        strip.addView(hide, hl);
        kb.addView(strip, U.lpu(U.MATCH, 42));

        String[][] rows = symbols
                ? new String[][]{"1234567890".split(""), {"@", "#", "&", "_", "-", "/", ":", ";", "(", ")"}, {"=", ",", "+", "%", "'", "\"", "!", "?"}}
                : new String[][]{"qwertyuiop".split(""), "asdfghjkl".split(""), "zxcvbnm".split("")};
        for (int r = 0; r < 3; r++) {
            LinearLayout row = U.row(c);
            if (r == 1 && !symbols) U.pad(row, 36, 0, 36, 0);
            if (r == 2) row.addView(Keyboard.special(c, symbols ? "=" : null, symbols ? 0 : R.drawable.ic_shift, () -> {
                if (symbols) type("=");
                else {
                    shift = !shift;
                    render();
                }
            }, shift && !symbols), Keyboard.weightLp(1.5f));
            for (String k : rows[r]) {
                if (k.isEmpty() || (r == 2 && symbols && "=".equals(k))) continue;
                String shownKey = shift ? k.toUpperCase(Locale.ROOT) : k;
                row.addView(Keyboard.key(c, shownKey, () -> type(shownKey)), Keyboard.weightLp(1));
            }
            if (r == 2) {
                View bs = Keyboard.special(c, null, R.drawable.ic_backspace, this::backspace, false);
                Keyboard.repeat(bs, this::backspace);
                row.addView(bs, Keyboard.weightLp(1.5f));
            }
            kb.addView(row, U.lpu(U.MATCH, Keyboard.KEY_H));
        }
        LinearLayout last = U.row(c);
        last.addView(Keyboard.special(c, symbols ? "ABC" : "?123", 0, () -> {
            symbols = !symbols;
            render();
        }, false), Keyboard.weightLp(1.5f));
        last.addView(Keyboard.key(c, ",", () -> type(",")), Keyboard.weightLp(1));
        last.addView(Keyboard.key(c, "space", () -> type(" ")), Keyboard.weightLp(5));
        last.addView(Keyboard.key(c, ".", () -> type(".")), Keyboard.weightLp(1));
        View enter = Keyboard.special(c, enterLabel, 0, this::enter, false);
        enter.findViewWithTag("face").setBackground(U.round(U.ACCENT, 11));
        last.addView(enter, Keyboard.weightLp(2));
        kb.addView(last, U.lpu(U.MATCH, Keyboard.KEY_H));

        root.addView(kb, new FrameLayout.LayoutParams(U.MATCH, U.WRAP, Gravity.BOTTOM));
    }

    // ------------------------------------------------------------------ typing

    private void type(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(s, 1);
        if (shift) {
            shift = false;
            render();
        }
    }

    private void backspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) ic.commitText("", 1);
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
    }

    private void enter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (enterAction == EditorInfo.IME_ACTION_NONE || enterAction == EditorInfo.IME_ACTION_UNSPECIFIED) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        else ic.performEditorAction(enterAction);
    }

    private void paste() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData d = cm == null ? null : cm.getPrimaryClip();
        if (d != null && d.getItemCount() > 0) {
            CharSequence t = d.getItemAt(0).coerceToText(this);
            if (t != null) type(t.toString());
        }
    }

    // ------------------------------------------------------------------ voice

    private void voice() {
        if (listening) {
            stopVoice();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Allow the microphone in Car Launcher → Settings → Keyboard", Toast.LENGTH_LONG).show();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Voice typing isn't available on this box", Toast.LENGTH_SHORT).show();
            return;
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float v) { }
            @Override public void onBufferReceived(byte[] b) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onEvent(int t, Bundle p) { }

            @Override
            public void onError(int err) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.finishComposingText();
                stopVoice();
            }

            @Override
            public void onPartialResults(Bundle r) {
                ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                InputConnection ic = getCurrentInputConnection();
                if (l != null && !l.isEmpty() && ic != null) ic.setComposingText(l.get(0), 1);
            }

            @Override
            public void onResults(Bundle r) {
                ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    if (l != null && !l.isEmpty()) ic.commitText(l.get(0) + " ", 1);
                    else ic.finishComposingText();
                }
                stopVoice();
            }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        listening = true;
        render();
        try {
            recognizer.startListening(i);
        } catch (Exception e) {
            stopVoice();
        }
    }

    private void stopVoice() {
        boolean was = listening;
        listening = false;
        if (recognizer != null) {
            try {
                recognizer.destroy();
            } catch (Exception ignored) {
            }
            recognizer = null;
        }
        if (was && root != null) render();
    }

    // ------------------------------------------------------------------ setup helpers (used by Settings → Keyboard)

    static String id(Context c) {
        return new android.content.ComponentName(c, CarIME.class).flattenToShortString();
    }

    static boolean enabled(Context c) {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return false;
        for (android.view.inputmethod.InputMethodInfo i : imm.getEnabledInputMethodList())
            if (i.getPackageName().equals(c.getPackageName())) return true;
        return false;
    }

    static boolean isDefault(Context c) {
        String cur = android.provider.Settings.Secure.getString(c.getContentResolver(), android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
        return cur != null && cur.startsWith(c.getPackageName() + "/");
    }

    /** The usual buttons are 44 tall with roomy padding; in the 38-high strip that crops the label. */
    private static void fit(TextView b) {
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        U.pad(b, 16, 0, 16, 0);
        b.setBackground(U.round(U.CARD2, 10));
    }
}
