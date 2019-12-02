/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import de.radioshuttle.mqttpushclient.dash.DashBoardEditActivity;

public class ConfirmClearDialog extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        FragmentActivity a = getActivity();
        if (a instanceof JavaScriptEditorActivity) {
            builder.setTitle(getString(R.string.dlg_confirm_clear_title));
        } else if (a instanceof DashBoardEditActivity) {
            builder.setTitle(getString(R.string.dlg_confirm_clear_html_title));
        }
        // builder.setMessage(getString(R.string.dlg_back_without_save_js_msg));

        builder.setPositiveButton(R.string.action_clear, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                FragmentActivity a = getActivity();
                if (a instanceof JavaScriptEditorActivity) {
                    if (((JavaScriptEditorActivity) a).mEditor != null) {
                        ((JavaScriptEditorActivity) a).mEditor.setText(null);
                    }
                } else if (a instanceof DashBoardEditActivity) {
                    DashBoardEditActivity da = (DashBoardEditActivity) a;
                    if (da.mEditTextHTML != null) {
                        da.mEditTextHTML.setText(null);
                    }
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
}