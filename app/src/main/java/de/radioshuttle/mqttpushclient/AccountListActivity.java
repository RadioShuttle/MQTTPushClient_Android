/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;

import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.DialogFragment;
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
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;

import de.radioshuttle.db.AppDatabase;
import de.radioshuttle.db.MqttMessageDao;
import de.radioshuttle.fcm.MessagingService;
import de.radioshuttle.fcm.Notifications;
import de.radioshuttle.net.AppTrustManager;
import de.radioshuttle.net.CertException;
import de.radioshuttle.net.Request;
import de.radioshuttle.net.Cmd;

import static de.radioshuttle.mqttpushclient.EditAccountActivity.PARAM_ACCOUNT_JSON;
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

        mListView = findViewById(R.id.accountListView);
        RecyclerView.ItemDecoration itemDecoration =
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        mListView.addItemDecoration(itemDecoration);
        mListView.setItemAnimator(null);
        mListView.setLayoutManager(new LinearLayoutManager(this));

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        String accountsJSON = settings.getString(ACCOUNTS, null);

        mViewModel = ViewModelProviders.of(this).get(AccountViewModel.class);
        boolean accountsChecked = mViewModel.initialized;
        try {
            mViewModel.init(accountsJSON);
        } catch (JSONException e) {
            Log.e(TAG, "Loading accounts failed." , e);
            Toast.makeText(getApplicationContext(), R.string.error_loading_accounts, Toast.LENGTH_LONG).show();
        }

        mViewModel.accountList.observe(this, new Observer<ArrayList<PushAccount>>() {
            @Override
            public void onChanged(@Nullable ArrayList<PushAccount> pushAccounts) {
                mAdapter.setData(pushAccounts);
            }
        });

        //TODO: check observer: multiple accounts somtimes update events of previous record is skipped
        mViewModel.request.observe(this, new Observer<Request>() {
            @Override
            public void onChanged(@Nullable Request request) {
                if (request != null) {
                    ArrayList<PushAccount> pushAccounts = mViewModel.accountList.getValue();
                    if (pushAccounts != null) {
                        for (int i = 0; i < pushAccounts.size(); i++) {
                            PushAccount pushAccount = request.getAccount();
                            if (request.getAccount().getKey().equals(pushAccounts.get(i).getKey())) {
                                pushAccounts.set(i, pushAccount);
                                mAdapter.setData(pushAccounts);
                                if (mViewModel.isCurrentRequest(request)) {
                                    mSwipeRefreshLayout.setRefreshing(false);
                                    mViewModel.confirmResultDelivered();
                                }

                                CertException x = request.getCertificateException();

                                /* handle cerificate exception */
                                Log.d(TAG, request.getAccount().getKey() +  " " +
                                        (x == null ? "null" : x.reason) + " "); //TODO raus
                                if (request.hasCertifiateException()) {
                                    /* only show dialog if the certificate has not already been denied */
                                    if (!AppTrustManager.isDenied(request.getCertificateException().chain[0])) {
                                        FragmentManager fm = getSupportFragmentManager();

                                        String DLG_TAG = CertificateErrorDialog.class.getSimpleName() + "_" +
                                                AppTrustManager.getUniqueKey(request.getCertificateException().chain[0]);

                                        /* check if a dialog is not already showing (for this certificate) */
                                        if (fm.findFragmentByTag(DLG_TAG) == null) {
                                            CertificateErrorDialog dialog = new CertificateErrorDialog();
                                            Bundle args = CertificateErrorDialog.createArgsFromEx(
                                                    request.getCertificateException(), pushAccount.pushserver);
                                            if (args != null) {
                                                dialog.setArguments(args);
                                                dialog.show(getSupportFragmentManager(), DLG_TAG);
                                                Log.d(TAG, "dialog show!!"); //TODO: remove
                                            }
                                        }
                                    }
                                } /* end dialog already showing */
                                request.setCertificateExeption(null); // mark es "processed"
                                /* end handle cerificate exception */

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
                    startMessagesActivity(b);
                }
            }
        });
        mListView.setAdapter(mAdapter);
        mViewModel.addNotificationUpdateListener(getApplication());

        if (!accountsChecked) {
            refresh();
        }

        /* check if started via notification, if found a matching account open messaging activity to show latest mqtt messages */
        if (savedInstanceState == null) {
            String arg_account = getIntent().getStringExtra(ARG_MQTT_ACCOUNT);
            String arg_pushserver = getIntent().getStringExtra(ARG_PUSHSERVER_ID);
            if (arg_account != null) {
                PushAccount showMessagesForAcc = null;
                for (Iterator<PushAccount> it = mViewModel.accountList.getValue().iterator(); it.hasNext(); ) {
                    PushAccount acc = it.next();
                    // Log.d(TAG, acc.getKey() + " " + arg_account);

                    if (acc.getMqttAccountName().equals(arg_account)) {

                        if (Utils.equals(arg_pushserver, acc.pushserverID)) {
                            showMessagesForAcc = acc;
                            String k = showMessagesForAcc.getNotifcationChannelName();
                            Notifications.cancelAll(this, k);
                            Notifications.cancelAll(this, k + ".a");
                        }
                    }
                }
                if (showMessagesForAcc != null) {
                    mActivityStarted = true;
                    startMessagesActivity(showMessagesForAcc);
                }
            }
            Boolean fcm_on_delete = getIntent().getBooleanExtra(MessagingService.FCM_ON_DELETE, false);
            if (fcm_on_delete) {
                Notifications.cancelOnDeleteWarning(this);
                Log.d(TAG, "notification deletion intent received.");
            }

            checkGooglePlayServices();
        }

    }

    protected void updateNoOfNewMsgs() {

    }

    protected void startMessagesActivity(PushAccount b) {
        Intent intent = new Intent(AccountListActivity.this, MessagesActivity.class);
        intent.putExtra(PARAM_MULTIPLE_PUSHSERVERS, mViewModel.hasMultiplePushServers());
        try {
            intent.putExtra(PARAM_ACCOUNT_JSON, b.getJSONObject().toString());
        } catch(JSONException e) {
            Log.e(TAG, "push account (subscriptions) parse error", e);
        }
        startActivityForResult(intent, RC_MESSAGES);
    }

    public void deleteAccount(int sel) {
        if (mAdapter != null) {
            PushAccount b = mAdapter.getAccount(sel);
            if (b != null) {
                b = mViewModel.removeBorker(b.getKey());
                if (b != null) {
                    try {
                        SharedPreferences settings = getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(ACCOUNTS, mViewModel.getAccountsJSON());
                        editor.commit();
                        ArrayList<PushAccount> pushAccounts = mViewModel.accountList.getValue();
                        boolean found = false;
                        if (pushAccounts != null) {
                            for(PushAccount br : mViewModel.accountList.getValue()) {
                                if (br.getKey().equals(b.getKey())) {
                                    found = true;
                                }
                            }
                        }
                        if (!found) {
                            Notifications.deleteMessageCounter(this, b.pushserver, b.getMqttAccountName());

                            @SuppressLint("StaticFieldLeak")
                            AsyncTask<String, Object, Object> t = new AsyncTask<String, Object, Object>() {
                                @Override
                                protected Object doInBackground(String[] objects) {
                                    AppDatabase db = AppDatabase.getInstance(getApplication());
                                    MqttMessageDao dao = db.mqttMessageDao();
                                    long psid = dao.getCode(objects[0]);
                                    long accountID = dao.getCode(objects[1]);
                                    dao.deleteMessagesForAccount(psid, accountID);
                                    return null;
                                }

                            };
                            t.execute(new String[] {b.pushserverID, b.getMqttAccountName()});

                            mViewModel.deleteToken(this, b);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "saving deleted account failed", e);
                        Toast.makeText(getApplicationContext(), R.string.error_saving_accounts, Toast.LENGTH_LONG).show();
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

        if (requestCode == RC_ADD_ACCOUNT || requestCode == RC_EDIT_ACCOUNT) {
            if (resultCode == AppCompatActivity.RESULT_OK) {
                if (data != null) {
                    String s = data.getStringExtra(PARAM_ACCOUNT_JSON);
                    if (s != null) {
                        try {
                            PushAccount b = PushAccount.createAccountFormJSON(new JSONObject(s));
                            ArrayList<PushAccount> list = mViewModel.accountList.getValue();

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
                                mViewModel.accountList.setValue(list);
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
        Intent intent;
        switch (item.getItemId()) {
            case R.id.action_add:
                intent = new Intent(this, EditAccountActivity.class);
                // intent.putExtra(LoginActivity.EMAIL, mail);
                intent.putExtra(EditAccountActivity.MODE, EditAccountActivity.MODE_ADD);
                if (!mActivityStarted) {
                    mActivityStarted = true;
                    startActivityForResult(intent, RC_ADD_ACCOUNT);
                }
                return true;
            case R.id.menu_privacy:
                if (!mActivityStarted) {
                    mActivityStarted = true;
                    intent = new Intent(this, PrivacyActivity.class);
                    startActivityForResult(intent, RC_PRIVACY);
                }
                return true;

            case R.id.menu_refresh:
                refresh();
                return true;
            case R.id.menu_about:
                intent = new Intent(this, AboutActivity.class);
                if (!mActivityStarted) {
                    mActivityStarted = true;
                    startActivityForResult(intent, RC_ABOUT);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected void refresh() {
        ArrayList<PushAccount> pushAccounts = mViewModel.accountList.getValue();
        if (pushAccounts != null && pushAccounts.size() > 0) { // only allow refresh if an account exists
            if (!mSwipeRefreshLayout.isRefreshing())
                mSwipeRefreshLayout.setRefreshing(true);
            mViewModel.checkAccounts(this);
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

    private boolean checkGooglePlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        final int status = apiAvailability.isGooglePlayServicesAvailable(this);
        if (status != ConnectionResult.SUCCESS) {
            Log.e(TAG, apiAvailability.getErrorString(status));

            // ask user to update google play services.
            if (apiAvailability.isUserResolvableError(status)) {
                Dialog dialog = apiAvailability.getErrorDialog(this, status, RC_GOOGLE_PLAY_SERVICES);
                dialog.show();
            } else {
                View rootView = findViewById(R.id.root_view);
                if (rootView != null) {
                    Snackbar.make(rootView, apiAvailability.getErrorString(status), Snackbar.LENGTH_INDEFINITE).show();
                }
            }
            return false;
        } else {
            // google play services is updated.
            return true;
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
                        PushAccount b = mAdapter.getAccount(row);
                        if (b != null) {
                            try {
                                intent1.putExtra(PARAM_ACCOUNT_JSON, b.getJSONObject().toString());
                                intent1.putExtra(PARAM_MULTIPLE_PUSHSERVERS, mViewModel.hasMultiplePushServers());
                                if (!mActivityStarted) {
                                    mActivityStarted = false;
                                    startActivityForResult(intent1, RC_EDIT_ACCOUNT);
                                }
                            } catch(JSONException e) {
                                Log.e(TAG, "edit server parse error", e);
                            }
                        }
                    }
                    handled = true;
                    break;
                case R.id.action_remove_account:
                    ConfirmDeleteDlg dlg = new ConfirmDeleteDlg();
                    Bundle args = new Bundle();

                    args.putInt(SEL_ROW, mAdapter.getSelectedRow());
                    dlg.setArguments(args);
                    dlg.show(getSupportFragmentManager(), ConfirmDeleteDlg.class.getSimpleName());
                    handled = true;
                    break;
                case R.id.action_edit_account:
                    Intent intent = new Intent(AccountListActivity.this, EditAccountActivity.class);
                    intent.putExtra(EditAccountActivity.MODE, EditAccountActivity.MODE_EDIT);
                    if (mAdapter != null) {
                        int row = mAdapter.getSelectedRow();
                        PushAccount b = mAdapter.getAccount(row);
                        if (b != null) {
                            try {
                                intent.putExtra(PARAM_ACCOUNT_JSON, b.getJSONObject().toString());
                                if (!mActivityStarted) {
                                    mActivityStarted = false;
                                    startActivityForResult(intent, RC_EDIT_ACCOUNT);
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
            builder.setTitle(getString(R.string.dlg_remove_account_title));
            builder.setMessage(getString(R.string.dlg_remove_account_msg));

            builder.setPositiveButton(R.string.action_remove_account, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Activity a = getActivity();
                    if (a instanceof AccountListActivity) {
                        ((AccountListActivity) a).deleteAccount(selRow);
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
    public final static String ACCOUNTS = "accounts";

    public final static String ARG_MQTT_ACCOUNT = "ARG_MQTT_ACCOUNT";
    public final static String ARG_PUSHSERVER_ID = "ARG_PUSHSERVER_ADDR";

    /* keys for instance state */
    private final static String SEL_ROW = " SEL_ROW";

    private final static String TAG = AccountListActivity.class.getSimpleName();

    public final static int RC_ADD_ACCOUNT = 1;
    public final static int RC_EDIT_ACCOUNT = 2;
    public final static int RC_MESSAGES = 3;
    public final static int RC_SUBSCRIPTIONS = 4;
    public final static int RC_ACTIONS = 5;
    public final static int RC_ABOUT = 6;
    public final static int RC_PRIVACY = 7;
    public final static int RC_GOOGLE_PLAY_SERVICES = 8;


    private long lastBackPressTime = 0;
    private boolean mActivityStarted;
    private ActionMode mActionMode;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private AccountRecyclerViewAdapter mAdapter;
    private RecyclerView mListView;
    private AccountViewModel mViewModel;

}
