/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import de.radioshuttle.net.ActionsRequest;
import de.radioshuttle.net.AppTrustManager;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.net.Connection;
import de.radioshuttle.net.Request;
import de.radioshuttle.utils.Utils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static de.radioshuttle.mqttpushclient.EditAccountActivity.PARAM_ACCOUNT_JSON;

public class EditActionActivity extends AppCompatActivity implements CertificateErrorDialog.Callback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_action);

        Bundle args = getIntent().getExtras();

        mActionNameTextView = findViewById(R.id.actionNameEditText);
        mActionTopicTextView = findViewById(R.id.actionTopicText);
        mActionContentTextView = findViewById(R.id.actionContentText);
        mRetainCheckBox = findViewById(R.id.retain);
        mSaveButton = findViewById(R.id.save_button);
        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });

        if (savedInstanceState == null) {
            if (args != null) {
                mMode = args.getInt(ARG_EDIT_MODE, MODE_ADD);
                mActionName = args.getString(ARG_NAME);
                mPrevName = mActionName;
                mTopic = args.getString(ARG_TOPIC);
                mContent = args.getString(ARG_CONTENT);
                mRetain = args.getBoolean(ARG_RETAIN);
                mActionNameTextView.setText(mActionName);
                mActionTopicTextView.setText(mTopic);
                mActionContentTextView.setText(mContent);
                mRetainCheckBox.setChecked(mRetain);
            }
            mSaved = false;
            mSavedActions = null;
        } else {
            // last saved
            mMode = savedInstanceState.getInt(ARG_EDIT_MODE);
            mActionName = savedInstanceState.getString(ARG_NAME);
            mPrevName = savedInstanceState.getString(ARG_PREV);
            mTopic = savedInstanceState.getString(ARG_TOPIC);
            mContent = savedInstanceState.getString(ARG_CONTENT);
            mRetain = savedInstanceState.getBoolean(ARG_RETAIN);
            mSaved = savedInstanceState.getBoolean(IS_SAVED);
            mSavedActions = savedInstanceState.getString(RESULT_ACTIONS);
        }

        String json = args.getString(PARAM_ACCOUNT_JSON);
        mViewModel = ViewModelProviders.of(this).get(ActionsViewModel.class);
        try {
            mViewModel.init(json);
        } catch (JSONException e) {
            Log.e(TAG, "parse error", e);
        }

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setEnabled(false);
        mSwipeRefreshLayout.setRefreshing(mViewModel.isRequestActive());
        updateUI(!mViewModel.isRequestActive());

        mViewModel.actionsRequest.observe(this, new Observer<Request>() {
            @Override
            public void onChanged(Request request) {
                if (request != null && request instanceof ActionsRequest) {
                    ActionsRequest actionsRequest = (ActionsRequest) request;
                    PushAccount b = actionsRequest.getAccount();
                    if (b.status == 1) {
                        //
                    } else {
                        boolean isNew = false;
                        if (mViewModel.isCurrentRequest(request)) {
                            isNew = mViewModel.isRequestActive(); // result already processed/displayed?
                            mViewModel.confirmResultDelivered();
                            mSwipeRefreshLayout.setRefreshing(false);
                            updateUI(true);
                            invalidateOptionsMenu();

                            /* handle cerificate exception */
                            if (b.hasCertifiateException()) {
                                /* only show dialog if the certificate has not already been denied */
                                if (!AppTrustManager.isDenied(b.getCertificateException().chain[0])) {
                                    FragmentManager fm = getSupportFragmentManager();

                                    String DLG_TAG = CertificateErrorDialog.class.getSimpleName() + "_" +
                                            AppTrustManager.getUniqueKey(b.getCertificateException().chain[0]);

                                    /* check if a dialog is not already showing (for this certificate) */
                                    if (fm.findFragmentByTag(DLG_TAG) == null) {
                                        CertificateErrorDialog dialog = new CertificateErrorDialog();
                                        Bundle args = CertificateErrorDialog.createArgsFromEx(
                                                b.getCertificateException(), request.getAccount().pushserver);
                                        if (args != null) {
                                            int cmd = ((ActionsRequest) request).mCmd;
                                            if (cmd == Cmd.CMD_ADD_ACTION || cmd == Cmd.CMD_UPD_ACTION) { // Cmd.CMD_ADD_ACTION; Cmd.CMD_DEL_ACTIONS;
                                                args.putString("action_name", actionsRequest.mActionArg.name);
                                                args.putString("action_prevName",actionsRequest.mActionArg.prevName);
                                                args.putString("action_content",actionsRequest.mActionArg.content);
                                                args.putBoolean("action_retain",actionsRequest.mActionArg.retain);
                                                args.putString("action_topic",actionsRequest.mActionArg.topic);
                                            }
                                            args.putInt("cmd", cmd);
                                            dialog.setArguments(args);
                                            dialog.show(getSupportFragmentManager(), DLG_TAG);
                                        }
                                    }
                                }
                            } /* end dialog already showing */
                            b.setCertificateExeption(null); // mark es "processed"
                            /* end handle cerificate exception */

                            /* handle insecure connection */
                            if (b.inSecureConnectionAsk) {
                                if (Connection.mInsecureConnection.get(b.pushserver) == null) {
                                    FragmentManager fm = getSupportFragmentManager();

                                    String DLG_TAG = InsecureConnectionDialog.class.getSimpleName() + "_" + b.pushserver;

                                    /* check if a dialog is not already showing (for this host) */
                                    if (fm.findFragmentByTag(DLG_TAG) == null) {
                                        InsecureConnectionDialog dialog = new InsecureConnectionDialog();
                                        Bundle args = InsecureConnectionDialog.createArgsFromEx(b.pushserver);
                                        if (args != null) {
                                            int cmd = ((ActionsRequest) request).mCmd;
                                            if (cmd == Cmd.CMD_ADD_ACTION || cmd == Cmd.CMD_UPD_ACTION) { // Cmd.CMD_ADD_ACTION; Cmd.CMD_DEL_ACTIONS;
                                                args.putString("action_name", actionsRequest.mActionArg.name);
                                                args.putString("action_prevName",actionsRequest.mActionArg.prevName);
                                                args.putString("action_content",actionsRequest.mActionArg.content);
                                                args.putBoolean("action_retain",actionsRequest.mActionArg.retain);
                                                args.putString("action_topic",actionsRequest.mActionArg.topic);
                                            }
                                            args.putInt("cmd", cmd);
                                            dialog.setArguments(args);
                                            dialog.show(getSupportFragmentManager(), DLG_TAG);
                                        }
                                    }
                                }
                            }
                            b.inSecureConnectionAsk = false; // mark as "processed"

                        }
                        if (b.requestStatus != Cmd.RC_OK) {
                            String t = (b.requestErrorTxt == null ? "" : b.requestErrorTxt);
                            if (b.requestStatus == Cmd.RC_MQTT_ERROR || (b.requestStatus == Cmd.RC_NOT_AUTHORIZED && b.requestErrorCode != 0)) {
                                t = EditActionActivity.this.getString(R.string.errormsg_mqtt_prefix) + " " + t;
                            }
                            showErrorMsg(t);
                        } else {
                            if (actionsRequest.requestStatus != Cmd.RC_OK) { // topics add or delete result
                                String t = (actionsRequest.requestErrorTxt == null ? "" : actionsRequest.requestErrorTxt);
                                if (actionsRequest.requestStatus == Cmd.RC_MQTT_ERROR) {
                                    t = EditActionActivity.this.getString(R.string.errormsg_mqtt_prefix) + " " + t;
                                }
                                showErrorMsg(t);
                            } else {
                                if (mSnackbar != null && mSnackbar.isShownOrQueued()) {
                                    mSnackbar.dismiss();
                                }
                                // set new "saved" state
                                if (actionsRequest.mActions != null) {
                                    // actionsRequest.mActionArg;
                                    if (mMode == MODE_ADD) {
                                        mMode = MODE_EDIT;
                                        updateUI(true);
                                    }
                                    mActionName = actionsRequest.mActionArg.name;
                                    mPrevName = actionsRequest.mActionArg.name;
                                    mTopic = actionsRequest.mActionArg.topic;
                                    mContent = actionsRequest.mActionArg.content;
                                    mRetain = actionsRequest.mActionArg.retain;

                                    try {
                                        JSONArray resultActions = new JSONArray();

                                        JSONObject te;
                                        for(ActionsViewModel.Action a : actionsRequest.mActions) {
                                            te = new JSONObject();
                                            te.put("actionname", a.name);
                                            te.put("prev_actionname", a.prevName);
                                            te.put("topic", a.topic);
                                            te.put("content", a.content);
                                            te.put("retain", a.retain);
                                            resultActions.put(te);
                                        }
                                        mSavedActions = resultActions.toString();

                                    } catch (Exception e1) {
                                        Log.d(TAG, "onChanged(): Error parsing json object", e1);
                                    }
                                    mSaved = true;
                                    if (isNew) {
                                        Toast.makeText(getApplicationContext(), R.string.info_data_saved, Toast.LENGTH_LONG).show();
                                    }

                                }
                            }
                        }
                    }
                }

            }
        });


        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_edit_action, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home :
                handleBackPressed();
                return true;
            case R.id.action_save:
                save();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem m = menu.findItem(R.id.action_save);
        if (m != null) {
            m.setEnabled(!mViewModel.isRequestActive());
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(ARG_EDIT_MODE, mMode);

        if (!Utils.isEmpty(mActionName)) {
            outState.putString(ARG_NAME, mActionName);
        }
        if (!Utils.isEmpty(mPrevName)) {
            outState.putString(ARG_PREV, mPrevName);
        }
        if (!Utils.isEmpty(mTopic)) {
            outState.putString(ARG_TOPIC, mTopic);
        }
        if (!Utils.isEmpty(mContent)) {
            outState.putString(ARG_CONTENT, mContent);
        }
        outState.putBoolean(ARG_RETAIN, mRetain);

        outState.putBoolean(IS_SAVED, mSaved);

        if (!Utils.isEmpty(mSavedActions)) {
            outState.putString(RESULT_ACTIONS, mSavedActions);
        }

    }

    @Override
    public void onBackPressed() {
        handleBackPressed();
        // super.onBackPressed();
    }

   protected void handleBackPressed() {
        if (hasDataChanged()) {
            QuitWithoutSaveDlg dlg = new QuitWithoutSaveDlg();
            if (mSaved) {
                Bundle args = new Bundle();
                args.putString(RESULT_ACTIONS, mSavedActions);
                dlg.setArguments(args);
            }
            dlg.show(getSupportFragmentManager(), QuitWithoutSaveDlg.class.getSimpleName());
        } else {
            if (mSaved) {
                Intent data = new Intent();
                data.putExtra(RESULT_ACTIONS, mSavedActions);
                setResult(AppCompatActivity.RESULT_OK, data);
                finish();
            } else {
                setResult(AppCompatActivity.RESULT_CANCELED);
                finish();
            }
        }
    }

    protected boolean hasDataChanged() {
        boolean changed = false;
        if (!Utils.equals(mActionName, mActionNameTextView.getText().toString())) {
            changed = true;
        } else if (!Utils.equals(mTopic, mActionTopicTextView.getText().toString())) {
            changed = true;
        } else if (!Utils.equals(mContent, mActionContentTextView.getText().toString())) {
            changed = true;
        } else if (mRetain != mRetainCheckBox.isChecked()) {
            changed = true;
        }
        return changed;
    }

    protected void save() {
        if (checkData()) {
            if (hasDataChanged()) {
                updateUI(false);
                ActionsViewModel.Action a = new ActionsViewModel.Action();
                a.name = mActionNameTextView.getText().toString();
                a.topic = mActionTopicTextView.getText().toString();
                a.prevName = mPrevName;
                a.content = mActionContentTextView.getText().toString();
                a.retain = mRetainCheckBox.isChecked();
                if (!mViewModel.isRequestActive()) {
                    mSwipeRefreshLayout.setRefreshing(true);
                    if (mMode == MODE_ADD) {
                        mViewModel.addAction(this, a);
                    } else if (mMode == MODE_EDIT) {
                        mViewModel.updateAction(this, a);
                    }
                }
            } else {
                Toast.makeText(getApplicationContext(), R.string.error_data_unmodified, Toast.LENGTH_LONG).show();
            }
        }
    }

    protected boolean checkData() {
        boolean ok = true;
        if (Utils.isEmpty(mActionNameTextView.getText().toString())) {
            mActionNameTextView.setError(getString(R.string.error_empty_field));
            ok = false;
        } else if (Utils.isEmpty(mActionTopicTextView.getText().toString())) {
            mActionTopicTextView.setError(getString(R.string.error_empty_field));
            ok = false;
        } else if (Utils.isEmpty(mActionContentTextView.getText().toString())) {
            mActionContentTextView.setError(getString(R.string.error_empty_field));
            ok = false;
        }
        return ok;
    }

    protected void showErrorMsg(String msg) {
        View v = findViewById(R.id.rView);
        if (v != null) {
            mSnackbar = Snackbar.make(v, msg, Snackbar.LENGTH_INDEFINITE);
            mSnackbar.show();
        }
    }

    protected void updateUI(boolean enableFields) {
        if (mMode == MODE_ADD) {
            setTitle(R.string.dlg_actions_title_add);
        } else {
            setTitle(R.string.dlg_actions_title_update);
        }
        mActionNameTextView.setEnabled(enableFields);
        mActionTopicTextView.setEnabled(enableFields);
        mActionContentTextView.setEnabled(enableFields);
        mRetainCheckBox.setEnabled(enableFields);
        mSaveButton.setEnabled(enableFields);

        invalidateOptionsMenu();
    }

    @Override
    public void retry(Bundle args) {
        if (args != null) {
            int cmd = args.getInt("cmd", 0);
            if (cmd == Cmd.CMD_ADD_ACTION || cmd == Cmd.CMD_UPD_ACTION) {
                ActionsViewModel.Action a = new ActionsViewModel.Action();
                a.name = args.getString("action_name");
                a.prevName = args.getString("action_prevName");
                a.content = args.getString("action_content");
                a.retain = args.getBoolean("action_retain");
                a.topic = args.getString("action_topic");

                if (!mViewModel.isRequestActive()) {
                    mSwipeRefreshLayout.setRefreshing(true);

                    if (cmd == Cmd.CMD_ADD_ACTION) {
                        mViewModel.addAction(this, a);
                    } else {
                        mViewModel.updateAction(this, a);
                    }
                }
            }
        }
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
                    Bundle args = getArguments();
                    if (args != null && !Utils.isEmpty(args.getString(RESULT_ACTIONS))) {
                        Intent data = new Intent();
                        data.putExtra(RESULT_ACTIONS, args.getString(RESULT_ACTIONS));
                        getActivity().setResult(AppCompatActivity.RESULT_OK, data);
                        getActivity().finish();
                    } else {
                        getActivity().setResult(AppCompatActivity.RESULT_CANCELED);
                        getActivity().finish();
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


    protected int mMode;

    protected String mActionName;
    protected String mPrevName;
    protected String mTopic;
    protected String mContent;
    protected boolean mRetain;

    protected boolean mSaved;
    protected String mSavedActions;

    protected TextView mActionNameTextView;
    protected TextView mActionTopicTextView;
    protected TextView mActionContentTextView;
    protected CheckBox mRetainCheckBox;
    protected Button mSaveButton;

    protected Snackbar mSnackbar;
    protected SwipeRefreshLayout mSwipeRefreshLayout;
    protected ActionsViewModel mViewModel;

    public final static String ARG_EDIT_MODE = "ARG_EDIT_MODE";
    public final static String ARG_NAME = "ARG_NAME";
    public final static String ARG_TOPIC = "ARG_TOPIC";
    public final static String ARG_CONTENT = "ARG_CONTENT";
    public final static String ARG_PREV = "ARG_PREV";
    public final static String ARG_RETAIN = "ARG_RETAIN";

    public final static String RESULT_ACTIONS = "RESULT_ACTIONS";
    private final static String IS_SAVED = "IS_SAVED";

    public final static int MODE_ADD = 0;
    public final static int MODE_EDIT = 1;

    private final static String TAG = EditActionActivity.class.getSimpleName();
}
