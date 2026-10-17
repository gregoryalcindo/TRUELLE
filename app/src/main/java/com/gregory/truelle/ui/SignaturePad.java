package com.gregory.truelle.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

/** Zone où le client signe au doigt. Exportée en image PNG sur fond blanc. */
public final class SignaturePad extends View {

    private final Path trace = new Path();
    private final Paint encre = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean vide = true;

    public SignaturePad(Context c) {
        super(c);
        encre.setColor(UiKit.ENCRE);
        encre.setStyle(Paint.Style.STROKE);
        encre.setStrokeCap(Paint.Cap.ROUND);
        encre.setStrokeJoin(Paint.Join.ROUND);
        encre.setStrokeWidth(UiKit.dp(c, 3.5f));
        setBackground(UiKit.rounded(Color.WHITE, UiKit.dp(c, 16), UiKit.dp(c, 2), UiKit.BORDURE));
        setContentDescription("Zone de signature");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(trace, encre);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // Empêche le défilement de l'écran pendant qu'on signe.
        getParent().requestDisallowInterceptTouchEvent(true);
        float x = e.getX();
        float y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                trace.moveTo(x, y);
                trace.lineTo(x + 0.1f, y + 0.1f);
                vide = false;
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getHistorySize(); i++) trace.lineTo(e.getHistoricalX(i), e.getHistoricalY(i));
                trace.lineTo(x, y);
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    public boolean estVide() {
        return vide;
    }

    public void effacer() {
        trace.reset();
        vide = true;
        invalidate();
    }

    public Bitmap image() {
        Bitmap bmp = Bitmap.createBitmap(Math.max(1, getWidth()), Math.max(1, getHeight()), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);
        c.drawPath(trace, encre);
        return bmp;
    }
}
