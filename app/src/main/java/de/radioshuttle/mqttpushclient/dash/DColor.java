/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient.dash;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.TypedValue;

import de.radioshuttle.mqttpushclient.R;

public class DColor {
    public final static long OS_DEFAULT = 0x0100000000L;
    public final static long CLEAR = 0x0200000000L;

    public static int fetchAccentColor(Context context) {
        return fetchColor(context, R.attr.colorAccent);
    }

    public static int fetchColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();

        TypedArray a = context.obtainStyledAttributes(typedValue.data, new int[] { attr });
        int color = a.getColor(0, 0);

        a.recycle();

        return color;
    }
}
