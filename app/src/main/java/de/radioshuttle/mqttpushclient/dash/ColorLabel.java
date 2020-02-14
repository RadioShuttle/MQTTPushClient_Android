/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient.dash;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
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
        if (mTransparent) {
            Canvas bcanvas = new Canvas(mBitmap);
            bcanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

            bcanvas.drawCircle((mRight - mLeft) / 2f + mLeft, (mBottom - mTop) / 2f + mTop, Math.min(mRight - mLeft, mBottom - mTop) / 2f, mPaintCircle);
            mPaintSquare.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

            float s = mSquareWidth;

            for(int i = 0; i < Math.ceil((mRight - mLeft) / s); i++ ) {
                for(int j = 0; j < Math.ceil((mBottom - mTop) / s); j++) {
                    if (j % 2 == 0) {
                        if ((i % 2 == 0)) {
                            bcanvas.drawRect(i * s + mLeft, j * s + mTop, (i * s) + s + mLeft, (j * s) + s + mTop, mPaintSquare);
                        }
                    } else {
                        if ((i % 2 != 0)) {
                            bcanvas.drawRect(i * s + mLeft, j * s + mTop, (i * s) + s + mLeft, (j * s) + s + mTop, mPaintSquare);
                        }
                    }

                }
            }

            mPaintSquare.setXfermode(null);
            bcanvas.drawCircle((mRight - mLeft) / 2f + mLeft, (mBottom - mTop) / 2f + mTop, Math.min(mRight - mLeft, mBottom - mTop) / 2f, mPaintCircleBorder);
            canvas.drawBitmap(mBitmap, 0, 0, null);
        } else {
            canvas.drawCircle((mRight - mLeft) / 2f + mLeft, (mBottom - mTop) / 2f + mTop, Math.min(mRight - mLeft, mBottom - mTop) / 2f, mPaintCircle);
            canvas.drawCircle((mRight - mLeft) / 2f + mLeft, (mBottom - mTop) / 2f + mTop, Math.min(mRight - mLeft, mBottom - mTop) / 2f, mPaintCircleBorder);
        }
    }

    /** must be called before setColor() to take effect */
    protected void setDisableTransparentImage(boolean disable) {
        mDisableTransparentMatrixDrawing = disable;
    }

    protected void setColor(int color, int borderColor) {
        mPaintCircleBorder.setColor(borderColor);
        if (Color.alpha(color) == 0 && !mDisableTransparentMatrixDrawing) {
            mTransparent = true;
            DisplayMetrics dm = getResources().getDisplayMetrics();
            mBitmap = Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, Bitmap.Config.ARGB_8888);
            mPaintCircle.setColor(Color.WHITE);
            mSquareWidth = TRANSPARENT_SQUARE_SIZE_DP * dm.density;

            mPaintSquare = new Paint();
            mPaintSquare.setAntiAlias(true);
            mPaintSquare.setStyle(Paint.Style.FILL);
            mPaintSquare.setColor(Color.LTGRAY);
        } else {
            mPaintCircle.setColor(color);
            mTransparent = false;
        }
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

        mDisableTransparentMatrixDrawing = false;
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
        mLeft = getPaddingLeft();
        mRight = w - getPaddingRight();
        mTop = getPaddingTop();
        mBottom = h - getPaddingBottom();
    }

    private Bitmap mBitmap;
    private boolean mTransparent;
    private boolean mDisableTransparentMatrixDrawing;
    private float mSquareWidth;
    private Paint mPaintCircleBorder;
    private Paint mPaintCircle;
    private Paint mPaintSquare;
    private float mLeft;
    private float mRight;
    private float mTop;
    private float mBottom;

    private final static int DEFALUT_STROKE_WIDTH_DP = 1;
    private final static int TRANSPARENT_SQUARE_SIZE_DP = 6;
}
