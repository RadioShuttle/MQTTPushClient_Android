/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.constraintlayout.widget.ConstraintLayout;

// intercepts all motion events to childs
public class DashConstraintLayout extends ConstraintLayout {
    public DashConstraintLayout(Context context) {
        super(context);
    }

    public DashConstraintLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DashConstraintLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setInterceptTouchEvent(boolean intercept) {
        mInterceptTouchEvent = intercept;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return mInterceptTouchEvent;
    }

    private boolean mInterceptTouchEvent = true;
}
