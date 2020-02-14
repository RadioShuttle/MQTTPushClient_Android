/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.net;

import android.content.Context;

import java.io.IOException;

import de.radioshuttle.mqttpushclient.R;

public class IncompatibleProtocolException extends IOException {
    public IncompatibleProtocolException(int expectedMajor, int expectedMinor, int currMajor, int currentMin, Context context) {
        mExpectedMajor = expectedMajor;
        mExpectedMinor = expectedMinor;
        mCurrentMajor = currMajor;
        mCurrentMinor = currentMin;
        mAppContext = context.getApplicationContext();
    }

    @Override
    public String getMessage() {
        return getLocalizedMessage();
    }

    @Override
    public String getLocalizedMessage() {
        String msg;
        String localizedMsg = mAppContext.getResources().getString(R.string.errormsg_incompatible_protocol);
        if (localizedMsg != null)
            msg = String.format(localizedMsg, mExpectedMajor, mExpectedMinor, mCurrentMajor, mCurrentMinor);
        else
            msg = null;
        return msg;
    }

    private Context mAppContext;

    private int mExpectedMajor;
    private int mExpectedMinor;
    private int mCurrentMajor;
    private int mCurrentMinor;

}
