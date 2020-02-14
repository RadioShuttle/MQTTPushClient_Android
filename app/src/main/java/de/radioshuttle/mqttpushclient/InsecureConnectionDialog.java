/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.fragment.app.DialogFragment;
import de.radioshuttle.net.Connection;

public class InsecureConnectionDialog extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setTitle(R.string.dlg_certerr_title);

        StringBuilder sb = new StringBuilder();
        Bundle args = getArguments();

        String host = args.getString("host", "host");
        sb.append(getString(R.string.dlg_insecure_txt, host));
        // sb.append('\n');

        builder.setMessage(sb);

        builder.setPositiveButton(getString(R.string.action_allow), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (mDlgCallback != null && getArguments() != null) {
                    mDlgCallback.retry(getArguments());
                }
                Connection.mInsecureConnection.put(getArguments().getString("host", ""), true);
            }
        });

        builder.setNegativeButton(R.string.action_deny, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Connection.mInsecureConnection.put(getArguments().getString("host", ""), false);
            }
        });

        builder.setNeutralButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        return builder.create();

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (Build.VERSION.SDK_INT < 23) {
            onAttachToContext(activity);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        onAttachToContext(context);
    }

    protected void onAttachToContext(Context context) {
        if (context instanceof CertificateErrorDialog.Callback)
            mDlgCallback = (CertificateErrorDialog.Callback) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mDlgCallback = null;
    }


    public static Bundle createArgsFromEx(String host)  {
        Bundle b = new Bundle();
        if (host != null)
            b.putString("host", host);
        return b;
    }

    private CertificateErrorDialog.Callback mDlgCallback;

    private final static String TAG = InsecureConnectionDialog.class.getSimpleName();

}
