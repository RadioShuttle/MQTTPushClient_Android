/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;

import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.DialogFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AppCompatActivity;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;

import de.radioshuttle.net.AppTrustManager;
import de.radioshuttle.net.Connection;
import de.radioshuttle.net.Request;
import de.radioshuttle.net.Cmd;

import static de.radioshuttle.mqttpushclient.AccountListActivity.RC_ACTIONS;
import static de.radioshuttle.mqttpushclient.AccountListActivity.RC_SUBSCRIPTIONS;

public class EditAccountActivity extends AppCompatActivity implements CertificateErrorDialog.Callback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);

        mPushNotificationServer = findViewById(R.id.push_notification_server);
        mMQTTHost = findViewById(R.id.mqtt_host);
        mMQTTPort = findViewById(R.id.mqtt_port);
        mMQTTSSL = findViewById(R.id.ssl);
        mUser = findViewById(R.id.user);;
        mPassword = findViewById(R.id.password);
        mSaveButton = findViewById(R.id.save_button);
        mTopicsButton = findViewById(R.id.topics_button);
        mTopicsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTopicsActivity();
            }
        });
        mActionsButton = findViewById(R.id.actions_button);
        mActionsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openActionsActivity();
            }
        });

        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                save();
            }
        });

        SharedPreferences settings = getSharedPreferences(AccountListActivity.PREFS_NAME, Activity.MODE_PRIVATE);
        String accountsJSON = settings.getString(AccountListActivity.ACCOUNTS, null);

        mViewModel = ViewModelProviders.of(this).get(AccountViewModel.class);
        try {
            mViewModel.init(accountsJSON);
        } catch (JSONException e) {
            Log.d(TAG, accountsJSON);
            Log.e(TAG, "Loading accounts failed." , e);
            Toast.makeText(getApplicationContext(), R.string.error_loading_accounts, Toast.LENGTH_LONG).show();
        }

        mViewModel.request.observe(this, new Observer<Request>() {
            @Override
            public void onChanged(@Nullable Request request) {
                if (request != null) {
                    PushAccount b = request.getAccount();
                    if (b.status == 1) {
                        mSwipeRefreshLayout.setRefreshing(true);
                    } else {
                        if (mViewModel.isCurrentRequest(request)) {
                            mSwipeRefreshLayout.setRefreshing(false);
                            mViewModel.confirmResultDelivered();
                            setUIEnabled(true, true);

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
                                            dialog.show(fm, DLG_TAG);
                                        }
                                    }
                                }
                            }
                            b.inSecureConnectionAsk = false; // mark as "processed"

                        }

                        if (b.requestStatus != Cmd.RC_OK) {
                            String t = (b.requestErrorTxt == null ? "" : b.requestErrorTxt);
                            if (b.requestStatus == Cmd.RC_MQTT_ERROR || (b.requestStatus == Cmd.RC_NOT_AUTHORIZED && b.requestErrorCode != 0)) {
                                t = EditAccountActivity.this.getString(R.string.errormsg_mqtt_prefix) + " " + t;
                            }
                            showErrorMsg(t);
                        } else {
                            mPushAccount = b;
                            saveLocal(b);
                        }
                    }
                }
            }
        });

        Bundle args = getIntent().getExtras();
        if (savedInstanceState == null) {
            mMode = (args != null ? args.getInt(MODE, MODE_ADD) : MODE_ADD);
        } else {
            mMode = savedInstanceState.getInt(MODE, MODE_ADD);
            mSavedAccountJson = savedInstanceState.getString(SAVED_ACCOUNT_JSON);
        }

        if (mMode == MODE_EDIT) {

            setTitle(R.string.title_edit_account);

            String json;
            if (savedInstanceState == null) {
                json = args.getString(PARAM_ACCOUNT_JSON);
            } else {
                json = savedInstanceState.getString(PARAM_ACCOUNT_JSON);
            }

            try {

                mPushAccount = PushAccount.createAccountFormJSON(new JSONObject(json));
                mPushNotificationServer.setText(mPushAccount.pushserver);
                URI u = new URI(mPushAccount.uri);
                mMQTTHost.setText(u.getHost());
                if (u.getPort() != -1)
                    mMQTTPort.setText(String.valueOf(u.getPort()));
                mMQTTSSL.setChecked(u.getScheme().toLowerCase().equals("ssl"));
                mUser.setText(mPushAccount.user);
                mPassword.setText(new String(mPushAccount.password));

            } catch (Exception e) {
                Log.e(TAG, "parse error", e);
            }
        } else { // MODE == MODE_ADD
            setTitle(R.string.title_add_account);
            if (savedInstanceState == null) {
                mPushNotificationServer.setText("push.radioshuttle.de");
                mMQTTHost.setText("mqtt.arduino-hannover.de");
                mMQTTPort.setText("1883");
                mUser.requestFocus();
            }
        }

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setEnabled(false);
        mSwipeRefreshLayout.setRefreshing(mViewModel.isRequestActive());
        setUIEnabled(!mViewModel.isRequestActive(), !mViewModel.isRequestActive());

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    protected void showErrorMsg(String msg) {
        View v = EditAccountActivity.this.findViewById(R.id.rView);
        if (v != null) {
            mSnackbar = Snackbar.make(v, msg, Snackbar.LENGTH_INDEFINITE);
            mSnackbar.show();
        }
    }

    protected void setUIEnabled(boolean fieldsEnabled, boolean buttonsEnabled) {
        boolean addMode = mMode == MODE_ADD;
        mPushNotificationServer.setEnabled(fieldsEnabled && addMode);
        mMQTTHost.setEnabled(fieldsEnabled && addMode);
        mMQTTSSL.setEnabled(fieldsEnabled && addMode);
        mMQTTPort.setEnabled(fieldsEnabled && addMode);
        mUser.setEnabled(fieldsEnabled && addMode);
        mPassword.setEnabled(fieldsEnabled);
        mSaveButton.setEnabled(buttonsEnabled);
        mTopicsButton.setEnabled(!addMode);
        mActionsButton.setEnabled(!addMode);
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_edit_account, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem m = menu.findItem(R.id.action_save);
        if (m != null) {
            m.setEnabled(!mViewModel.isRequestActive());
        }
        m = menu.findItem(R.id.menu_topics);
        if (m != null) {
            m.setEnabled(!mViewModel.isRequestActive() && mMode == MODE_EDIT);
        }
        m = menu.findItem(R.id.menu_actions);
        if (m != null) {
            m.setEnabled(!mViewModel.isRequestActive() && mMode == MODE_EDIT);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mPushAccount != null) {
            try {
                outState.putString("PARAM_ACCOUNT_JSON", mPushAccount.getJSONObject().toString());
            } catch (JSONException e) {
                Log.e(TAG, "parse error", e);
            }
        }
        outState.putInt(MODE, mMode);
        if (mSavedAccountJson != null) {
            outState.putString(SAVED_ACCOUNT_JSON , mSavedAccountJson);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home :
                handleBackPressed();
                return true;
            case R.id.action_save :
                save();
                return true;
            case R.id.menu_topics :
                openTopicsActivity();
                return true;
            case R.id.menu_actions :
                openActionsActivity();
                return true;
            default:
                return super.onOptionsItemSelected(item);
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
            dlg.show(getSupportFragmentManager(), QuitWithoutSaveDlg.class.getSimpleName());
        } else {
            if (mSavedAccountJson != null) {
                Intent data = new Intent();
                data.putExtra(PARAM_ACCOUNT_JSON, mSavedAccountJson);
                setResult(AppCompatActivity.RESULT_OK, data);
                finish();
            }
            setResult(AppCompatActivity.RESULT_CANCELED);
            finish();
        }
    }

    protected PushAccount getUserInput() {
        PushAccount b = new PushAccount();
        b.pushserver = mPushNotificationServer.getText().toString().trim();
        String host = mMQTTHost.getText().toString().trim();
        String port = mMQTTPort.getText().toString().trim();
        boolean ssl = mMQTTSSL.isChecked();
        if (ssl) {
            b.uri = "ssl://";
        } else {
            b.uri = "tcp://";
        }
        b.uri += host;
        if (port.length() > 0) {
            b.uri += ":" + port;
        } else {
            b.uri += ":" + (ssl ? 8883 : 1883);
        }
        b.user = mUser.getText().toString().trim();
        b.password = mPassword.getText().toString().toCharArray();

        return b;
    }

    protected boolean hasDataChanged() {
        PushAccount o;
        if (mPushAccount == null) {
            o = new PushAccount();
        } else {
            o = mPushAccount;
        }
        PushAccount n = getUserInput();
        if (mMQTTHost.getText().toString().trim().isEmpty() && mMQTTPort.getText().toString().trim().isEmpty() && !mMQTTSSL.isChecked()) {
            n.uri = null;
        }

        boolean eq =
                Utils.equals(o.pushserver, n.pushserver) &&
                        Utils.equals(o.uri, n.uri) &&
                        Utils.equals(o.user, n.user);
        if (eq) {
            if (o.password == null) {
                o.password = new char[0];
            }
            if (n.password == null) {
                n.password = new char[0];
            }
            eq = eq && Arrays.equals(o.password, n.password);
        }

        return !eq;
    }

    protected void save() {
        if (checkData()) {
            if (hasDataChanged()) {
                setUIEnabled(false, false);
                mViewModel.saveAccount(this, getUserInput());
                if (mSnackbar != null) {
                    mSnackbar.dismiss();
                }
            } else {
                Toast.makeText(getApplicationContext(), R.string.error_data_unmodified, Toast.LENGTH_LONG).show();
            }
        }
    }

    protected void openTopicsActivity() {
        if (!mActivityStarted) {
            mActivityStarted = true;
            Bundle args = getIntent().getExtras();
            Intent intent = new Intent(EditAccountActivity.this, TopicsActivity.class);
            String acc;
            if (mSavedAccountJson == null) {
                acc = args.getString(PARAM_ACCOUNT_JSON);
            } else {
                acc = mSavedAccountJson;
            }
            intent.putExtra(PARAM_ACCOUNT_JSON, acc);
            intent.putExtra(MessagesActivity.PARAM_MULTIPLE_PUSHSERVERS, mViewModel.hasMultiplePushServers());
            startActivityForResult(intent, RC_SUBSCRIPTIONS);
        }
    }

    protected void openActionsActivity() {
        if (!mActivityStarted) {
            mActivityStarted = true;
            Bundle args = getIntent().getExtras();
            Intent intent = new Intent(EditAccountActivity.this, ActionsActivity.class);
            String acc;
            if (mSavedAccountJson == null) {
                acc = args.getString(PARAM_ACCOUNT_JSON);
            } else {
                acc = mSavedAccountJson;
            }
            intent.putExtra(PARAM_ACCOUNT_JSON, acc);
            intent.putExtra(MessagesActivity.PARAM_MULTIPLE_PUSHSERVERS, mViewModel.hasMultiplePushServers());
            startActivityForResult(intent, RC_ACTIONS);
        }
    }

    protected void saveLocal(PushAccount checkedPushAccount) {
        ArrayList<PushAccount> pushAccount = mViewModel.accountList.getValue();
        if (pushAccount == null)
            pushAccount = new ArrayList<>();

        boolean found = false;
        for(int i = 0; i < pushAccount.size(); i++) {
            PushAccount b = pushAccount.get(i);
            if (b.getKey().equals(checkedPushAccount.getKey())) {
                PushAccount n = getUserInput();
                b.pushserver = n.pushserver;
                b.pushserverID = checkedPushAccount.pushserverID; // received from server
                b.uri = n.uri;
                b.user = n.user;
                b.password = n.password;
                b.topics = n.topics;
                found = true;
                break;
            }
        }
        if (!found) {
            pushAccount.add((checkedPushAccount));
        }

        try {
            mViewModel.accountList.setValue(pushAccount);
            SharedPreferences settings = getSharedPreferences(AccountListActivity.PREFS_NAME, Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(AccountListActivity.ACCOUNTS, mViewModel.getAccountsJSON());
            editor.commit();
            Toast.makeText(getApplicationContext(), R.string.info_data_saved, Toast.LENGTH_LONG).show();

            if (mMode == MODE_ADD) {
                mMode = MODE_EDIT;
                setUIEnabled(!mViewModel.isRequestActive(), !mViewModel.isRequestActive());
            }
            mSavedAccountJson = mPushAccount.getJSONObject().toString();


        } catch (JSONException e) {
            Log.e(TAG, "saving account list failed", e);
            showErrorMsg(getString(R.string.error_saving_accounts));
        }
    }


    protected boolean checkData() {
        boolean ok = true;
        if (Utils.isEmpty(mPushNotificationServer.getText().toString())) {
            mPushNotificationServer.setError(getString(R.string.error_empty_field));
            ok = false;
        }
        if (Utils.isEmpty(mMQTTHost.getText().toString())) {
            mMQTTHost.setError(getString(R.string.error_empty_field));
            ok = false;
        }

        return ok;
    }

    @Override
    public void retry(Bundle args) {
        save();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mActivityStarted = false;
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

    /* keys instance state */
    private final static String LAST_SAVED_ACC = "LAST_SAVED_ACC";


    public AccountViewModel mViewModel;
    public PushAccount mPushAccount;
    public TextView mPushNotificationServer;
    public TextView mMQTTHost;
    public TextView mMQTTPort;
    public CheckBox mMQTTSSL;
    public TextView mUser;
    public TextView mPassword;
    public Button mSaveButton;
    public Button mTopicsButton;
    public Button mActionsButton;

    public int mMode;

    public final static String SAVED_ACCOUNT_JSON = "SAVED_ACCOUNT_JSON";
    private String mSavedAccountJson;
    private boolean mActivityStarted;

    private Snackbar mSnackbar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    public final static String MODE = "MODE";
    public final static int MODE_ADD = 1;
    public final static int MODE_EDIT = 2;
    public final static String PARAM_ACCOUNT_JSON = "PARAM_ACCOUNT_JSON";
    private final static String TAG = EditAccountActivity.class.getSimpleName();
}
