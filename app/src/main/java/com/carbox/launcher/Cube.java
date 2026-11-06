package com.carbox.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/** The rotating-cube turn between two faces (both children of the same parent). dir 1 = turns left (next), -1 = turns right. */
final class Cube {
    private Cube() {
    }

    static void turn(final View out, final View in, final int dir, final Runnable end) {
        ViewGroup p = (ViewGroup) out.getParent();
        final float w = p == null ? 0 : p.getWidth(), h = p == null ? 0 : p.getHeight();
        in.setVisibility(View.VISIBLE);
        if (w == 0 || Ui.ms(1) == 0) {
            out.setVisibility(View.GONE);
            if (end != null) end.run();
            return;
        }
        out.setCameraDistance(w * 6);
        in.setCameraDistance(w * 6);
        out.setPivotX(dir > 0 ? w : 0);
        out.setPivotY(h / 2);
        in.setPivotX(dir > 0 ? 0 : w);
        in.setPivotY(h / 2);
        ValueAnimator a = ValueAnimator.ofFloat(0, 1);
        a.setDuration(Ui.ms(420));
        a.setInterpolator(new DecelerateInterpolator(1.4f));
        a.addUpdateListener(x -> {
            float f = (float) x.getAnimatedValue();
            out.setTranslationX(-dir * w * f);
            out.setRotationY(-dir * 90 * f);
            in.setTranslationX(dir * w * (1 - f));
            in.setRotationY(dir * 90 * (1 - f));
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator an) {
                out.setVisibility(View.GONE);
                out.setTranslationX(0);
                out.setRotationY(0);
                in.setTranslationX(0);
                in.setRotationY(0);
                if (end != null) end.run();
            }
        });
        a.start();
    }

    /** A picture of a stage taken before its faces change; play() then turns the new face in over it like a cube. */
    static final class Snap {
        private final ViewGroup p;
        private final android.widget.ImageView shot;

        Snap(ViewGroup p) {
            this.p = p;
            android.widget.ImageView s = null;
            try {
                if (p.getWidth() > 0 && Ui.ms(1) > 0) {
                    android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(p.getWidth(), p.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
                    p.draw(new android.graphics.Canvas(b));
                    s = new android.widget.ImageView(p.getContext());
                    s.setImageBitmap(b);
                }
            } catch (Throwable ignored) {
            }
            shot = s;
        }

        void play(final int dir) {
            if (shot == null) return;
            final float w = p.getWidth(), h = p.getHeight();
            final java.util.List<View> in = new java.util.ArrayList<>();
            for (int i = 0; i < p.getChildCount(); i++) in.add(p.getChildAt(i));
            p.addView(shot, new android.widget.FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            shot.setCameraDistance(w * 6);
            shot.setPivotX(dir > 0 ? w : 0);
            shot.setPivotY(h / 2);
            for (View v : in) {
                v.setCameraDistance(w * 6);
                v.setPivotX((dir > 0 ? 0 : w) - v.getLeft());
                v.setPivotY(h / 2 - v.getTop());
            }
            ValueAnimator a = ValueAnimator.ofFloat(0, 1);
            a.setDuration(Ui.ms(420));
            a.setInterpolator(new DecelerateInterpolator(1.4f));
            a.addUpdateListener(x -> {
                float f = (float) x.getAnimatedValue();
                shot.setTranslationX(-dir * w * f);
                shot.setRotationY(-dir * 90 * f);
                for (View v : in) {
                    v.setTranslationX(dir * w * (1 - f));
                    v.setRotationY(dir * 90 * (1 - f));
                }
            });
            a.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator an) {
                    p.removeView(shot);
                    for (View v : in) {
                        v.setTranslationX(0);
                        v.setRotationY(0);
                    }
                }
            });
            a.start();
        }
    }

    static Snap snap(ViewGroup p) {
        return new Snap(p);
    }
}
