/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient;

import android.app.AlertDialog;

import androidx.core.view.MenuCompat;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;

import de.radioshuttle.fcm.Notifications;
import de.radioshuttle.mqttpushclient.dash.DashBoardActivity;
import de.radioshuttle.mqttpushclient.dash.ViewState;
import de.radioshuttle.net.ActionsRequest;
import de.radioshuttle.net.AppTrustManager;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.net.Connection;
import de.radioshuttle.net.Request;

import static de.radioshuttle.mqttpushclient.EditAccountActivity.PARAM_ACCOUNT_JSON;

public class MessagesActivity extends AppCompatActivity implements CertificateErrorDialog.Callback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);
        setTitle(getString(R.string.title_messages));

        Bundle args = getIntent().getExtras();
        String json = args.getString(PARAM_ACCOUNT_JSON);
        boolean hastMultipleServer = args.getBoolean(PARAM_MULTIPLE_PUSHSERVERS);
        mListView = findViewById(R.id.messagesListView);
        try {
            PushAccount b = PushAccount.createAccountFormJSON(new JSONObject(json));
            if (savedInstanceState == null) {
                ViewState.getInstance(getApplication()).setLastState(b.getKey(), ViewState.VIEW_MESSAGES);
            }
            TextView server = findViewById(R.id.push_notification_server);
            TextView key = findViewById(R.id.account_display_name);
            server.setText(b.pushserver);
            key.setText(b.getDisplayName());
            if (!hastMultipleServer) {
                server.setVisibility(View.GONE);
            }

            mViewModel = new ViewModelProvider(
                    this, new MessagesViewModel.Factory(b, getApplication(), null))
                    .get(MessagesViewModel.class);
            if (mViewModel.pushAccount == null)
                mViewModel.pushAccount = b;

            mActionsViewModel = new ViewModelProvider(this).get(ActionsViewModel.class);
            boolean actionsLoaded = mActionsViewModel.initialized;
            mActionsViewModel.init(json);

            boolean savedState = false; // currently state is not saved
            mFilterVisible = (savedInstanceState != null ? savedInstanceState.getBoolean(FILTER_VISIBLE) : savedState);
            if (mFilterVisible) {
                showFilterView();
            } else {
                hideFilterView();
            }
            View vb = findViewById(R.id.filterCloseButton);

            vb.setOnClickListener((v) -> closeFilterView());
            EditText filterEditText = findViewById(R.id.filterEditText);
            filterEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    mViewModel.onFilterUpdated(s.toString());
                }
            });


            final MessagesPagedListAdapter adapter = new MessagesPagedListAdapter(this);
            mListView.setAdapter(adapter);
            mAdapterObserver = new RecyclerView.AdapterDataObserver() {
                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    // Log.d(TAG, "item inserted: " + positionStart + " cnt: " + itemCount);
                    if (positionStart == 0) {
                        int pos = ((LinearLayoutManager) mListView.getLayoutManager()).findFirstCompletelyVisibleItemPosition();
                        if (pos >= 0) {
                            MessagesPagedListAdapter a = (MessagesPagedListAdapter) mListView.getAdapter();
                            if (a != null) {
                                mListView.scrollToPosition(0);
                            }
                        }
                    }
                }
            };
            adapter.registerAdapterDataObserver(mAdapterObserver);
            mListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        // Log.d(TAG, "scroll state dragging");
                        adapter.clearSelection();
                    }
                }
            });

            mViewModel.messagesPagedList.observe(this, mqttMessages -> adapter.submitList(mqttMessages, mViewModel.newItems));

            mActionsViewModel.actionsRequest.observe(this, new Observer<Request>() {
                @Override
                public void onChanged(@Nullable Request request) {
                    if (request != null && request instanceof ActionsRequest) {
                        ActionsRequest actionsRequest = (ActionsRequest) request;
                        PushAccount b = actionsRequest.getAccount();
                        if (b.status == 1) {
                            // special case: configuration change and load in progress -> update view
                        } else {
                            if (mActionsViewModel.isCurrentRequest(request)) {
                                mActionsViewModel.confirmResultDelivered();
                                mSwipeRefreshLayout.setRefreshing(false);
                                invalidateOptionsMenu();
                                View v = findViewById(R.id.noTopicsWarning);
                                if (v != null) {
                                    if (actionsRequest.mHasTopics != null && !actionsRequest.mHasTopics) {
                                        if (v.getVisibility() != View.VISIBLE) {
                                            v.setVisibility(View.VISIBLE);
                                        }
                                    } else {
                                        if (v.getVisibility() != View.GONE) {
                                            v.setVisibility(View.GONE);
                                        }
                                    }
                                }

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
                                                if (cmd == Cmd.CMD_MQTT_PUBLISH) {
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
                                    t = MessagesActivity.this.getString(R.string.errormsg_mqtt_prefix) + " " + t;
                                }
                                showErrorMsg(t);
                            } else {
                                /* filter scripts updated? */
                                if (actionsRequest.hasAccountUpdated()) {
                                    /* mark as processed */
                                    actionsRequest.setAccountUpdated(false);
                                    /* reinit filter scripts */
                                    mViewModel.initJavaScript();
                                    /* rerun filter scripts */
                                    doRefresh();
                                }

                                if (actionsRequest.requestStatus != Cmd.RC_OK) { // getActions() or publish failed
                                    String t = (actionsRequest.requestErrorTxt == null ? "" : actionsRequest.requestErrorTxt);
                                    if (actionsRequest.requestStatus == Cmd.RC_MQTT_ERROR) {
                                        t = MessagesActivity.this.getString(R.string.errormsg_mqtt_prefix) + " " + t;
                                    }
                                    showErrorMsg(t);
                                } else {
                                    if (mSnackbar != null && mSnackbar.isShownOrQueued()) {
                                        mSnackbar.dismiss();
                                    }
                                }
                            }
                        }
                    }
                }
            });

            mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
            mSwipeRefreshLayout.setOnRefreshListener(() -> refreshActions(false));

            if (!actionsLoaded) {
                refreshActions(true);
            } else {
                mSwipeRefreshLayout.setRefreshing(mActionsViewModel.isRequestActive());

            }


        } catch (JSONException e) {
            Log.e(TAG, "parse error", e);
        }

        RecyclerView.ItemDecoration itemDecoration =
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        mListView.addItemDecoration(itemDecoration);
        // mListView.setItemAnimator(null);
        mListView.setLayoutManager(new LinearLayoutManager(this));

        if (savedInstanceState == null) {
            mCurrentDay = System.currentTimeMillis();
        } else {
            mCurrentDay = savedInstanceState.getLong(CURRENT_DAY, 0l);
        }

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_messages, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuCompat.setGroupDividerEnabled(menu, true);
        ActionsRequest r = (ActionsRequest) mActionsViewModel.actionsRequest.getValue();
        if (r != null)
        {
            MenuItem noActions = menu.findItem(R.id.menu_noactions);
            if (noActions != null) {
                if (r.requestStatus == Cmd.RC_OK && (r.mActions == null || r.mActions.size() == 0)) {
                    if (!noActions.isVisible()) {
                        noActions.setVisible(true);
                    }
                } else {
                    if (noActions.isVisible()) {
                        noActions.setVisible(false);
                    }
                }
            }

            if (r.mActions != null) {
                menu.removeGroup(ACTION_ITEM_GROUP_ID);
                MenuItem item;

                int i = 0;
                for(ActionsViewModel.Action a : r.mActions) {
                    item = menu.add(ACTION_ITEM_GROUP_ID, i++,  200+ i, a.name );
                    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                }
            }
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
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getGroupId() == ACTION_ITEM_GROUP_ID) {
            String actionCmd = item.getTitle().toString();
            Log.d(TAG, "action item clicked: " +  actionCmd);

            ActionsRequest r = (ActionsRequest) mActionsViewModel.actionsRequest.getValue();
            if (r != null) {
                for(Iterator<ActionsViewModel.Action> it = r.mActions.iterator(); it.hasNext();) {
                    ActionsViewModel.Action a = it.next();
                    if (a.name.equals(actionCmd)) {
                        mActionsViewModel.publish(getApplicationContext(), a);
                        Toast.makeText(this, getString(R.string.action_cmd_exe, actionCmd), Toast.LENGTH_LONG).show();
                        break;
                    }
                }
            }
            return true;
        }

        switch (item.getItemId()) {
            case android.R.id.home :
                handleBackPressed();
                return true;
            case R.id.menu_delete :
                showDeleteDialog();
                return true;
            case R.id.menu_filter:
                showFilterView();
                return true;
            case R.id.menu_change_view :
                if (!mActivityStarted) {
                    mActivityStarted = true;
                    switchToDashboardActivity();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected void showFilterView() {
        mFilterVisible = true;
        View filter = findViewById(R.id.filterSection);
        if (filter.getVisibility() != View.VISIBLE) {
            filter.setVisibility(View.VISIBLE);
        }
    }

    protected void closeFilterView() {
        EditText te = findViewById(R.id.filterEditText);
        te.setText(null);
        // mViewModel.onFilterUpdated(null);
        hideFilterView();
    }

    protected void hideFilterView() {
        mFilterVisible = false;
        View filter = findViewById(R.id.filterSection);
        if (filter.getVisibility() != View.GONE) {
            filter.setVisibility(View.GONE);
        }

        EditText filterText = (EditText) findViewById(R.id.filterEditText);
        if (filterText != null) {
            filterText.clearFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(filterText.getWindowToken(), 0);
        }
    }

    protected void switchToDashboardActivity() {
        Intent intent = new Intent(this, DashBoardActivity.class);
        Bundle args = getIntent().getExtras();
        String json = args.getString(PARAM_ACCOUNT_JSON);
        boolean notifStart = args.getBoolean(AccountListActivity.ARG_NOTIFSTART, false);
        boolean hastMultipleServer = args.getBoolean(PARAM_MULTIPLE_PUSHSERVERS);
        intent.putExtra(PARAM_MULTIPLE_PUSHSERVERS, hastMultipleServer);
        intent.putExtra(PARAM_ACCOUNT_JSON, json);
        intent.putExtra(AccountListActivity.ARG_NOTIFSTART, notifStart);
        startActivityForResult(intent, AccountListActivity.RC_MESSAGES);
        finish();
    }


    protected void doRefresh() {
        if (mViewModel != null) {
            mViewModel.refresh();
        }
    }

    protected void refreshActions(boolean setRefreshing) {
        if (!mActionsViewModel.isRequestActive()) {
            if (setRefreshing)
                mSwipeRefreshLayout.setRefreshing(true);
            mActionsViewModel.getActions(this, true, true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mListView != null && mAdapterObserver != null) {
            RecyclerView.Adapter a = mListView.getAdapter();
            if (a != null) {
                a.unregisterAdapterDataObserver(mAdapterObserver);
            }
        }
    }

    @Override
    public void onBackPressed() {
        handleBackPressed();
        // super.onBackPressed();
    }

    protected void showDeleteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String title = getString(R.string.dlg_del_messages_title);
        String all = getString(R.string.dlg_item_delete_all);
        String oneDay = getString(R.string.dlg_item_delete_older_one_day);

        builder.setTitle(title);

        final int[] selection = new int[] {1};
        builder.setSingleChoiceItems(new String[]{all, oneDay}, selection[0], new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                selection[0] = item;
            }
        });
        builder.setPositiveButton(getString(R.string.action_delete_msgs), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Long before;
                if (selection[0] == 0) {
                    before = null;
                } else {
                    GregorianCalendar cal = new GregorianCalendar();
                    cal.add(Calendar.DAY_OF_MONTH, -1);
                    before = cal.getTimeInMillis();
                }
                MessagesPagedListAdapter a = (MessagesPagedListAdapter) mListView.getAdapter();
                if (a != null) {
                    a.clearSelection();
                }
                mViewModel.deleteMessages(before);
            }
        });
        builder.setNegativeButton(getString(R.string.action_cancel), null);
        builder.setNeutralButton(getString(R.string.resotre_action), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // get last date
                if (mActionsViewModel != null && mActionsViewModel.pushAccount != null) {
                    Notifications.setLastSyncDate(getApplication(),
                            mActionsViewModel.pushAccount.pushserver, mActionsViewModel.pushAccount.getMqttAccountName(), 0L, 0);

                    refreshActions(true);
                }
            }
        });
        AlertDialog dlg = builder.create();

        dlg.show();

    }

    @Override
    public void retry(Bundle args) {
        if (args != null) {
            int cmd = args.getInt("cmd", 0);
            if (cmd == 0) { // Cmd.CMD_GET_ACTIONS
                refreshActions(true);
            } else if (cmd == Cmd.CMD_MQTT_PUBLISH) {
                ActionsViewModel.Action a = new ActionsViewModel.Action();
                a.name = args.getString("action_name");
                a.prevName = args.getString("action_prevName");
                a.content = args.getString("action_content");
                a.retain = args.getBoolean("action_retain");
                a.topic = args.getString("action_topic");
                mActionsViewModel.publish(getApplicationContext(), a);
                Toast.makeText(this, getString(R.string.action_cmd_exe, a.name), Toast.LENGTH_LONG).show();
            }
        }
    }

    protected void handleBackPressed() {
        String name = mViewModel.pushAccount.getNotifcationChannelName();
        Intent intent = new Intent();
        intent.putExtra(AccountListActivity.ARG_NOTIFSTART, getIntent().getBooleanExtra(AccountListActivity.ARG_NOTIFSTART, false));
        setResult(AppCompatActivity.RESULT_OK, intent);
        finish();
    }

    @Override
    public void onPause() {
        super.onPause();
        String name = mViewModel.pushAccount.getNotifcationChannelName();
        Notifications.cancelAll(this, name); // clear systen notification tray
        Notifications.cancelAll(this, name + ".a"); // clear systen notification tray
        Notifications.resetNewMessageCounter(this, mViewModel.pushAccount.pushserver, mViewModel.pushAccount.getMqttAccountName());
        Log.d(TAG, "onPause() called");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(CURRENT_DAY, mCurrentDay);
        outState.putBoolean(FILTER_VISIBLE, mFilterVisible);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!DateUtils.isToday(mCurrentDay)) {
            mCurrentDay = System.currentTimeMillis();
            if (mListView != null && mListView.getAdapter() != null) {
                mListView.getAdapter().notifyDataSetChanged();
            }
        }
    }

    public final static String PARAM_MULTIPLE_PUSHSERVERS = "PARAM_MULTIPLE_PUSHSERVERS";

    private boolean mActivityStarted;
    private Snackbar mSnackbar;
    private RecyclerView mListView;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView.AdapterDataObserver mAdapterObserver;
    private MessagesViewModel mViewModel;
    private ActionsViewModel mActionsViewModel;
    private boolean mFilterVisible;

    private long mCurrentDay;
    private static String CURRENT_DAY = "CURRENT_DAY";
    private static String FILTER_VISIBLE = "FILTER_VISIBLE";

    private final static int ACTION_ITEM_GROUP_ID = Menu.FIRST + 1;

    private final static String TAG = MessagesActivity.class.getSimpleName();

}
