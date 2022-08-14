package com.shutdownhook.sharetoroku;

import com.shutdownhook.sharetoroku.R;
import com.shutdownhook.sharetoroku.util.Loggy;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

public class CirclePad extends View {

    private final static int LONG_CLICK_MILLIS = 200;

    public enum Direction { UP, DOWN, LEFT, RIGHT };

    public interface Listener {
        public void onDirectional(Direction direction);
        public void onMiddleClick();
        public void onLongClick();
    }

    public CirclePad(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs,
                R.styleable.CirclePad, 0, 0);

        try {
            colorMain = a.getColor(R.styleable.CirclePad_colorMain, Color.GRAY);
            colorLines = a.getColor(R.styleable.CirclePad_colorLines, Color.LTGRAY);
            widthLines = a.getInt(R.styleable.CirclePad_widthLines, 12);
            colorMiddle = a.getColor(R.styleable.CirclePad_colorMiddle, Color.LTGRAY);
            middleSizePct = a.getInt(R.styleable.CirclePad_middleSizePct, 40);
        }
        finally {
            a.recycle();
        }

        initializeDrawing(context);
        setupGestureDetection(context);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    // +---------+
    // | Drawing |
    // +---------+

    private void initializeDrawing(Context context) {

        paintMain = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintMain.setColor(colorMain);
        paintMain.setStyle(Paint.Style.FILL);

        paintLines = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLines.setColor(colorLines);
        paintLines.setStrokeWidth((float) widthLines);
        paintMain.setStyle(Paint.Style.STROKE);

        paintMiddle = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintMiddle.setColor(colorMiddle);
        paintMain.setStyle(Paint.Style.FILL);

        icon = VectorDrawableCompat.create(context.getResources(),
                R.drawable.ic_baseline_control_camera_24, null);

        icon.setTint(colorMain);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {

        if (w < h) {
            dxpRadius = ((float) w) / 2f;
            xp = dxpRadius;
            yp = (((float) (h - w)) / 2f) + dxpRadius;
        }
        else {
            dxpRadius = ((float) h) / 2f;
            xp = (((float) (w - h)) / 2f) + dxpRadius;
            yp = dxpRadius;
        }

        dxpRadiusMiddle = dxpRadius * ((float) middleSizePct) / 100f;

        clipPath = new Path();
        clipPath.addCircle(xp, yp, dxpRadius, Path.Direction.CW);

        float dxpIconHalf = (dxpRadiusMiddle / 2f);
        icon.setBounds((int) (xp - dxpIconHalf), (int) (yp - dxpIconHalf),
                (int) (xp + dxpIconHalf), (int) (yp + dxpIconHalf));
    }

    @Override
    protected void onDraw(Canvas canvas) {

        canvas.clipPath(clipPath);

        canvas.drawCircle(xp, yp, dxpRadius, paintMain);

        canvas.drawLine(xp - dxpRadius, yp - dxpRadius,
                xp + dxpRadius, yp + dxpRadius, paintLines);

        canvas.drawLine(xp + dxpRadius, yp - dxpRadius,
                xp - dxpRadius, yp + dxpRadius, paintLines);

        canvas.drawCircle(xp, yp, dxpRadiusMiddle, paintMiddle);

        icon.draw(canvas);
    }

    // +----------+
    // | Gestures |
    // +----------+

    public static class CirclePadGestureListener extends GestureDetector.SimpleOnGestureListener
    {
        public CirclePadGestureListener(CirclePad circlePad) {
            this.circlePad = circlePad;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return(true);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2,
                               float velocityX, float velocityY) {

            Listener listener = circlePad.getListener();
            if (listener != null) {

                if (Math.abs(velocityX) > Math.abs(velocityY)) {
                    listener.onDirectional(velocityX < 0 ? Direction.LEFT : Direction.RIGHT);
                } else {
                    listener.onDirectional(velocityY < 0 ? Direction.UP : Direction.DOWN);
                }
            }

            return(true);
        }

        private CirclePad circlePad;
    }

    private void setupGestureDetection(Context context) {
        gestureDetector = new GestureDetector(context, new CirclePadGestureListener(this));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {

        if (gestureDetector.onTouchEvent(e)) {
            return(true);
        }

        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            long duration = e.getEventTime()  - e.getDownTime();
            if (duration < LONG_CLICK_MILLIS) dispatchClick(e.getX(), e.getY());
            else listener.onLongClick();
            return(true);
        }

        return(false);
    }

    public Listener getListener() {
        return(listener);
    }

    public void dispatchClick(float xpClick, float ypClick) {
        if (listener == null) return;

        float distanceFromCenter = (float)
            Math.sqrt(Math.pow(xpClick - xp, 2) + Math.pow(ypClick - yp, 2));

        if (distanceFromCenter > dxpRadius) {
            // outside of pad
            return;
        }

        if (distanceFromCenter < dxpRadiusMiddle) {
            // in center
            listener.onMiddleClick();
            return;
        }

        // this is tricky; note the inversion of the y axis!
        double angle = Math.toDegrees(Math.atan2(yp - ypClick, xpClick - xp));

        Direction dir = Direction.LEFT;
        if (angle > 45f && angle < 135f) { dir = Direction.UP; }
        else if (angle > -45f && angle <= 45f) { dir = Direction.RIGHT; }
        else if (angle > -135f && angle <= -45f) { dir = Direction.DOWN; }

        listener.onDirectional(dir);
    }

    // +-------------------+
    // | Helpers & Members |
    // +-------------------+

    private GestureDetector gestureDetector;
    private Listener listener;
    private VectorDrawableCompat icon;

    private int colorMain;
    private int colorLines;
    private int widthLines;
    private int colorMiddle;
    private int middleSizePct;

    private Paint paintMain;
    private Paint paintLines;
    private Paint paintMiddle;

    private float xp;
    private float yp;
    private float dxpRadius;
    private float dxpRadiusMiddle;

    private Path clipPath;

    private final static Loggy log = new Loggy(CirclePad.class.getName());
}

