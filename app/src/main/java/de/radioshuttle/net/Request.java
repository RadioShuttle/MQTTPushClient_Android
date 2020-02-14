/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.net;

import androidx.lifecycle.MutableLiveData;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteConstraintException;
import android.os.AsyncTask;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import org.json.JSONArray;
import org.json.JSONObject;

import de.radioshuttle.db.AppDatabase;
import de.radioshuttle.db.Code;
import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.fcm.Notifications;
import de.radioshuttle.mqttpushclient.AccountListActivity;
import de.radioshuttle.mqttpushclient.R;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLHandshakeException;

import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.utils.FirebaseTokens;
import de.radioshuttle.utils.Utils;

public class Request extends AsyncTask<Void, Void, PushAccount> {

    public Request(Context context, PushAccount pushAccount, MutableLiveData<Request> accountLiveData) {
        mAppContext = context.getApplicationContext();
        mPushAccount = pushAccount;
        mAccountLiveData = accountLiveData;
        mSync = false;
        mGetTopicFilterScripts = true;
        mAccountUpdated = false;
        mHasTopics = null;
        mCancelled = new AtomicBoolean(false);
        mCompleted = false;
    }

    public void cancel() {
        mCancelled.set(true);
        if (mConnection != null) {
            Thread t = new Thread(new Runnable() { //TODO: check if there are still network ops on main ui thread
                @Override
                public void run() {
                    Log.d(TAG, "connection cancelled.");
                    mConnection.disconnect();
                }
            });
            t.start();
        }
    }

