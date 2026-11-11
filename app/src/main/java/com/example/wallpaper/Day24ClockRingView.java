package com.example.wallpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Calendar;

public class Day24ClockRingView extends View {

    private Paint trackPaint;
    private Paint progressPaint;
    private Paint dotPaint;
    private Paint dotGlowPaint;
    private Paint tickPaint;

    private RectF ringBounds;
    private Matrix gradientMatrix;

    private float currentProgress = 0f; // 0.0 to 1.0
    private float targetProgress = 0f;
    private float pulsePhase = 0f;

    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private boolean isAnimating = false;

    private final Runnable animationRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimeProgress();
            pulsePhase += 0.05f;
            if (pulsePhase > Math.PI * 2) {
                pulsePhase -= (float) (Math.PI * 2);
            }
            currentProgress += (targetProgress - currentProgress) * 0.1f;
            invalidate();

            if (isAnimating) {
                updateHandler.postDelayed(this, 30);
            }
        }
    };

    public Day24ClockRingView(Context context) {
        super(context);
        init();
    }

    public Day24ClockRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Day24ClockRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ringBounds = new RectF();
        gradientMatrix = new Matrix();

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(10f);
        trackPaint.setColor(Color.parseColor("#1F1F24"));

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(12f);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.WHITE);

        dotGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotGlowPaint.setStyle(Paint.Style.FILL);
        dotGlowPaint.setColor(Color.parseColor("#40FFFFFF"));

        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setStyle(Paint.Style.FILL);
        tickPaint.setColor(Color.parseColor("#444444"));

        updateTimeProgress();
        currentProgress = targetProgress;
    }

    private void updateTimeProgress() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
        int millis = calendar.get(Calendar.MILLISECOND);

        float totalSecondsInDay = 86400f;
        float currentSeconds = hour * 3600f + minute * 60f + second + millis / 1000f;
        targetProgress = currentSeconds / totalSecondsInDay;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float padding = 20f;
        ringBounds.set(padding, padding, w - padding, h - padding);

        int[] colors = new int[]{
                Color.parseColor("#4A00E0"), // 00:00 Deep Violet
                Color.parseColor("#8E2DE2"), // 04:00 Early Dawn
                Color.parseColor("#FF6A00"), // 08:00 Morning Sun
                Color.parseColor("#EE0979"), // 12:00 High Noon Pink/Magenta
                Color.parseColor("#FF6A00"), // 16:00 Sunset Gold
                Color.parseColor("#7F00FF"), // 20:00 Twilight Purple
                Color.parseColor("#4A00E0")  // 24:00 Midnight
        };

        SweepGradient sweepGradient = new SweepGradient(w / 2f, h / 2f, colors, null);
        gradientMatrix.setRotate(-90, w / 2f, h / 2f);
        sweepGradient.setLocalMatrix(gradientMatrix);
        progressPaint.setShader(sweepGradient);
    }

    @Override
    protected void onDraw(@androidx.annotation.NonNull Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = (ringBounds.width()) / 2f;

        // 1. Draw Track Ring
        canvas.drawOval(ringBounds, trackPaint);

        // 2. Draw 4 main 6-hour tick marks (00, 06, 12, 18)
        for (int i = 0; i < 4; i++) {
            float angleDeg = -90 + (i * 90);
            float angleRad = (float) Math.toRadians(angleDeg);
            float tickX = cx + radius * (float) Math.cos(angleRad);
            float tickY = cy + radius * (float) Math.sin(angleRad);
            canvas.drawCircle(tickX, tickY, 3.5f, tickPaint);
        }

        // 3. Draw Progress Arc
        float sweepAngle = currentProgress * 360f;
        if (sweepAngle > 0.5f) {
            canvas.drawArc(ringBounds, -90, sweepAngle, false, progressPaint);

            // 4. Draw Pulsing Glowing Tip Dot
            float tipAngleDeg = -90 + sweepAngle;
            float tipAngleRad = (float) Math.toRadians(tipAngleDeg);
            float tipX = cx + radius * (float) Math.cos(tipAngleRad);
            float tipY = cy + radius * (float) Math.sin(tipAngleRad);

            float pulseScale = (float) (Math.sin(pulsePhase) * 3f + 14f);
            canvas.drawCircle(tipX, tipY, pulseScale, dotGlowPaint);
            canvas.drawCircle(tipX, tipY, 7f, dotPaint);
        }
    }

    public void startAnimation() {
        if (!isAnimating) {
            isAnimating = true;
            updateHandler.post(animationRunnable);
        }
    }

    public void stopAnimation() {
        isAnimating = false;
        updateHandler.removeCallbacks(animationRunnable);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    public float getDayProgressPercentage() {
        return currentProgress * 100f;
    }
}
