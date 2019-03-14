/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import de.radioshuttle.mqttpushclient.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class DashBoardEditActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dash_board_edit);

        Intent intent = getIntent();
        mMode = intent.getIntExtra(ARG_MODE, MODE_ADD);
        try {
            mItem = (Item) Class.forName(intent.getStringExtra(ARG_TYPE)).newInstance();
        } catch (Exception e) {
            Log.e(TAG, "Init failed: " + e.getMessage());
        }

        String title = "";
        if (mItem != null) {
            if (mMode == MODE_ADD) {
                if (mItem instanceof GroupItem) {
                    title = getString(R.string.title_add_group);
                } else if (mItem instanceof TextItem) {
                    title = getString(R.string.title_add_text);
                }
            } else { // mMode == MODE_EDIT
                if (mItem instanceof GroupItem) {
                    title = getString(R.string.title_edit_group);
                } else if (mItem instanceof TextItem) {
                    title = getString(R.string.title_edit_text);
                }
            }
        }


        setTitle(title);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    protected void handleBackPressed() {
        if (hasDataChanged()) {
            QuitWithoutSaveDlg dlg = new QuitWithoutSaveDlg();
            dlg.show(getSupportFragmentManager(), QuitWithoutSaveDlg.class.getSimpleName());
        } else {
            setResult(AppCompatActivity.RESULT_CANCELED);
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_dash_board_edit, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean consumed = true;
        switch (item.getItemId()) {
            case android.R.id.home:
                handleBackPressed();
            case R.id.action_save:
                saveAndQuit();
                break;
            default:
                consumed = super.onOptionsItemSelected(item);
        }
        return consumed;
    }

    @Override
    public void onBackPressed() {
        handleBackPressed();
    }

    public static class QuitWithoutSaveDlg extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getString(R.string.dlg_back_without_save_title));
            builder.setMessage(getString(R.string.dlg_back_without_save_msg));

            builder.setPositiveButton(R.string.action_back, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    getActivity().setResult(AppCompatActivity.RESULT_CANCELED);
                    getActivity().finish();
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

    protected void saveAndQuit() {
        Intent intent = new Intent();
        intent.putExtra(ARG_MODE, mMode);
        if (mItem != null) {
            intent.putExtra(ARG_TYPE, mItem.getClass().getName());
            //TODO: add data
        }
        setResult(AppCompatActivity.RESULT_OK, intent);
        finish();
    }


    protected boolean hasDataChanged() {
        return false; //TODO
    }


    int mMode;
    Item mItem;

    private final static String TAG = DashBoardEditActivity.class.getSimpleName();

    public final static int MODE_ADD = 1;
    public final static int MODE_EDIT = 2;

    public final static String ARG_MODE = "ARG_MODE";
    public final static String ARG_TYPE = "ARG_TYPE";
}