    @Override
    protected PushAccount doInBackground(Void... voids) {

        int requestStatus = 0;
        int requestErrorCode = 0;
        String requestErrorTxt = "";

        try {
            boolean cont = false;

            if (!mCancelled.get()) {
                cont = true;
            }

            SharedPreferences settings = mAppContext.getSharedPreferences(AccountListActivity.PREFS_INST, Activity.MODE_PRIVATE);
            String uuid = settings.getString(AccountListActivity.UUID, "");

            boolean isDeleteRequest = (this instanceof DeleteToken); // to disable some steps
            boolean isRestrictedAccess = false;

            /* connect */
            if (cont) {
                cont = false;
                if (mAccountLiveData != null) {
                    if (mPushAccount.status != 1) {
                        mPushAccount.status = 1;
                        mAccountLiveData.postValue(this);
                    }
                }
                try {
                    mConnection = new Connection(mPushAccount.pushserver, mAppContext);
                    mConnection.connect();
                    if (!mCancelled.get()) {
                        cont = true;
                    }
                } catch (IncompatibleProtocolException p) {
                    requestStatus = Cmd.RC_INVALID_PROTOCOL;
                    requestErrorTxt = p.getLocalizedMessage();
                } catch (UnknownHostException u) {
                    requestStatus = Connection.STATUS_UNKNOWN_HOST;
                    requestErrorTxt = mAppContext.getString(R.string.errormsg_unknown_host);
                } catch (SocketTimeoutException so) {
                    requestStatus = Connection.STATUS_TIMEOUT;
                    requestErrorTxt = mAppContext.getString(R.string.errormsg_socket_timeout);
                } catch(SSLHandshakeException e) {
                    requestStatus = Connection.STATUS_CONNECTION_FAILED;
                    requestErrorTxt = mAppContext.getString(R.string.errormsg_connection_failed_SSL);
                    if (e.getCause() instanceof CertificateException) {
                        requestErrorTxt += ": " + mAppContext.getString(R.string.errormsg_certificate_error);
                        if (e.getCause() instanceof CertException) {
                            mCertException = (CertException) e.getCause();
                            /* host name may not be validated because exception already thrown, check host for more detailed error handling */
                            if (!OkHostnameVerifier.INSTANCE.verify(mPushAccount.pushserver, mCertException.chain[0])) {
                                mCertException.reason |= AppTrustManager.HOST_NOT_MATCHING;
                            }
                        }
                    } else if (e instanceof HostVerificationError) {
                        requestErrorTxt += ": " + mAppContext.getString(R.string.errormsg_certificate_error);
                        mCertException = new CertException(
                                null,
                                AppTrustManager.HOST_NOT_MATCHING,
                                ((HostVerificationError) e).chain);
                    }
                } catch (IOException io) {
                    requestStatus = Connection.STATUS_CONNECTION_FAILED;
                    String msg = io.getMessage();
                    if (msg != null && msg.contains("ENETUNREACH")) {
                        requestErrorTxt = mAppContext.getString(R.string.errormsg_enetunreach);
                    } else if (msg != null && msg.contains("ECONNREFUSED")) {
                        requestErrorTxt = mAppContext.getString(R.string.errormsg_econnrefused);
                    } else {
                        // default msg,
                        requestErrorTxt = mAppContext.getString(R.string.errormsg_connection_failed);
                    }
                } catch(InsecureConnection ic) {
                    requestStatus = Connection.STATUS_CONNECTION_FAILED;
                    requestErrorTxt = mAppContext.getString(R.string.errormsg_insecure);
                    Boolean allow = Connection.mInsecureConnection.get(mPushAccount.pushserver);
                    if (allow == null) {
                        mInsecureConnectionAsk = true;
                    }
                }
            }

            /* login */
            if (cont) {
                cont = false;
                Cmd.RawCmd rawCmd = null;
                try {
                    rawCmd = mConnection.login(mPushAccount, uuid);
                } catch(MQTTException mq) {
                    if (isDeleteRequest && mq.accountInfo == 1) {
                        /* special case: MQTT broker not available, but login matches stored user credentials
                        * -> continue with deletion, but set error codes */
                        // requestStatus = Cmd.RC_MQTT_ERROR;
                        requestErrorCode = mq.errorCode;
                        requestErrorTxt = mq.getMessage();
                        isRestrictedAccess = true;
                        cont = true;
                    } else {
                        throw mq;
                    }
                }
                requestStatus = mConnection.lastReturnCode;
                if (mConnection.lastReturnCode == Cmd.RC_OK) {
                    requestErrorTxt = mAppContext.getString(R.string.status_ok);
                    if (!mCancelled.get()) {
                        cont = true;
                    }
                } else if (mConnection.lastReturnCode == Cmd.RC_NOT_AUTHORIZED) {
                    Map<String, Object> m = mConnection.mCmd.readErrorData(rawCmd.data);
                    requestErrorCode = (m.containsKey("err_code") ? (short) m.get("err_code") : 0);
                    if (requestErrorCode == 0) {
                        requestErrorTxt = mAppContext.getString(R.string.errormsg_not_permitted, mPushAccount.pushserver);
                    } else {
                        requestErrorTxt = mAppContext.getString(R.string.errormsg_not_authorized);
                    }
                } else if (mConnection.lastReturnCode == Cmd.RC_INVALID_ARGS) {
                    requestStatus = Cmd.RC_INVALID_ARGS;
                    requestErrorTxt = mAppContext.getString(R.string.errormsg_invalid_url);
                }
            }

            Task<InstanceIdResult> instanceIdTask = null;

            /* de.radioshuttle.fcm data*/
            if (cont && !isRestrictedAccess) {
                cont = false;

                Map<String, String> m = mConnection.getFCMData();

                mPushAccount.pushserverID = m.get("pushserverid");

                FirebaseApp app = null;
                mSenderID = m.get("sender_id");
                if (!Utils.equals(mSenderID, mPushAccount.fcm_sender_id) || !Utils.equals(m.get("app_id"), mPushAccount.fcm_app_id)) {
                    mPushAccount.fcm_app_id = m.get("app_id");
                    mPushAccount.fcm_sender_id = mSenderID;

                    synchronized (ACCOUNTS) {
                        updateAccountFCMData();
                    }
                }

                for (FirebaseApp a : FirebaseApp.getApps(mAppContext)) {
                    if (a.getName().equals(mSenderID)) {
                        app = FirebaseApp.getInstance(mSenderID);
                        break;
                    }
                }

                if (app == null) {
                    try {
                        synchronized (FIREBASE_SYNC) {
                            for (FirebaseApp a : FirebaseApp.getApps(mAppContext)) { // reread
                                if (a.getName().equals(mSenderID)) {
                                    app = FirebaseApp.getInstance(mSenderID);
                                    break;
                                }
                            }
                            if (app == null) {
                                FirebaseOptions options = new FirebaseOptions.Builder()
                                        .setApplicationId(m.get("app_id"))
                                        .build();
                                app = FirebaseApp.initializeApp(mAppContext, options, mSenderID);
                            }
                        }
                    } catch(Exception e) {
                        Log.d(TAG, "Error initializing firebase for "+ mSenderID, e);
                    }
                }

                if (app != null) {
                    FirebaseInstanceId id = FirebaseInstanceId.getInstance(app);
                    instanceIdTask = id.getInstanceId();
                    cont = true;
                } else {
                    throw new ClientError(mAppContext.getString(R.string.errormsg_firebase_init_failed));
                }

                /* get last messages from server */
                if (mSync) {
                    syncMessages();
                }

                /* get stored filter scripts. local stored scripts may not be up to date */
                if (mGetTopicFilterScripts) {
                    LinkedHashMap<String, Cmd.Topic> topics = mConnection.getTopics();
                    synchronized (ACCOUNTS) {
                        updateLocalStoredScripts(topics);
                    }
                }

            }

            if (cont) {
                cont = perform();

                if (!isDeleteRequest) {
                    /* if the fcm token was not set as request argument, get fcm token from instanceid task */
                    if (mToken == null && instanceIdTask != null) {
                        if (instanceIdTask.isSuccessful()) {
                            mToken = instanceIdTask.getResult().getToken();
                        } else if (!instanceIdTask.isComplete()) {
                            /* "get token" is still in progress? then wait and notify (async) */
                            FirebaseTokens.getInstance(mAppContext)
                                    .waitForTokenAndNotifyPushServer(mPushAccount, instanceIdTask);
                        }
                    }

                    mConnection.setDeviceInfo(mToken);
                    /* set info that token was send to pushserver for the given account */
                    if (mToken != null) {
                        FirebaseTokens.getInstance(mAppContext).setTokenSent(mPushAccount.getKey(), mToken);
                    }
                }
            }

            if (requestStatus == Cmd.RC_OK) {
                mConnection.bye();
            }

        } catch(ClientError e) {
            requestStatus = Connection.STATUS_CLIENT_ERROR;
            requestErrorTxt = e.getMessage();
        } catch(MQTTException e) {
            requestStatus = Cmd.RC_MQTT_ERROR;
            requestErrorCode = e.errorCode;
            requestErrorTxt = e.getMessage();
        } catch(ServerError e) {
            requestStatus = Cmd.RC_SERVER_ERROR;
            requestErrorCode = e.errorCode;
            requestErrorTxt = mAppContext.getString(R.string.errormsg_unexpected_server_error);
        } catch (SocketTimeoutException so) {
            requestStatus = Connection.STATUS_TIMEOUT;
            requestErrorTxt = mAppContext.getString(R.string.errormsg_timeout);
        } catch(IOException io) {
            requestStatus = Connection.STATUS_IO_ERROR;
            requestErrorTxt = mAppContext.getString(R.string.errormsg_io_error);
        } catch(Exception e) {
            Log.e(TAG, "unexpected error: ", e);
            requestStatus = Connection.STATUS_UNEXPECTED_ERROR;
            requestErrorTxt = mAppContext.getString(R.string.errormsg_unexpected_error);
        } finally {
            if (mCancelled.get()) { // override error code which may be caused by cancellation
                mPushAccount.status = 0;
                requestStatus = Connection.STATUS_CANCELED;
                requestErrorTxt = mAppContext.getString(R.string.errormsg_op_canceled);
                if (mAccountLiveData != null) {
                    mAccountLiveData.postValue(this);
                }
            }

            if (mConnection != null) {
                mConnection.disconnect();
            }

            mPushAccount.requestStatus = requestStatus;
            mPushAccount.requestErrorCode = requestErrorCode;
            mPushAccount.requestErrorTxt = requestErrorTxt;
            mPushAccount.certException = mCertException;
            mPushAccount.inSecureConnectionAsk = mInsecureConnectionAsk;
        }

        return null;
    }

