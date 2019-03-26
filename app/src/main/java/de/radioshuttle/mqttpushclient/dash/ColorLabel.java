/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.annotation.Nullable;

public class ColorLabel extends View
{
    public ColorLabel(Context context) {
        super(context);
        init();
    }

    public ColorLabel(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ColorLabel(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(21)
    public ColorLabel(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    @Override
    protected void onDraw(Canvas canvas) {

        canvas.drawCircle((mRight - mLeft) / 2f + mLeft, (mBottom - mTop) / 2f + mTop, Math.min(mRight - mLeft, mBottom - mTop) / 2f, mPaintCircle);
        canvas.drawCircle((mRight - mLeft) / 2f + mLeft, (mBottom - mTop) / 2f + mTop, Math.min(mRight - mLeft, mBottom - mTop) / 2f, mPaintCircleBorder);
    }

    protected void setColor(int color, int borderColor) {
        mPaintCircle.setColor(color);
        mPaintCircleBorder.setColor(borderColor);
        invalidate();
    }

    protected void init() {
        mPaintCircleBorder = new Paint();
        mPaintCircleBorder.setAntiAlias(true);
        mPaintCircleBorder.setStyle(Paint.Style.STROKE);
        DisplayMetrics dp = getResources().getDisplayMetrics();
        float strokeWidth = (float) DEFALUT_STROKE_WIDTH_DP * dp.density;
        mPaintCircleBorder.setStrokeWidth(strokeWidth);

        mPaintCircleBorder.setColor(Color.BLACK);

        mPaintCircle = new Paint();
        mPaintCircle.setAntiAlias(true);
        mPaintCircle.setStyle(Paint.Style.FILL);
        mPaintCircle.setColor(Color.WHITE);
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
        mLeft = getPaddingLeft();
        mRight = w - getPaddingRight();
        mTop = getPaddingTop();
        mBottom = h - getPaddingBottom();
    }

    private Paint mPaintCircleBorder;
    private Paint mPaintCircle;
    private float mLeft;
    private float mRight;
    private float mTop;
    private float mBottom;

    private final static int DEFALUT_STROKE_WIDTH_DP = 1;

}
