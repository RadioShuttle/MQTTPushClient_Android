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
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.radioshuttle.net.ActionsRequest;
import de.radioshuttle.net.AppTrustManager;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.net.Connection;
import de.radioshuttle.net.Request;
import de.radioshuttle.utils.Utils;

import static de.radioshuttle.mqttpushclient.EditAccountActivity.PARAM_ACCOUNT_JSON;
import static de.radioshuttle.mqttpushclient.MessagesActivity.PARAM_MULTIPLE_PUSHSERVERS;

public class ActionsActivity extends AppCompatActivity implements CertificateErrorDialog.Callback {

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
                                            int cmd = ((ActionsRequest) request).mCmd;
                                            if (cmd == Cmd.CMD_DEL_ACTIONS) {
                                                args.putStringArrayList("action_del", new ArrayList<>(actionsRequest.mActionListArg));
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
                                            if (cmd == Cmd.CMD_DEL_ACTIONS) {
                                                args.putStringArrayList("action_del", new ArrayList<>(actionsRequest.mActionListArg));
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
            mActionMode = startSupportActionMode(mActionModeCallback);
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

    public void deleteActions(List<String> actions) {
        if (!mViewModel.isRequestActive()) {
            mSwipeRefreshLayout.setRefreshing(true);
            mViewModel.deleteActions(this, actions);
        }
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
                showEditActivity(EditActionActivity.MODE_ADD, null);
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
            mViewModel.getActions(this, false, false);
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
                                                showEditActivity(EditActionActivity.MODE_EDIT, ac);
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

    @Override
    public void retry(Bundle args) {
        if (args != null) {
            int cmd = args.getInt("cmd", 0);
            if (cmd == 0) { // Cmd.CMD_GET_ACTIONS
                refresh();
            } else if (cmd == Cmd.CMD_DEL_ACTIONS) {
                if (!mViewModel.isRequestActive()) {
                    mSwipeRefreshLayout.setRefreshing(true);

                    mViewModel.deleteActions(getApplicationContext(), args.getStringArrayList("action_del"));
                }

            }
        }
    }

    protected void showEditActivity(int mode, ActionsViewModel.Action a) {
        if (mViewModel.isRequestActive()) {
            Toast.makeText(getApplicationContext(), R.string.op_in_progress, Toast.LENGTH_LONG).show();
        } else {
            if (!mActivityStarted) {
                mActivityStarted = true;
                Bundle activityArgs = getIntent().getExtras();
                if (activityArgs == null || activityArgs.getString(PARAM_ACCOUNT_JSON) == null) {
                    return;
                }
                Bundle args = new Bundle();
                args.putString(PARAM_ACCOUNT_JSON, activityArgs.getString(PARAM_ACCOUNT_JSON));
                args.putInt(EditActionActivity.ARG_EDIT_MODE, mode);
                if (a != null) {
                    args.putString(EditActionActivity.ARG_NAME, a.name);
                    args.putString(EditActionActivity.ARG_TOPIC, a.topic);
                    args.putString(EditActionActivity.ARG_CONTENT, a.content);
                    args.putBoolean(EditActionActivity.ARG_RETAIN, a.retain);
                }
                Intent intent = new Intent(this, EditActionActivity.class);
                intent.putExtras(args);
                startActivityForResult(intent, 0);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mActivityStarted = false;
        if (resultCode == AppCompatActivity.RESULT_OK && data != null) {
            if (!mViewModel.isRequestActive()) {
                try {
                    String resultActions = data.getStringExtra(EditActionActivity.RESULT_ACTIONS);
                    if (Utils.isEmpty(resultActions)) {
                        return;
                    }
                    JSONArray resultArr = new JSONArray(resultActions);
                    ArrayList<ActionsViewModel.Action> actionList = new ArrayList<>();
                    ActionsViewModel.Action e;
                    JSONObject jt;
                    for(int i = 0; i < resultArr.length(); i++) {
                        e = new ActionsViewModel.Action();
                        jt = resultArr.getJSONObject(i);
                        e.name = jt.optString("actionname");
                        e.prevName = jt.optString("prev_actionname");
                        e.topic = jt.optString("topic");
                        e.content = jt.optString("content");
                        e.retain = jt.optBoolean("retain");
                        actionList.add(e);
                    }
                    Collections.sort(actionList, new ActionsViewModel.ActionComparator());

                    ActionsRequest request = (ActionsRequest) mViewModel.actionsRequest.getValue();
                    if (request == null) {
                        request = new ActionsRequest(getApplication(), mViewModel.pushAccount, mViewModel.actionsRequest);
                    }
                    request.mActions = actionList;
                    mViewModel.actionsRequest.setValue(request);

                } catch(Exception e) {
                    Log.d(TAG, "onActivityResult(): error: ", e);
                }
            }
        }
    }


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
