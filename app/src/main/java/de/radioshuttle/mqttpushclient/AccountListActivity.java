/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import de.radioshuttle.net.Request;
import de.radioshuttle.net.Cmd;

import static de.radioshuttle.mqttpushclient.EditAccountActivity.PARAM_BROKER_JSON;
import static de.radioshuttle.mqttpushclient.MessagesActivity.PARAM_MULTIPLE_PUSHSERVERS;

public class AccountListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_list);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setEnabled(true);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh();
            }
        });

        mListView = findViewById(R.id.brokerListView);
        RecyclerView.ItemDecoration itemDecoration =
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        mListView.addItemDecoration(itemDecoration);
        mListView.setItemAnimator(null);
        mListView.setLayoutManager(new LinearLayoutManager(this));

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        String brokersJSON = settings.getString(BROKERS, null);
        // String brokesStateJSON = (savedInstanceState != null ? savedInstanceState.getString(BROKERS_STATE) : null);

        mViewModel = ViewModelProviders.of(this).get(AccountViewModel.class);
        boolean brokersChecked = mViewModel.initialized;
        try {
            mViewModel.init(brokersJSON);
        } catch (JSONException e) {
            Log.e(TAG, "Loading brokers failed." , e);
            Toast.makeText(getApplicationContext(), R.string.error_loading_brokers, Toast.LENGTH_LONG).show();
        }

        mViewModel.brokerList.observe(this, new Observer<ArrayList<PushAccount>>() {
            @Override
            public void onChanged(@Nullable ArrayList<PushAccount> pushAccounts) {
                mAdapter.setData(pushAccounts);
            }
        });

        mViewModel.requestBroker.observe(this, new Observer<Request>() {
            @Override
            public void onChanged(@Nullable Request request) {
                if (request != null) {
                    ArrayList<PushAccount> pushAccounts = mViewModel.brokerList.getValue();
                    if (pushAccounts != null) {
                        for (int i = 0; i < pushAccounts.size(); i++) {
                            PushAccount pushAccount = request.getBroker();
                            if (request.getBroker().getKey().equals(pushAccounts.get(i).getKey())) {
                                pushAccounts.set(i, pushAccount);
                                mAdapter.setData(pushAccounts);
                                if (mViewModel.isCurrentRequest(request)) {
                                    mSwipeRefreshLayout.setRefreshing(false);
                                    mViewModel.confirmResultDelivered();
                                }
                                break;
                            }
                        }
                    }
                }
            }
        });

        if (mViewModel.isRequestActive()) {
            mSwipeRefreshLayout.setRefreshing(true);
        }

        int selectedRow = (savedInstanceState == null ? -1 : savedInstanceState.getInt(SEL_ROW));
        if (selectedRow != -1) {
            mActionMode = startSupportActionMode(mActionModeCallback);
        }

        mAdapter = new AccountRecyclerViewAdapter(this, selectedRow, new AccountRecyclerViewAdapter.RowSelectionListener() {
            @Override
            public void onItemSelected(int oldPos, int newPos) {
                if (oldPos == -1) {
                    mActionMode = startSupportActionMode(mActionModeCallback);
                }
            }

            @Override
            public void onItemDeselected() {
                if (mActionMode != null)
                    mActionMode.finish();
            }

            @Override
            public void onItemClicked(PushAccount b) {
                if (!mActivityStarted && b != null) {
                    mActivityStarted = true;
                    Intent intent = new Intent(AccountListActivity.this, MessagesActivity.class);
                    intent.putExtra(PARAM_MULTIPLE_PUSHSERVERS, mViewModel.hasMultiplePushServers());
                    try {
                        intent.putExtra(PARAM_BROKER_JSON, b.getJSONObject().toString());
                    } catch(JSONException e) {
                        Log.e(TAG, "broker (subscriptions) parse error", e);
                    }
                    startActivityForResult(intent, RC_MESSAGES);
                }
            }
        });
        mListView.setAdapter(mAdapter);

        if (!brokersChecked) {
            refresh();
        }

    }

    public void deleteBroker(int sel) {
        if (mAdapter != null) {
            PushAccount b = mAdapter.getBroker(sel);
            if (b != null) {
                b = mViewModel.removeBorker(b.getKey());
                if (b != null) {
                    try {
                        SharedPreferences settings = getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(BROKERS, mViewModel.getBrokersJSON());
                        editor.commit();
                        ArrayList<PushAccount> pushAccounts = mViewModel.brokerList.getValue();
                        boolean found = false;
                        if (pushAccounts != null) {
                            for(PushAccount br : mViewModel.brokerList.getValue()) {
                                if (br.getKey().equals(b.getKey())) {
                                    found = true;
                                }
                            }
                        }
                        if (!found) {
                            mViewModel.deleteToken(this, b);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "saving deleted broker failed", e);
                        Toast.makeText(getApplicationContext(), R.string.error_saving_brokers, Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mAdapter != null) {
            outState.putInt(SEL_ROW, mAdapter.getSelectedRow());
        }
        /*
        try {
            outState.putString(BROKERS_STATE, mViewModel.getBrokersStateJSON());
        } catch (JSONException e) {
            Log.e(TAG, "onSaveInstance broker state", e);
        }
        */
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_account, menu);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mActivityStarted = false;

        // mBroker
        if (requestCode == RC_ADD_BROKER || requestCode == RC_EDIT_BROKER) {
            if (resultCode == AppCompatActivity.RESULT_OK) {
                if (data != null) {
                    String s = data.getStringExtra(PARAM_BROKER_JSON);
                    if (s != null) {
                        try {
                            PushAccount b = PushAccount.createBrokerFormJSON(new JSONObject(s));
                            ArrayList<PushAccount> list = mViewModel.brokerList.getValue();

                            if (list != null) {
                                b.requestStatus = Cmd.RC_OK;
                                b.requestErrorTxt = getString(R.string.status_ok);
                                boolean found = false;
                                for (int i = 0; i < list.size(); i++) {
                                    if (list.get(i).getKey().equals(b.getKey())) {
                                        list.set(i, b);
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    list.add(b);
                                }
                                mViewModel.brokerList.setValue(list);
                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_add:
                Intent intent = new Intent(this, EditAccountActivity.class);
                // intent.putExtra(LoginActivity.EMAIL, mail);
                intent.putExtra(EditAccountActivity.MODE, EditAccountActivity.MODE_ADD);
                if (!mActivityStarted) {
                    mActivityStarted = true;
                    startActivityForResult(intent, RC_ADD_BROKER);
                }
                return true;
            case R.id.menu_refresh:
                refresh();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected void refresh() {
        ArrayList<PushAccount> pushAccounts = mViewModel.brokerList.getValue();
        if (pushAccounts != null && pushAccounts.size() > 0) { // only allow refresh if a broker exists
            if (!mSwipeRefreshLayout.isRefreshing())
                mSwipeRefreshLayout.setRefreshing(true);
            mViewModel.checkBrokers(this);
        } else if (!mViewModel.isRequestActive() && mSwipeRefreshLayout.isRefreshing()) {
            mSwipeRefreshLayout.setRefreshing(false);
        }
    }

    @Override
    public void onBackPressed() {
        int count = getSupportFragmentManager().getBackStackEntryCount();
        if (lastBackPressTime < System.currentTimeMillis() - 3000 && count == 0) {
            Toast.makeText(this, getString(R.string.warning_back_pressed), Toast.LENGTH_SHORT).show();
            lastBackPressTime = System.currentTimeMillis();
        } else {
            super.onBackPressed();
        }

    }

    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.activity_account_action, menu);
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
                case R.id.action_subscriptions:
                    Intent intent1 = new Intent(AccountListActivity.this, TopicsActivity.class);
                    if (mAdapter != null) {
                        int row = mAdapter.getSelectedRow();
                        PushAccount b = mAdapter.getBroker(row);
                        if (b != null) {
                            try {
                                intent1.putExtra(PARAM_BROKER_JSON, b.getJSONObject().toString());
                                intent1.putExtra(PARAM_MULTIPLE_PUSHSERVERS, mViewModel.hasMultiplePushServers());
                                if (!mActivityStarted) {
                                    mActivityStarted = false;
                                    startActivityForResult(intent1, RC_EDIT_BROKER);
                                }
                            } catch(JSONException e) {
                                Log.e(TAG, "edit server parse error", e);
                            }
                        }
                    }
                    handled = true;
                    break;
                case R.id.action_remove_broker:
                    ConfirmDeleteDlg dlg = new ConfirmDeleteDlg();
                    Bundle args = new Bundle();

                    args.putInt(SEL_ROW, mAdapter.getSelectedRow());
                    dlg.setArguments(args);
                    dlg.show(getSupportFragmentManager(), ConfirmDeleteDlg.class.getSimpleName());
                    handled = true;
                    break;
                case R.id.action_edit_broker:
                    Intent intent = new Intent(AccountListActivity.this, EditAccountActivity.class);
                    intent.putExtra(EditAccountActivity.MODE, EditAccountActivity.MODE_EDIT);
                    if (mAdapter != null) {
                        int row = mAdapter.getSelectedRow();
                        PushAccount b = mAdapter.getBroker(row);
                        if (b != null) {
                            try {
                                intent.putExtra(PARAM_BROKER_JSON, b.getJSONObject().toString());
                                if (!mActivityStarted) {
                                    mActivityStarted = false;
                                    startActivityForResult(intent, RC_EDIT_BROKER);
                                }
                            } catch(JSONException e) {
                                Log.e(TAG, "edit server parse error", e);
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
            final int selRow = args.getInt(SEL_ROW, 0);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getString(R.string.dlg_remove_broker_title));
            builder.setMessage(getString(R.string.dlg_remove_broker_msg));

            builder.setPositiveButton(R.string.action_remove_broker, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Activity a = getActivity();
                    if (a instanceof AccountListActivity) {
                        ((AccountListActivity) a).deleteBroker(selRow);
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

    /* preferneces */
    public final static String PREFS_NAME = "mqttpushclient_prefs";
    public final static String BROKERS = "brokers";

    /* keys for instance state */
    private final static String SEL_ROW = " SEL_ROW";

    private final static String TAG = AccountListActivity.class.getSimpleName();

    public final static int RC_ADD_BROKER = 1;
    public final static int RC_EDIT_BROKER = 2;
    public final static int RC_MESSAGES = 3;
    public final static int RC_SUBSCRIPTIONS = 4;

    private long lastBackPressTime = 0;
    private boolean mActivityStarted;
    private ActionMode mActionMode;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private AccountRecyclerViewAdapter mAdapter;
    private RecyclerView mListView;
    private AccountViewModel mViewModel;

}