    protected void updateAccountFCMData() throws Exception{

        SharedPreferences settings = mAppContext.getSharedPreferences(AccountListActivity.PREFS_NAME, Activity.MODE_PRIVATE);
        String accountsJson = settings.getString(AccountListActivity.ACCOUNTS, null);

        ArrayList<PushAccount> pushAccounts = new ArrayList<PushAccount>();
        if (accountsJson != null) {
            PushAccount pushAccount;
            JSONArray jarray = new JSONArray(accountsJson);
            for (int i = 0; i < jarray.length(); i++) {
                JSONObject b = jarray.getJSONObject(i);
                pushAccount = PushAccount.createAccountFormJSON(b);
                if (mPushAccount.getKey().equals(pushAccount.getKey())) {
                    pushAccount.fcm_sender_id = mPushAccount.fcm_sender_id;
                    pushAccount.fcm_app_id = mPushAccount.fcm_app_id;
                    Log.d(TAG, "updateAccountFCMData: " + pushAccount.getKey() + " " + pushAccount.fcm_sender_id + " " + pushAccount.fcm_app_id);
                }
                pushAccounts.add(pushAccount);
            }
            JSONArray accountList = new JSONArray();
            for(PushAccount acc : pushAccounts) {
                accountList.put(acc.getJSONObject());
            }
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(AccountListActivity.ACCOUNTS, accountList.toString());
            editor.commit();
        }
    }



