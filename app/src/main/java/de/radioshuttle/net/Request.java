/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.net;

import androidx.lifecycle.MutableLiveData;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteConstraintException;
import android.os.AsyncTask;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import de.radioshuttle.db.AppDatabase;
import de.radioshuttle.db.Code;
import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.fcm.Notifications;
import de.radioshuttle.mqttpushclient.R;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLHandshakeException;

import de.radioshuttle.mqttpushclient.PushAccount;

public class Request extends AsyncTask<Void, Void, PushAccount> {

    public Request(Context context, PushAccount pushAccount, MutableLiveData<Request> accountLiveData) {
        mAppContext = context.getApplicationContext();
        mPushAccount = pushAccount;
        mAccountLiveData = accountLiveData;
        mCancelled = new AtomicBoolean(false);
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

            /* connect */
            if (cont) {
                cont = false;
                if (mPushAccount.status != 1) {
                    mPushAccount.status = 1;
                    if (mAccountLiveData != null) {
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
                mConnection.login(mPushAccount);
                requestStatus = mConnection.lastReturnCode;
                if (mConnection.lastReturnCode == Cmd.RC_OK) {
                    requestErrorTxt = mAppContext.getString(R.string.status_ok);
                    if (!mCancelled.get()) {
                        cont = true;
                    }
                } else if (mConnection.lastReturnCode == Cmd.RC_NOT_AUTHORIZED) {
                    requestErrorTxt = mAppContext.getString(R.string.errormsg_not_authorized);
                } else if (mConnection.lastReturnCode == Cmd.RC_INVALID_ARGS) {
                    requestStatus = Cmd.RC_INVALID_ARGS;
                    requestErrorTxt = mAppContext.getString(R.string.errormsg_invalid_url);
                }
            }

            /* de.radioshuttle.fcm data*/
            if (cont) {
                cont = false;
                //TODO: remove ios example
                /*
                Map<String, String> m2 = mConnection.getFCMDataIOS();
                Log.d(TAG, "app_id " + m2.get("app_id_") + " " +  m2.get("sender_id"));
                */

                Map<String, String> m = mConnection.getFCMData();
                mPushAccount.pushserverID = m.get("pushserverid");

                /* get last messages from server */
                syncMessages();

                FirebaseApp app = null;
                String firebaseOptionsName = m.get("sender_id");

                for (FirebaseApp a : FirebaseApp.getApps(mAppContext)) {
                    if (a.getName().equals(firebaseOptionsName)) {
                        app = FirebaseApp.getInstance(firebaseOptionsName);
                        break;
                    }
                }

                if (app == null) {
                    FirebaseOptions options = new FirebaseOptions.Builder()
                            .setApplicationId(m.get("app_id"))
                            .build();
                    app = FirebaseApp.initializeApp(mAppContext, options, firebaseOptionsName);
                }

                if (app != null) {
                    FirebaseInstanceId id = FirebaseInstanceId.getInstance(app);

                    Task<InstanceIdResult> t = id.getInstanceId();

                    try {
                        Tasks.await(t, 3, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        // Log.d(TAG, "task exception ", e);
                    }

                    if (!t.isSuccessful()) {
                        throw new ClientError(mAppContext.getString(R.string.errormsg_fcm_id));
                    }
                    mConnection.setDeviceInfo(t.getResult().getToken());
                    cont = true;
                } else {
                    throw new ClientError("Initializing cloud messaging failed."); //TODO: add to resources, check for fcmlib
                }
            }

            if (cont) {
                cont = perform();
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
            mPushAccount.status = 0;

        }

        return null;
    }

    @Override
    protected void onPostExecute(PushAccount pushAccount) {
        super.onPostExecute(pushAccount);
        if (mAccountLiveData != null) {
            mAccountLiveData.setValue(this);
        }
    }

    protected void syncMessages() throws IOException, ServerError {
        long[] lastReceivedKey = Notifications.getMaxReceivedDate(mAppContext, mPushAccount.pushserver, mPushAccount.getMqttAccountName());
        long lastReceived = lastReceivedKey[0];
        int lastReceivedSeqNo = (int) lastReceivedKey[1];

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

        Log.d(TAG, "getCachedMessages: " + (lastReceived / 1000L) + " / " + lastReceivedSeqNo);
        List<Object[]> messages =  mConnection.getCachedMessages(lastReceived / 1000L, lastReceivedSeqNo);
        ArrayList<Integer> ids = new ArrayList<>();

        long expireDate = System.currentTimeMillis() - MqttMessage.MESSAGE_EXPIRE_MS;

        for(int i = 0; i < messages.size(); i++) {
            MqttMessage mqttMessage = new MqttMessage();
            mqttMessage.setPushServerID(psCode.intValue());
            mqttMessage.setMqttAccountID(mqttAccountCode.intValue());

            mqttMessage.setWhen((Long) messages.get(i)[0] * 1000L);
            mqttMessage.setTopic((String) messages.get(i)[1]);
            try {
                mqttMessage.setMsg(new String((byte[]) messages.get(i)[2]));
            } catch(Exception e) {
                mqttMessage.setMsg(Base64.encodeToString((byte[]) messages.get(i)[2], Base64.DEFAULT)); //TODO: error should be rare but consider using hex
            }
            mqttMessage.setSeqno((Integer) messages.get(i)[3]);

            if (mqttMessage.getWhen() < expireDate)
                continue;

            Log.i(TAG, "entry: " + new Date(mqttMessage.getWhen()) + " " + (mqttMessage.getWhen() / 1000L)  + " "
                    +  mqttMessage.getSeqno() + " " + mqttMessage.getTopic() + " " + mqttMessage.getMsg());

            try {
                Long k = db.mqttMessageDao().insertMqttMessage(mqttMessage);
                if (k != null && k >= 0) {
                    ids.add(k.intValue());
                    if (mqttMessage.getWhen() > lastReceived) {
                        lastReceived = mqttMessage.getWhen();
                        lastReceivedSeqNo = mqttMessage.getSeqno();
                    } else if (mqttMessage.getWhen() == lastReceived) {
                        if (mqttMessage.getSeqno() > lastReceivedSeqNo) {
                            lastReceivedSeqNo = mqttMessage.getSeqno();
                        }
                    }
                }
            } catch(SQLiteConstraintException e) {
                /* this error may occur, if the message has already been added by sync operation */
                Log.d(TAG, "constraint error: " + e.getMessage());
                continue;
            }
        }

        if (ids.size() > 0) {
            Notifications.addToNewMessageCounter(mAppContext, mPushAccount.pushserver, mPushAccount.getMqttAccountName(),
                    ids.size(), lastReceived, lastReceivedSeqNo);
            Intent intent = new Intent(MqttMessage.UPDATE_INTENT);
            intent.putExtra(MqttMessage.ARG_PUSHSERVER_ADDR, mPushAccount.pushserver);
            intent.putExtra(MqttMessage.ARG_MQTT_ACCOUNT, mPushAccount.getMqttAccountName());
            intent.putExtra(MqttMessage.ARG_IDS, ids);
            LocalBroadcastManager.getInstance(mAppContext).sendBroadcast(intent);

        }

        /* delete all messages older than 30 days */
        long before = System.currentTimeMillis() - MqttMessage.MESSAGE_EXPIRE_MS;
        db.mqttMessageDao().deleteMessagesBefore(before);

    }

    /** override for additional commands after login and exchanging de.radioshuttle.fcm data  */
    public boolean perform() throws Exception {
        return true;
    }

    public PushAccount getAccount() {
        return mPushAccount;
    }


    protected Connection mConnection;
    protected AtomicBoolean mCancelled;
    protected Context mAppContext;
    protected PushAccount mPushAccount;
    protected MutableLiveData<Request> mAccountLiveData;
    protected CertException mCertException;
    protected boolean mInsecureConnectionAsk;

    private final static String TAG = Request.class.getSimpleName();
}
