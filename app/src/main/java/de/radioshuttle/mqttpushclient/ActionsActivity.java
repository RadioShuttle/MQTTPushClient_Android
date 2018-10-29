/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import android.app.Activity;
import android.app.Dialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import android.content.DialogInterface;
import android.content.Intent;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

import de.radioshuttle.net.ActionsRequest;
import de.radioshuttle.net.AppTrustManager;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.net.Request;

import static de.radioshuttle.mqttpushclient.ActionsActivity.EditActionDialog.*;
import static de.radioshuttle.mqttpushclient.EditAccountActivity.PARAM_ACCOUNT_JSON;
import static de.radioshuttle.mqttpushclient.MessagesActivity.PARAM_MULTIPLE_PUSHSERVERS;

public class ActionsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_actions);
        setTitle(getString(R.string.title_actions));

        mViewModel = ViewModelProviders.of(this).get(ActionsViewModel.class);
        boolean actionsLoaded = mViewModel.initialized;

        mViewModel.actionsRequest.observe(this, new Observer<Request>() {
            @Override
            public void onChanged(@Nullable Request request) {
                if (request != null && request instanceof ActionsRequest) {
                    ActionsRequest actionsRequest = (ActionsRequest) request;
                    PushAccount b = actionsRequest.getAccount();
                    if (b.status == 1) {
                        if (mAdapter != null && mAdapter.getItemCount() == 0) {
                            if (actionsRequest.mActions.size() > 0) {
                                // special case: configuration change and load in progress -> update view
                                mAdapter.setData(actionsRequest.mActions);
                            }
                        }
                    } else {
                        if (mViewModel.isCurrentRequest(request)) {
                            mViewModel.confirmResultDelivered();
                            mSwipeRefreshLayout.setRefreshing(false);
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
                                            dialog.setArguments(args);
                                            dialog.show(getSupportFragmentManager(), DLG_TAG);
                                            Log.d(TAG, "dialog show!!"); //TODO: remove
                                        }
                                    }
                                }
                            } /* end dialog already showing */
                            b.setCertificateExeption(null); // mark es "processed"
                            /* end handle cerificate exception */

                        }
                        if (b.requestStatus != Cmd.RC_OK) {
                            String t = (b.requestErrorTxt == null ? "" : b.requestErrorTxt);
                            if (b.requestStatus == Cmd.RC_MQTT_ERROR || b.requestStatus == Cmd.RC_NOT_AUTHORIZED) {
                                t = ActionsActivity.this.getString(R.string.errormsg_mqtt_prefix) + " " + t;
                            }
                            showErrorMsg(t);
                        } else {
                            if (actionsRequest.requestStatus != Cmd.RC_OK) { // topics add or delete result
                                String t = (actionsRequest.requestErrorTxt == null ? "" : actionsRequest.requestErrorTxt);
                                if (actionsRequest.requestStatus == Cmd.RC_MQTT_ERROR) {
                                    t = ActionsActivity.this.getString(R.string.errormsg_mqtt_prefix) + " " + t;
                                }
                                showErrorMsg(t);
                            } else {
                                if (mSnackbar != null && mSnackbar.isShownOrQueued()) {
                                    mSnackbar.dismiss();
                                }
                            }
                        }
                        if (mAdapter != null) {
                            mAdapter.setData(actionsRequest.mActions);
                        }
                    }
                }
            }
        });


        Bundle args = getIntent().getExtras();
        String json = args.getString(PARAM_ACCOUNT_JSON);
        boolean hastMultipleServer = args.getBoolean(PARAM_MULTIPLE_PUSHSERVERS);

        try {
            mViewModel.init(json);
            TextView server = findViewById(R.id.push_notification_server);
            TextView key = findViewById(R.id.account_display_name);
            server.setText(mViewModel.pushAccount.pushserver);
            key.setText(mViewModel.pushAccount.getDisplayName());
            if (!hastMultipleServer) {
                server.setVisibility(View.GONE);
            }
        } catch (JSONException e) {
            Log.e(TAG, "parse error", e);
        } finally {

        }

        mListView = findViewById(R.id.actionsListView);
        RecyclerView.ItemDecoration itemDecoration =
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        mListView.addItemDecoration(itemDecoration);
        mListView.setItemAnimator(null);
        mListView.setLayoutManager(new LinearLayoutManager(this));

        if (mViewModel.selectedActions != null && mViewModel.selectedActions.size() > 0) {
            // mActionMode = startSupportActionMode(mActionModeCallback);
        }

        mAdapter = new ActionsRecyclerViewAdapter(this, mViewModel.selectedActions, new ActionsRecyclerViewAdapter.RowSelectionListener() {
            @Override
            public void onSelectionChange(int noOfSelectedItemsBefore, int noOfSelectedItems) {
                if (noOfSelectedItemsBefore == 0 && noOfSelectedItems > 0) {
                    mActionMode = startSupportActionMode(mActionModeCallback);
                } else if (noOfSelectedItemsBefore > 0 && noOfSelectedItems == 0) {
                    if (mActionMode != null)
                        mActionMode.finish();
                }
            }
        });
        mListView.setAdapter(mAdapter);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh();
            }
        });

        mSwipeRefreshLayout.setRefreshing(mViewModel.isRequestActive());

        if (!actionsLoaded) {
            refresh();
        }

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    }

    protected void showEditDialog(int mode, ActionsViewModel.Action a, String errorTxt, int errorIdx) {
        if (mViewModel.isRequestActive()) {
            Toast.makeText(getApplicationContext(), R.string.op_in_progress, Toast.LENGTH_LONG).show();
        } else {
            EditActionDialog dlg = new EditActionDialog();
            Bundle args = new Bundle();
            args.putInt(EditActionDialog.ARG_EDIT_MODE, mode);
            if (a != null) {
                args.putString(ARG_NAME, a.name);
                args.putString(ARG_TOPIC, a.topic);
                args.putString(ARG_CONTENT, a.content);
                args.putBoolean(ARG_RETIAN, a.retain);
                if (mode == MODE_EDIT) {
                    if (!Utils.isEmpty(a.prevName)) {
                        args.putString(ARG_PREV, a.prevName);
                    } else {
                        args.putString(ARG_PREV, a.name);
                    }
                }
            }
            if (!Utils.isEmpty(errorTxt)) {
                args.putString(ARG_ERROR, errorTxt);
                args.putInt(ARG_IDX, errorIdx);
            }
            dlg.setArguments(args);

            dlg.show(getSupportFragmentManager(), ActionsActivity.EditActionDialog.class.getSimpleName());
        }
    }

    public void addAction(ActionsViewModel.Action a) {
        if (Utils.isEmpty(a.name)) {
            showEditDialog(MODE_ADD, a, getString(R.string.error_empty_field), 0);
        } else if (Utils.isEmpty(a.topic)) {
            showEditDialog(MODE_ADD, a, getString(R.string.error_empty_field), 1);
        } else if (Utils.isEmpty(a.content)) {
            showEditDialog(MODE_ADD, a, getString(R.string.error_empty_field), 2);
        }  else if (!mViewModel.isRequestActive()) {
            mSwipeRefreshLayout.setRefreshing(true);
            mViewModel.addAction(this, a);
        }
    }

    public void updateAction(ActionsViewModel.Action a) {
        if (Utils.isEmpty(a.name)) {
            showEditDialog(MODE_EDIT, a, getString(R.string.error_empty_field), 0);
        } else if (Utils.isEmpty(a.topic)) {
            showEditDialog(MODE_EDIT, a, getString(R.string.error_empty_field), 1);
        } else if (Utils.isEmpty(a.content)) {
            showEditDialog(MODE_EDIT, a, getString(R.string.error_empty_field), 2);
        } else if (!mViewModel.isRequestActive()) {
            mSwipeRefreshLayout.setRefreshing(true);
            mViewModel.updateAction(this, a);
        }
    }

    public void deleteActions(List<String> actions) {
        if (!mViewModel.isRequestActive()) {
            mSwipeRefreshLayout.setRefreshing(true);
            mViewModel.deleteActions(this, actions);
        }
    }

    public static class EditActionDialog extends DialogFragment {
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Bundle args = getArguments();
            final int mode = args.getInt(ARG_EDIT_MODE);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            LayoutInflater inflater = (LayoutInflater) builder.getContext().getSystemService(LAYOUT_INFLATER_SERVICE);
            View body = inflater.inflate(R.layout.dlg_actions_body, null);

            String errorTxt = args.getString(ARG_ERROR);
            int idx = args.getInt(ARG_IDX, -1);

            final TextView actionName = body.findViewById(R.id.actionNameEditText);
            if (actionName != null) {
                actionName.setText(args.getString(ARG_NAME));
                if (idx == 0 && !Utils.isEmpty(errorTxt)) {
                    actionName.setError(errorTxt);
                }
            }
            final TextView actionTopic = body.findViewById(R.id.actionTopicText);
            if (actionTopic != null) {
                actionTopic.setText(args.getString(ARG_TOPIC));
                if (idx == 1 && !Utils.isEmpty(errorTxt)) {
                    actionTopic.setError(errorTxt);
                }
            }

            final TextView actionContent = body.findViewById(R.id.actionContentText);
            if (actionContent != null) {
                actionContent.setText(args.getString(ARG_CONTENT));
                if (idx == 2 && !Utils.isEmpty(errorTxt)) {
                    actionContent.setError(errorTxt);
                }
            }
            final CheckBox retainCheckBox = body.findViewById(R.id.retain);
            if (retainCheckBox != null) {
                retainCheckBox.setChecked(args.getBoolean(ARG_RETIAN, false));
            }

            builder.setView(body);

            if (mode == MODE_ADD) {
                builder.setTitle(R.string.dlg_actions_title_add);

                builder.setPositiveButton(R.string.action_add, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActionsViewModel.Action a = new ActionsViewModel.Action();
                        a.name = actionName.getText().toString();
                        a.topic = actionTopic.getText().toString();
                        a.content = actionContent.getText().toString();
                        a.retain = retainCheckBox.isChecked();
                        Activity ac =  getActivity();
                        if (ac instanceof ActionsActivity) {
                            ((ActionsActivity) ac).addAction(a);
                        }
                    }
                });
            } else {
                builder.setTitle(R.string.dlg_actions_title_update);

                builder.setPositiveButton(R.string.action_update, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActionsViewModel.Action a = new ActionsViewModel.Action();
                        a.name = actionName.getText().toString();
                        a.topic = actionTopic.getText().toString();
                        a.content = actionContent.getText().toString();
                        a.prevName = args.getString(ARG_PREV);
                        a.retain = retainCheckBox.isChecked();
                        Activity ac =  getActivity();
                        if (ac instanceof ActionsActivity) {
                            ((ActionsActivity) ac).updateAction(a);
                        }
                    }
                });
            }

            builder.setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });

            AlertDialog dlg = builder.create();
            dlg.setCanceledOnTouchOutside(false);

            return dlg;
        }

        final static String ARG_EDIT_MODE = "ARG_EDIT_MODE";
        final static String ARG_NAME = "ARG_NAME";
        final static String ARG_TOPIC = "ARG_TOPIC";
        final static String ARG_CONTENT = "ARG_CONTENT";
        final static String ARG_PREV = "ARG_PREV";
        final static String ARG_ERROR = "ARG_ERROR";
        final static String ARG_IDX = "ARG_IDX";
        final static String ARG_RETIAN = "ARG_RETIAN";
        final static int MODE_ADD = 0;
        final static int MODE_EDIT = 1;

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean handled;
        switch (item.getItemId()) {
            case android.R.id.home:
                handleBackPressed();
                handled = true;
                break;
            case R.id.menu_refresh:
                refresh();
                handled = true;
                break;
            case R.id.action_add:
                showEditDialog(EditActionDialog.MODE_ADD, null, null, -1);
                handled = true;
                break;
            default:
                handled = super.onOptionsItemSelected(item);
        }
        return handled;
    }

    @Override
    public void onBackPressed() {
        handleBackPressed();
        // super.onBackPressed();
    }

    protected void handleBackPressed() {
        setResult(AppCompatActivity.RESULT_CANCELED); //TODO:
        finish();
    }

    protected void refresh() {
        if (!mViewModel.isRequestActive()) {
            mSwipeRefreshLayout.setRefreshing(true);
            mViewModel.getActions(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_actions, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem m = menu.findItem(R.id.menu_refresh);
        if (m != null) {
            // m.setEnabled(!mViewModel.isRequestActive());
        }

        return super.onPrepareOptionsMenu(menu);
    }

    protected void showErrorMsg(String msg) {
        View v = findViewById(R.id.rView);
        if (v != null) {
            mSnackbar = Snackbar.make(v, msg, Snackbar.LENGTH_INDEFINITE);
            mSnackbar.show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mActivityStarted = false;
    }

    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.activity_actions_action, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            boolean handled = false;
            switch (item.getItemId()) {
                case R.id.action_delete:
                    if (mViewModel.isRequestActive()) {
                        Toast.makeText(getApplicationContext(), R.string.op_in_progress, Toast.LENGTH_LONG).show();
                    } else {
                        ConfirmDeleteDlg dlg = new ConfirmDeleteDlg();
                        Bundle args = new Bundle();
                        args.putStringArrayList(ConfirmDeleteDlg.SEL_ROWS, new ArrayList<String>(mViewModel.selectedActions));
                        dlg.setArguments(args);
                        dlg.show(getSupportFragmentManager(), ConfirmDeleteDlg.class.getSimpleName());
                    }

                    handled = true;
                    break;
                case R.id.action_edit:
                    if (mViewModel.selectedActions.size() != 1) {
                        Toast.makeText(getApplicationContext(), R.string.select_single_topic, Toast.LENGTH_LONG).show();
                    } else {
                        if (mViewModel.isRequestActive()) {
                            Toast.makeText(getApplicationContext(), R.string.op_in_progress, Toast.LENGTH_LONG).show();
                        } else {
                            if (mViewModel.selectedActions.size() == 1 && mViewModel != null) {
                                String t = mViewModel.selectedActions.iterator().next();
                                ActionsRecyclerViewAdapter a = (ActionsRecyclerViewAdapter) mListView.getAdapter();
                                if (a != null) {
                                    ArrayList<ActionsViewModel.Action> list = a.getActions();
                                    if (list != null) {
                                        for(ActionsViewModel.Action ac : list) {
                                            if (ac.name.equals(t)) {
                                                showEditDialog(MODE_EDIT, ac, null, -1);
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    handled = true;
                    break;
            }

            return handled;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            if (mAdapter != null)
                mAdapter.clearSelection();
            mActionMode = null;
        }
    };

    public static class ConfirmDeleteDlg extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Bundle args = getArguments();
            final ArrayList<String> topics = args.getStringArrayList(SEL_ROWS);

            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getActivity());
            builder.setTitle(getString(R.string.dlg_delete_actions_title));
            if (topics.size() == 1) {
                builder.setMessage(getString(R.string.dlg_delete_actions_msg));
            } else {
                builder.setMessage(getString(R.string.dlg_delete_actions_msg_pl));
            }

            builder.setPositiveButton(R.string.action_delete_topics, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Activity a = getActivity();
                    if (a instanceof ActionsActivity) {
                        ((ActionsActivity) a).deleteActions(topics);
                    }
                }
            });

            builder.setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });

            android.app.AlertDialog dlg = builder.create();
            dlg.setCanceledOnTouchOutside(false);

            return dlg;
        }

        public final static String SEL_ROWS = "SEL_ROWS";
    }


    private ActionMode mActionMode;
    private Snackbar mSnackbar;
    private ActionsRecyclerViewAdapter mAdapter;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    ActionsViewModel mViewModel;
    private RecyclerView mListView;
    private boolean mActivityStarted;

    private final static String TAG = ActionsActivity.class.getSimpleName();
}
