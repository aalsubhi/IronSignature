package com.example.ironsignature;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class FingerCircleView extends View {

    private Paint fillPaint;
    private Paint strokePaint;
    private float radiusPx;

    // Callback to Activity
    public interface OnPressureListener {
        void onPressure(float pressure);
    }

    private OnPressureListener listener;

    public void setOnPressureListener(OnPressureListener l) {
        this.listener = l;
    }

    public FingerCircleView(Context context, AttributeSet attrs) {
        super(context, attrs);

        float density = getResources().getDisplayMetrics().density;
        radiusPx = 65f * density; // your chosen size

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(Color.WHITE);
        fillPaint.setStyle(Paint.Style.FILL);

        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(Color.parseColor("#CCCCCC"));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(3 * density);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        canvas.drawCircle(cx, cy, radiusPx, fillPaint);
        canvas.drawCircle(cx, cy, radiusPx, strokePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        // Check if touch is INSIDE circle
        float dx = event.getX() - getWidth() / 2f;
        float dy = event.getY() - getHeight() / 2f;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist > radiusPx) {
            return false; // let buttons receive the touch
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE) {

            float effectivePressure = Math.max(0f, Math.min(1f,event.getPressure(0)));


            if (listener != null) {
                listener.onPressure(effectivePressure);
            }
            return true; // circle handled it
        }

        return false;
    }
}
