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

import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.AsyncTask;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.DialogFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Build;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.radioshuttle.db.AppDatabase;
import de.radioshuttle.db.MqttMessageDao;
import de.radioshuttle.fcm.MessagingService;
import de.radioshuttle.fcm.Notifications;
import de.radioshuttle.mqttpushclient.dash.DashBoardActivity;
import de.radioshuttle.mqttpushclient.dash.ViewState;
import de.radioshuttle.net.AppTrustManager;
import de.radioshuttle.net.Connection;
import de.radioshuttle.net.DeleteToken;
import de.radioshuttle.net.Request;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.utils.FirebaseTokens;
import de.radioshuttle.utils.Utils;

import static de.radioshuttle.mqttpushclient.EditAccountActivity.PARAM_ACCOUNT_JSON;
import static de.radioshuttle.mqttpushclient.MessagesActivity.PARAM_MULTIPLE_PUSHSERVERS;

public class AccountListActivity extends AppCompatActivity implements CertificateErrorDialog.Callback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_list);

        if (savedInstanceState == null) {
            try {
                AppTrustManager.readTrustedCerts(getApplication());
            } catch (Exception e) {
                Log.d(TAG, "Error reading trusted certs (user): ", e);
            }
        }

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

        /* create uuid, if not already exists */
        SharedPreferences isettings = getSharedPreferences(PREFS_INST, Activity.MODE_PRIVATE);
        String uuid = isettings.getString(UUID, null);
        if (uuid == null) {
            SharedPreferences.Editor e = isettings.edit();
            e.putString(UUID, Utils.byteArrayToHex(Utils.randomUUID()));
            e.commit();
        }

        /* UI prefs */
        SharedPreferences uprefs = getSharedPreferences(PREFS_UI, Activity.MODE_PRIVATE);
        mTheme = uprefs.getInt(THEME, 0);
        invalidateOptionsMenu();
        setNightMode();

        mViewModel = ViewModelProviders.of(this).get(AccountViewModel.class);
        boolean accountsChecked = mViewModel.initialized;
        try {
            mViewModel.init(accountsJSON);
            if (!accountsChecked) {
                List<PushAccount> accounts = mViewModel.accountList.getValue();
                if (accounts != null) {
                    for(PushAccount a : accounts) {
                        a.executor = Utils.newSingleThreadPool();
                    }
                }
            }
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
                                boolean requestFinished = mViewModel.isCurrentRequest(request); // last request of this account
                                mAdapter.setData(pushAccounts);
                                Log.d(TAG, "status: " + pushAccount.status + " requestStatus: " + pushAccount.requestStatus
                                        + " request: " + (request instanceof DeleteToken ? "deleteDevice"  : "check"));

                                if (requestFinished) {
                                    mViewModel.confirmResultDelivered(request);

                                    // hide progress, if no requests active anymore
                                    if (!mViewModel.isRequestActive()) {
                                        mSwipeRefreshLayout.setRefreshing(false);
                                    }

                                    if (request instanceof DeleteToken) {
                                        /* success */
                                        DeleteToken dt = (DeleteToken) request;
                                        if (dt.deviceRemoved) {
                                            if (pushAccount.executor != null) {
                                                pushAccount.executor.shutdown();
                                            }
                                            removeAccountData(pushAccount);
                                            return;
                                        }
                                    }
                                }

                                /* handle cerificate exception */
                                if (pushAccount.hasCertifiateException()) {
                                    /* only show dialog if the certificate has not already been denied */
                                    if (!AppTrustManager.isDenied(pushAccount.getCertificateException().chain[0])) {
                                        FragmentManager fm = getSupportFragmentManager();

                                        String DLG_TAG = CertificateErrorDialog.class.getSimpleName() + "_" +
                                                AppTrustManager.getUniqueKey(pushAccount.getCertificateException().chain[0]);

                                        /* check if a dialog is not already showing (for this certificate) */
                                        if (fm.findFragmentByTag(DLG_TAG) == null) {
                                            CertificateErrorDialog dialog = new CertificateErrorDialog();
                                            Bundle args = CertificateErrorDialog.createArgsFromEx(
                                                    pushAccount.getCertificateException(), pushAccount.pushserver);
                                            if (args != null) {
                                                if (request instanceof DeleteToken) {
                                                    args.putString("removeAccount", pushAccount.getKey());
                                                }
                                                dialog.setArguments(args);
                                                dialog.showNow(getSupportFragmentManager(), DLG_TAG);
                                            }
                                        }
                                    }
                                } /* end dialog already showing */
                                pushAccount.setCertificateExeption(null); // mark as "processed"

                                /* handle insecure connection */
                                if (pushAccount.inSecureConnectionAsk) {
                                    if (Connection.mInsecureConnection.get(pushAccount.pushserver) == null) {
                                        FragmentManager fm = getSupportFragmentManager();

                                        String DLG_TAG = InsecureConnectionDialog.class.getSimpleName() + "_" + pushAccount.pushserver;

                                        /* check if a dialog is not already showing (for this host) */
                                        if (fm.findFragmentByTag(DLG_TAG) == null) {
                                            InsecureConnectionDialog dialog = new InsecureConnectionDialog();
                                            Bundle args = InsecureConnectionDialog.createArgsFromEx(pushAccount.pushserver);
                                            if (args != null) {
                                                Log.d(TAG, pushAccount.pushserver + " " + i);
                                                if (request instanceof DeleteToken) {
                                                    args.putString("removeAccount", pushAccount.getKey());
                                                }
                                                dialog.setArguments(args);
                                                dialog.showNow(getSupportFragmentManager(), DLG_TAG);
                                            }
                                        }
                                    }
                                }
                                pushAccount.inSecureConnectionAsk = false; // mark as "processed"
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
                    startMessagesActivity(b, false);
                }
            }
        });
        mListView.setAdapter(mAdapter);
        mViewModel.addNotificationUpdateListener(getApplication());

        boolean startedFromNotificationTray = false;
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
                    startedFromNotificationTray = true;
                    mActivityStarted = true;
                    startMessagesActivity(showMessagesForAcc, true);
                }
            }
            Boolean fcm_on_delete = getIntent().getBooleanExtra(MessagingService.FCM_ON_DELETE, false);
            if (fcm_on_delete) {
                Notifications.cancelOnDeleteWarning(this);
                Log.d(TAG, "notification deletion intent received.");
            }

            checkGooglePlayServices();
        }

        if (!accountsChecked && !startedFromNotificationTray) {
            refresh();
        }

    }

    protected void startMessagesActivity(PushAccount b, boolean notifstart) {

        Class<?> m = MessagesActivity.class;
        if (!notifstart && b != null) {
            int lastState = ViewState.getInstance(getApplication()).getLastState(b.getKey());
            if (lastState == ViewState.VIEW_DASHBOARD) {
                m = DashBoardActivity.class;
            }
        }

        Intent intent = new Intent(AccountListActivity.this, m);
        intent.putExtra(PARAM_MULTIPLE_PUSHSERVERS, mViewModel.hasMultiplePushServers());
        try {
            intent.putExtra(PARAM_ACCOUNT_JSON, b.getJSONObject().toString());
            intent.putExtra(ARG_NOTIFSTART, notifstart);
        } catch(JSONException e) {
            Log.e(TAG, "push account (subscriptions) parse error", e);
        }
        startActivityForResult(intent, RC_MESSAGES);
    }

    protected void removeAccountData(PushAccount account) {
        if (account != null) {
            account = mViewModel.removeBorker(account.getKey());
            if (account != null) {
                try {
                    /* save account data locally without */
                    FirebaseTokens.getInstance(getApplication()).removeAccount(account.getKey());
                    synchronized (Request.ACCOUNTS) {
                        SharedPreferences settings = getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(ACCOUNTS, mViewModel.getAccountsJSON());
                        editor.commit();
                    }
                    ArrayList<PushAccount> pushAccounts = mViewModel.accountList.getValue();
                    boolean found = false;
                    if (pushAccounts != null) {
                        for(PushAccount br : mViewModel.accountList.getValue()) {
                            if (br.getKey().equals(account.getKey())) {
                                found = true;
                            }
                        }
                    }
                    if (!found) {
                        Notifications.deleteMessageCounter(this, account.pushserver, account.getMqttAccountName());

                        @SuppressLint("StaticFieldLeak")
                        AsyncTask<String, Object, Object> t = new AsyncTask<String, Object, Object>() {
                            @Override
                            protected Object doInBackground(String[] objects) {
                                AppDatabase db = AppDatabase.getInstance(getApplication());
                                MqttMessageDao dao = db.mqttMessageDao();
                                long psid = dao.getCode(objects[0]);
                                long accountID = dao.getCode(objects[1]);
                                dao.deleteMessagesForAccount(psid, accountID);

                                /* delete cached dashboard messages */
                                try {
                                    File cachedMessages = new File(getApplication().getFilesDir(), "mc_" + psid + "_" + accountID + ".json");
                                    boolean deleted = cachedMessages.delete();
                                    Log.d(TAG, "Cached dashboard messages deleted for account " + objects[0] + ", " + objects[1] + ": " + deleted);
                                } catch (Exception e) {
                                    Log.e(TAG, "Deletion of cached messages failed: " + e.getMessage());
                                }

                                return null;
                            }

                        };
                        t.executeOnExecutor(Utils.executor, new String[] {account.pushserverID, account.getMqttAccountName()});
                        ViewState.getInstance(getApplication()).removeAccount(account.getKey());
                        // Log.d(TAG, "deleteDevice: account data removed!!");
                    }

                } catch (JSONException e) {
                    Log.e(TAG, "saving deleted account failed", e);
                    Toast.makeText(getApplicationContext(), R.string.error_saving_accounts, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    public void deleteAccount(PushAccount b) {
        if (b != null) {
            ArrayList<PushAccount> pushAccounts = mViewModel.accountList.getValue();
            int cnt = 0;
            if (pushAccounts != null) {
                /* determine if firbase token may be removed */
                for(PushAccount br : mViewModel.accountList.getValue()) {
                    if (Utils.equals(br.pushserverID, b.pushserverID)) {
                        cnt++;
                    }
                }
            }
            mViewModel.deleteAccount(this, cnt == 1,  b);
        }
    }

    /** trigger account deletion */
    public void deleteAccount(int sel) {
        if (mAdapter != null) {
            deleteAccount(mAdapter.getAccount(sel));
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
                                        b.executor = list.get(i).executor;
                                        list.set(i, b);
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    b.executor = Utils.newSingleThreadPool();
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
        } else if (requestCode == RC_MESSAGES) {
            if (data != null) {
                if (data.getBooleanExtra(ARG_NOTIFSTART, false)) {
                    refresh();
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
            case R.id.menu_theme_system:
                if (mTheme != 0) {
                    mTheme = 0;
                    saveTheme();
                    setNightMode();
                }
                return true;
            case R.id.menu_theme_light:
                if (mTheme != 1) {
                    mTheme = 1;
                    saveTheme();
                    setNightMode();
                }
                return true;
            case R.id.menu_theme_dark:
                if (mTheme != 2) {
                    mTheme = 2;
                    saveTheme();
                    setNightMode();
                }
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

    protected void saveTheme() {
        SharedPreferences uprefs = getSharedPreferences(PREFS_UI, Activity.MODE_PRIVATE);
        SharedPreferences.Editor e = uprefs.edit();
        e.putInt(THEME, mTheme);
        e.apply();
        invalidateOptionsMenu();
    }

    protected void setNightMode() {
        if (mTheme == 1) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (mTheme == 2) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else { // mTheme == 0
            if (Build.VERSION.SDK_INT <= 28) {
                // Android 9 and lower: set by battery saver
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);
            } else {
                // Android 9 and up: system default
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }
        }
    }

    protected void refresh() {
        /* if a delete operation is running, refresh is not allowed */
        if (mViewModel.isDeleteRequestActive()) {
            Toast.makeText(getApplicationContext(), R.string.op_in_progress, Toast.LENGTH_LONG).show();
        } else {
            ArrayList<PushAccount> pushAccounts = mViewModel.accountList.getValue();
            if (pushAccounts != null && pushAccounts.size() > 0) { // only allow refresh if an account exists
                if (!mSwipeRefreshLayout.isRefreshing())
                    mSwipeRefreshLayout.setRefreshing(true);
                mViewModel.checkAccounts(this);
            } else if (!mViewModel.isRequestActive() && mSwipeRefreshLayout.isRefreshing()) {
                mSwipeRefreshLayout.setRefreshing(false);
            }
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

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem m = null;
        if (mTheme == 0) {
            m = menu.findItem(R.id.menu_theme_system);
        } else if (mTheme == 1) {
            m = menu.findItem(R.id.menu_theme_light);
        }  else if (mTheme == 2) {
            m = menu.findItem(R.id.menu_theme_dark);
        }
        if (m != null) {
            m.setChecked(true);
        }
        return super.onPrepareOptionsMenu(menu);
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
                case R.id.action_remove_account:
                    if (mViewModel.isRequestActive()) {
                        Toast.makeText(getApplicationContext(), R.string.op_in_progress, Toast.LENGTH_LONG).show();
                    } else {
                        ConfirmDeleteDlg dlg = new ConfirmDeleteDlg();
                        Bundle args = new Bundle();

                        args.putInt(SEL_ROW, mAdapter.getSelectedRow());
                        dlg.setArguments(args);
                        dlg.show(getSupportFragmentManager(), ConfirmDeleteDlg.class.getSimpleName());
                    }
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

    @Override
    public void retry(Bundle args) {
        if (args.containsKey("removeAccount")) {
            String accKey = args.getString("removeAccount");
            for(PushAccount p : mViewModel.accountList.getValue()) {
                if (p.getKey().equals(accKey)) {
                    if (!mViewModel.isRequestActive()) {
                        // Log.d(TAG, "deleteDevice: retry"); //TODO: remove
                        deleteAccount(p);
                    }
                    break;
                }
            }
        } else {
            refresh();
        }
    }

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
    public final static String PREFS_INST = "instance_prefs";
    public final static String PREFS_UI = "ui_prefs";

    public final static String ACCOUNTS = "accounts";
    public final static String UUID = "uuid";
    public final static String THEME = "theme";

    public final static String ARG_MQTT_ACCOUNT = "ARG_MQTT_ACCOUNT";
    public final static String ARG_PUSHSERVER_ID = "ARG_PUSHSERVER_ADDR";

    public final static String ARG_NOTIFSTART = "ARG_NOTIFSTART";

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
    private int mTheme;

}
