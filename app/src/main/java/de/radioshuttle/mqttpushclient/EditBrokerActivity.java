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
import android.support.design.widget.Snackbar;
import android.support.v4.app.DialogFragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
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
import java.util.Collections;

import de.radioshuttle.net.BrokerRequest;
import de.radioshuttle.net.Cmd;

public class EditBrokerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_broker);

        mPushNotificationServer = findViewById(R.id.push_notification_server);
        mMQTTHost = findViewById(R.id.mqtt_host);
        mMQTTPort = findViewById(R.id.mqtt_port);
        mMQTTSSL = findViewById(R.id.ssl);
        mUser = findViewById(R.id.user);;
        mPassword = findViewById(R.id.password);
        mSaveButton = findViewById(R.id.save_button);
        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                save();
            }
        });
        mTopics = findViewById(R.id.topics);

        SharedPreferences settings = getSharedPreferences(BrokerListActivity.PREFS_NAME, Activity.MODE_PRIVATE);
        String brokersJSON = settings.getString(BrokerListActivity.BROKERS, null);

        mViewModel = ViewModelProviders.of(this).get(BrokerViewModel.class);
        try {
            mViewModel.init(brokersJSON);
        } catch (JSONException e) {
            Log.d(TAG, brokersJSON);
            Log.e(TAG, "Loading brokers failed." , e);
            Toast.makeText(getApplicationContext(), R.string.error_loading_brokers, Toast.LENGTH_LONG).show();
        }

        mViewModel.requestBroker.observe(this, new Observer<BrokerRequest>() {
            @Override
            public void onChanged(@Nullable BrokerRequest brokerRequest) {
                if (brokerRequest != null) {
                    Broker b = brokerRequest.getBroker();
                    if (b.status == 1) {
                        mSwipeRefreshLayout.setRefreshing(true);
                    } else {
                        if (mViewModel.isCurrentRequest(brokerRequest)) {
                            mSwipeRefreshLayout.setRefreshing(false);
                            mViewModel.confirmResultDelivered();
                            setUIEnabled(true, true);
                        }
                        if (b.requestStatus != Cmd.RC_OK) {
                            String t = (b.requestErrorTxt == null ? "" : b.requestErrorTxt);
                            if (b.requestStatus == Cmd.RC_MQTT_ERROR || b.requestStatus == Cmd.RC_NOT_AUTHORIZED) {
                                t = EditBrokerActivity.this.getString(R.string.errormsg_mqtt_prefix) + " " + t;
                            }
                            showErrorMsg(t);
                        } else {
                            mBroker = b;
                            saveLocalAndFinish(b);
                        }
                    }
                }
            }
        });

        Bundle args = getIntent().getExtras();
        mMode = (args != null ? args.getInt(MODE, MODE_ADD) : MODE_ADD);
        if (mMode == MODE_EDIT) {

            setTitle(R.string.title_edit_broker);

            String json;
            if (savedInstanceState == null) {
                json = args.getString(PARAM_BROKER_JSON);
            } else {
                json = savedInstanceState.getString(PARAM_BROKER_JSON);
            }

            try {
                //TODO: remove (including definitions in activity_edit_broker.xml), also remove mTopics textEdit
                // findViewById(R.id.viewExplanation).setVisibility(View.VISIBLE);
                // findViewById(R.id.viewTopics).setVisibility(View.VISIBLE);

                mBroker = Broker.createBrokerFormJSON(new JSONObject(json));
                mPushNotificationServer.setText(mBroker.pushserver);
                URI u = new URI(mBroker.uri);
                mMQTTHost.setText(u.getHost());
                if (u.getPort() != -1)
                    mMQTTPort.setText(String.valueOf(u.getPort()));
                mMQTTSSL.setChecked(u.getScheme().toLowerCase().equals("ssl"));
                mUser.setText(mBroker.user);
                mPassword.setText(new String(mBroker.password));
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < mBroker.topics.size(); i++) {
                    sb.append(mBroker.topics.get(i));
                    if (i + 1 < mBroker.topics.size())
                        sb.append("\n");
                }

                mTopics.setText(sb.toString());

            } catch (Exception e) {
                Log.e(TAG, "parse error", e);
            }
        } else { // MODE == MODE_ADD
            setTitle(R.string.title_add_broker);
        }

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setEnabled(false);
        mSwipeRefreshLayout.setRefreshing(mViewModel.isRequestActive());
        setUIEnabled(!mViewModel.isRequestActive(), !mViewModel.isRequestActive());

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    protected void showErrorMsg(String msg) {
        View v = EditBrokerActivity.this.findViewById(R.id.rView);
        if (v != null) {
            mSnackbar = Snackbar.make(v, msg, Snackbar.LENGTH_INDEFINITE);
            mSnackbar.show();
        }
    }

    protected void setUIEnabled(boolean fieldsEnabled, boolean buttonsEnabled) {
        Bundle args = getIntent().getExtras();
        boolean addMode = args == null || args.getInt(MODE, MODE_ADD) == MODE_ADD;
        mPushNotificationServer.setEnabled(fieldsEnabled && addMode);
        mMQTTHost.setEnabled(fieldsEnabled && addMode);
        mMQTTSSL.setEnabled(fieldsEnabled && addMode);
        mMQTTPort.setEnabled(fieldsEnabled && addMode);
        mUser.setEnabled(fieldsEnabled && addMode);
        mPassword.setEnabled(fieldsEnabled);
        mSaveButton.setEnabled(buttonsEnabled);
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_edit_broker, menu);
        return true;
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
        if (mBroker != null) {
            try {
                outState.putString("PARAM_BROKER_JSON", mBroker.getJSONObject().toString());
            } catch (JSONException e) {
                Log.e(TAG, "parse error", e);
            }
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
            setResult(AppCompatActivity.RESULT_CANCELED);
            finish();
        }
    }

    protected Broker getUserInput() {
        Broker b = new Broker();
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

        String[] t = mTopics.getText().toString().split("\n");
        ArrayList<String> topics = new ArrayList<>();
        for(int i = 0; i < t.length; i++) {
            if (t[i].trim().length() > 0)
            topics.add(t[i].trim());
        }
        b.topics = topics;
        return b;
    }

    protected boolean hasDataChanged() {
        Broker o;
        if (mBroker == null) {
            o = new Broker();
        } else {
            o = mBroker;
        }
        Broker n = getUserInput();
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

        if (eq) {
            ArrayList<String> oa = new ArrayList<>(o.topics);
            ArrayList<String> na = new ArrayList<>(n.topics);
            if (oa.size() != na.size()) {
                eq = false;
            } else {
                Collections.sort(oa);
                Collections.sort(na);
                for(int i = 0; i < oa.size(); i++) {
                    if (!oa.get(i).equals(na.get(i))) {
                        eq = false;
                        break;
                    }
                }
            }
        }

        return !eq;
    }

    protected void save() {
        if (checkData()) {
            if (hasDataChanged()) {
                setUIEnabled(false, false);
                mViewModel.saveBroker(this, getUserInput());
            } else {
                Toast.makeText(getApplicationContext(), R.string.error_data_unmodified, Toast.LENGTH_LONG).show();
            }
        }
    }

    protected void saveLocalAndFinish(Broker checkedBroker) {
        ArrayList<Broker> broker = mViewModel.brokerList.getValue();
        if (broker == null)
            broker = new ArrayList<>();

        boolean found = false;
        for(int i = 0; i < broker.size(); i++) {
            Broker b = broker.get(i);
            if (b.getKey().equals(checkedBroker.getKey())) {
                Broker n = getUserInput();
                b.pushserver = n.pushserver;
                b.uri = n.uri;
                b.user = n.user;
                b.password = n.password;
                b.topics = n.topics;
                found = true;
                break;
            }
        }
        if (!found) {
            broker.add((checkedBroker));
        }

        try {
            mViewModel.brokerList.setValue(broker);
            SharedPreferences settings = getSharedPreferences(BrokerListActivity.PREFS_NAME, Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(BrokerListActivity.BROKERS, mViewModel.getBrokersJSON());
            editor.commit();
            Toast.makeText(getApplicationContext(), R.string.info_data_saved, Toast.LENGTH_LONG).show();
            Intent data = new Intent();
            data.putExtra(PARAM_BROKER_JSON, mBroker.getJSONObject().toString());
            setResult(AppCompatActivity.RESULT_OK, data);
            finish();


        } catch (JSONException e) {
            Log.e(TAG, "saving broker list failed", e);
            showErrorMsg(getString(R.string.error_saving_brokers));
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

    public static class QuitWithoutSaveDlg extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getString(R.string.dlg_quit_without_save_title));
            builder.setMessage(getString(R.string.dlg_quit_without_save_msg));

            builder.setPositiveButton(R.string.action_quit, new DialogInterface.OnClickListener() {
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


    public BrokerViewModel mViewModel;
    public Broker mBroker;
    public TextView mPushNotificationServer;
    public TextView mMQTTHost;
    public TextView mMQTTPort;
    public CheckBox mMQTTSSL;
    public TextView mUser;
    public TextView mPassword;
    public Button mSaveButton;
    public TextView mTopics; //TODO: remove
    public int mMode;

    private Snackbar mSnackbar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    public final static String MODE = "MODE";
    public final static int MODE_ADD = 1;
    public final static int MODE_EDIT = 2;
    public final static String PARAM_BROKER_JSON = "PARAM_BROKER_JSON";
    private final static String TAG = EditBrokerActivity.class.getSimpleName();
}
