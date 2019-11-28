/*
 * $Id$
 * This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.mqttpushclient.dash;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;

import de.radioshuttle.mqttpushclient.R;

public class OptionDeleteDialog extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        final int n = args.getInt(NO_SELECTED, 2);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getString(R.string.dlg_delete_option_title));
        if (n == 1) {
            builder.setMessage(getString(R.string.dlg_delete_option_msg));
        } else {
            builder.setMessage(getString(R.string.dlg_delete_option_msg_pl));
        }

        builder.setPositiveButton(R.string.action_delete_topics, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Activity a = getActivity();
                if (a instanceof DashBoardEditActivity) {
                    ((DashBoardEditActivity) a).deleteSelectedOptions();
                }
            }
        });

        builder.setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        AlertDialog dlg = builder.create();
        dlg.setCanceledOnTouchOutside(false);

        return dlg;
    }

    public final static String NO_SELECTED = "NO_SELECTED";
}