package com.camera.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class CameraButton extends View {
    private Paint outerCirclePaint;
    private Paint innerCirclePaint;
    private Paint pressedPaint;
    private float centerX;
    private float centerY;
    private float outerRadius;
    private float innerRadius;
    private boolean isPressed = false;
    private OnClickListener onClickListener;

    // 颜色定义
    private int outerCircleColor = 0xFFFFFFFF; // 白色外圈
    private int innerCircleColor = 0xFFFFFFFF; // 白色内圈
    private int pressedColor = 0x80FFFFFF; // 半透明白色（按下时）

    public CameraButton(Context context) {
        super(context);
        init();
    }

    public CameraButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CameraButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 外圈画笔
        outerCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerCirclePaint.setColor(outerCircleColor);
        outerCirclePaint.setStyle(Paint.Style.STROKE);
        outerCirclePaint.setStrokeWidth(8f);

        // 内圈画笔
        innerCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerCirclePaint.setColor(innerCircleColor);
        innerCirclePaint.setStyle(Paint.Style.FILL);

        // 按下时的画笔
        pressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pressedPaint.setColor(pressedColor);
        pressedPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        outerRadius = Math.min(w, h) / 2f - 10f;
        innerRadius = outerRadius - 20f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 绘制外圈
        canvas.drawCircle(centerX, centerY, outerRadius, outerCirclePaint);

        // 绘制内圈
        canvas.drawCircle(centerX, centerY, innerRadius, innerCirclePaint);

        // 如果按下，绘制半透明覆盖层
        if (isPressed) {
            canvas.drawCircle(centerX, centerY, innerRadius, pressedPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isPressed = true;
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                isPressed = false;
                invalidate();
                if (onClickListener != null) {
                    onClickListener.onClick(this);
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                isPressed = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        this.onClickListener = l;
    }

    // 设置外圈颜色
    public void setOuterCircleColor(int color) {
        this.outerCircleColor = color;
        outerCirclePaint.setColor(color);
        invalidate();
    }

    // 设置内圈颜色
    public void setInnerCircleColor(int color) {
        this.innerCircleColor = color;
        innerCirclePaint.setColor(color);
        invalidate();
    }

    // 设置按下时的颜色
    public void setPressedColor(int color) {
        this.pressedColor = color;
        pressedPaint.setColor(color);
        invalidate();
    }
}