    protected void updateLocalStoredScripts(LinkedHashMap<String, Cmd.Topic> receivedTopics) throws Exception {
        if (receivedTopics == null) {
            receivedTopics = new LinkedHashMap<>();
        }
        mHasTopics = receivedTopics.size() > 0;

        SharedPreferences settings = mAppContext.getSharedPreferences(AccountListActivity.PREFS_NAME, Activity.MODE_PRIVATE);
        String accountsJson = settings.getString(AccountListActivity.ACCOUNTS, null);

        boolean updateTopicsFilterScripts = false;

        ArrayList<PushAccount> pushAccounts = new ArrayList<PushAccount>();
        if (accountsJson != null) {
            PushAccount pushAccount;
            JSONArray jarray = new JSONArray(accountsJson);
            for(int i = 0 ; i < jarray.length(); i++) {
                JSONObject b = jarray.getJSONObject(i);
                pushAccount = PushAccount.createAccountFormJSON(b);
                pushAccounts.add(pushAccount);

                if (mPushAccount.getKey().equals(pushAccount.getKey())) {
                    Cmd.Topic e;
                    if (receivedTopics.size() != pushAccount.topicJavaScript.size()) {
                        updateTopicsFilterScripts = true;
                    } else {
                        for(PushAccount.Topic t : pushAccount.topicJavaScript) {
                            if (!receivedTopics.containsKey(t.name)) {
                                updateTopicsFilterScripts = true;
                                break;
                            } else  {
                                e = receivedTopics.get(t.name);
                                if (!Utils.equals(e.script, t.jsSrc)) {
                                    updateTopicsFilterScripts = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (updateTopicsFilterScripts) {
                        pushAccount.topicJavaScript.clear();
                        Iterator<Map.Entry<String, Cmd.Topic>> it = receivedTopics.entrySet().iterator();
                        Map.Entry<String, Cmd.Topic> val;
                        Cmd.Topic to;
                        PushAccount.Topic t;
                        while(it.hasNext()) {
                            val = it.next();
                            to = val.getValue();
                            t = new PushAccount.Topic();
                            t.name = val.getKey();
                            t.prio = to.type;
                            t.jsSrc = to.script;
                            pushAccount.topicJavaScript.add(t);
                        }
                    }
                }
            }
            if (updateTopicsFilterScripts) {
                JSONArray accountList = new JSONArray();
                for(PushAccount acc : pushAccounts) {
                    accountList.put(acc.getJSONObject());
                }
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(AccountListActivity.ACCOUNTS, accountList.toString());
                editor.commit();
                mAccountUpdated = true;
            }

        }
        Log.d(TAG, "topic js updated: " + updateTopicsFilterScripts);
    }

    @Override
    protected void onPostExecute(PushAccount pushAccount) {
        super.onPostExecute(pushAccount);
        setCompleted(true);
        mPushAccount.status = 0;

        if (mAccountLiveData != null) {
            mAccountLiveData.setValue(this);
        }
        if (mSync) {
            if (mLastSyncKeyOut != null) {
                Notifications.setLastSyncDate(mAppContext, mPushAccount.pushserver, mPushAccount.getMqttAccountName(), mLastSyncKeyOut[0], (int) mLastSyncKeyOut[1]);
                Log.d(TAG, "last sync date set: " + new Date(mLastSyncKeyOut[0]) + " " + (mLastSyncKeyOut[0] / 1000L) + " / " + mLastSyncKeyOut[1]);
            }
            if (mNewMessagesCnt != null && mNewMessagesCnt > 0) {
                Notifications.addToNewMessageCounter(mAppContext, mPushAccount.pushserver, mPushAccount.getMqttAccountName(),
                        mNewMessagesCnt);
                Log.d(TAG, "New messages: " + mNewMessagesCnt);
            }
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (mSync) {
            mLastSyncKeyIn = Notifications.getLastSyncDate(mAppContext, mPushAccount.pushserver, mPushAccount.getMqttAccountName());
        }
    }

    public void setSync(boolean sync) {
        mSync = sync;
    }

    protected void syncMessages() throws IOException, ServerError {
        long lastReceived = mLastSyncKeyIn[0];
        int lastReceivedSeqNo = (int) mLastSyncKeyIn[1];

        //if last received older than 30 days, set it to 30daysAgo
        GregorianCalendar monthAgo = new GregorianCalendar();
        monthAgo.add(Calendar.DAY_OF_MONTH, -30);
        if (lastReceived < monthAgo.getTimeInMillis()) {
            lastReceived = monthAgo.getTimeInMillis();
            lastReceivedSeqNo = 0;
        }

        AppDatabase db = null;
        Long psCode = null;
        Long mqttAccountCode = null;

        db = AppDatabase.getInstance(mAppContext);
        psCode = db.mqttMessageDao().getCode(mPushAccount.pushserverID);
        if (psCode == null) {
            Code c = new Code();
            c.setName(mPushAccount.pushserverID);
            try {
                psCode = db.mqttMessageDao().insertCode(c);
            } catch(SQLiteConstraintException e) {
                // rare case: MessagingService has just inserted this pushServerID
                Log.d(TAG,"insert pushserver id into code table: ", e);
                psCode = db.mqttMessageDao().getCode(mPushAccount.pushserverID);
            }
            // Log.d(TAG, " (before null) code: " + psCode);
        }
        mqttAccountCode = db.mqttMessageDao().getCode(mPushAccount.getMqttAccountName());
        if (mqttAccountCode == null) {
            Code c = new Code();
            c.setName(mPushAccount.getMqttAccountName());
            try {
                mqttAccountCode = db.mqttMessageDao().insertCode(c);
            } catch(SQLiteConstraintException e) {
                Log.d(TAG,"insert accountname into code table: ", e);
                // rare case: MessagingService has just inserted this mqtt account
                mqttAccountCode = db.mqttMessageDao().insertCode(c);
            }
            // Log.d(TAG, " (before null) mqttAccountCode: " + mqttAccountCode);
        }

        Log.d(TAG, "getCachedMessages: last sync date used: " + new Date(lastReceived) + " " + (lastReceived / 1000L) + " / " + lastReceivedSeqNo);
        List<Object[]> messages =  mConnection.getCachedMessages(lastReceived / 1000L, lastReceivedSeqNo);
        ArrayList<Integer> ids = new ArrayList<>();

        GregorianCalendar cal = new GregorianCalendar();
        cal.add(Calendar.DAY_OF_MONTH, -MqttMessage.MESSAGE_EXPIRE_DAYS);
        long expireDate = cal.getTimeInMillis();

        for(int i = 0; i < messages.size(); i++) {
            MqttMessage mqttMessage = new MqttMessage();
            mqttMessage.setPushServerID(psCode.intValue());
            mqttMessage.setMqttAccountID(mqttAccountCode.intValue());

            mqttMessage.setWhen((Long) messages.get(i)[0] * 1000L);
            mqttMessage.setTopic((String) messages.get(i)[1]);
            mqttMessage.setPayload((byte[]) messages.get(i)[2]);
            mqttMessage.setSeqno((Integer) messages.get(i)[3]);

            if (mqttMessage.getWhen() > lastReceived) {
                lastReceived = mqttMessage.getWhen();
                lastReceivedSeqNo = mqttMessage.getSeqno();
            } else if (mqttMessage.getWhen() == lastReceived) {
                if (mqttMessage.getSeqno() > lastReceivedSeqNo) {
                    lastReceivedSeqNo = mqttMessage.getSeqno();
                }
            }

            if (mqttMessage.getWhen() < expireDate)
                continue;

            Log.i(TAG, "entry: " + new Date(mqttMessage.getWhen()) + " " + (mqttMessage.getWhen() / 1000L)  + " "
                    +  mqttMessage.getSeqno() + " " + mqttMessage.getTopic() + " " + new String(mqttMessage.getPayload(), Utils.UTF_8));

            try {
                Long k = db.mqttMessageDao().insertMqttMessage(mqttMessage);
                if (k != null && k >= 0) {
                    ids.add(k.intValue());
                }
            } catch(SQLiteConstraintException e) {
                /* this error may occur, if the message has already been added by sync operation */
                Log.d(TAG, "constraint error: " + e.getMessage());
                continue;
            }
        }

        if (lastReceived > mLastSyncKeyIn[0] || (lastReceived == mLastSyncKeyIn[0] && lastReceivedSeqNo >  (int) mLastSyncKeyIn[1])) {
            mLastSyncKeyOut = new long[] {lastReceived, lastReceivedSeqNo};
            // Log.d(TAG, "last sync date set: " + new Date(lastReceived) + " " + (lastReceived / 1000L) + " / " + lastReceivedSeqNo);
        }

        if (ids.size() > 0) {
            mNewMessagesCnt = ids.size();
            Intent intent = new Intent(MqttMessage.UPDATE_INTENT);
            intent.putExtra(MqttMessage.ARG_PUSHSERVER_ADDR, mPushAccount.pushserver);
            intent.putExtra(MqttMessage.ARG_MQTT_ACCOUNT, mPushAccount.getMqttAccountName());
            intent.putExtra(MqttMessage.ARG_IDS, ids);
            LocalBroadcastManager.getInstance(mAppContext).sendBroadcast(intent);
        }

        /* delete all messages older than 30 days */
        cal = new GregorianCalendar();
        cal.add(Calendar.DAY_OF_MONTH, -MqttMessage.MESSAGE_EXPIRE_DAYS);
        long before = cal.getTimeInMillis();
        db.mqttMessageDao().deleteMessagesBefore(before);
        
    }

    /** override for additional commands after login and exchanging de.radioshuttle.fcm data  */
    public boolean perform() throws Exception {
        return true;
    }

    public PushAccount getAccount() {
        return mPushAccount;
    }

    public boolean hasAccountUpdated() {
        return mAccountUpdated;
    }

    public void setAccountUpdated(boolean updated) {
        mAccountUpdated = updated;
    }

    public void setTokenRequest(String token) {
        mToken = token;
        // disable actions:
        mSync = false;
        mGetTopicFilterScripts = false;
    }

    public void setCompleted(boolean completed) {
        mCompleted = true;
    }

    public boolean hasCompleted() {
        return mCompleted;
    }

    /* true, if this account has topics, if null: getTopics() was not called in this request */
    public Boolean hasTopics() {
        return mHasTopics;
    }

    protected String mSenderID;

    protected String mToken;
    public Boolean mHasTopics;
    protected volatile boolean mAccountUpdated;
    protected boolean mGetTopicFilterScripts;
    protected boolean mSync;
    protected Connection mConnection;
    protected AtomicBoolean mCancelled;
    protected Context mAppContext;
    protected PushAccount mPushAccount;
    protected MutableLiveData<Request> mAccountLiveData;
    protected CertException mCertException;
    protected boolean mInsecureConnectionAsk;

    protected boolean mCompleted;

    // values set, used in pre-, postExectue
    long[] mLastSyncKeyIn; // value read in preExecute
    long[] mLastSyncKeyOut; // value to be processed in postExecute
    Integer mNewMessagesCnt; // value to be processed in postExecute;


    public static Object FIREBASE_SYNC = new Object();
    public static Object ACCOUNTS = new Object();

    private final static String TAG = Request.class.getSimpleName();
}
