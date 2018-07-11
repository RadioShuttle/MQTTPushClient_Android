/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.net;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import de.radioshuttle.mqttpushclient.R;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import de.radioshuttle.mqttpushclient.PushAccount;

public class Request extends AsyncTask<Void, Void, PushAccount> {

    public Request(Context context, PushAccount pushAccount, MutableLiveData<Request> brokerLiveData) {
        mAppContext = context.getApplicationContext();
        mPushAccount = pushAccount;
        mBrokerLiveData = brokerLiveData;
        mCancelled = new AtomicBoolean(false);
    }

    public void cancel() {
        mCancelled.set(true);
        if (mConnection != null) {
            mConnection.disconnect();
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
                    if (mBrokerLiveData != null) {
                        mBrokerLiveData.postValue(this);
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
                        //TODO: consider appending exception message text
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
                Map<String, String> m = mConnection.getFCMData();

                FirebaseApp app = null;

                for (FirebaseApp a : FirebaseApp.getApps(mAppContext)) {
                    if (a.getName().equals(mPushAccount.pushserver)) {
                        app = FirebaseApp.getInstance(mPushAccount.pushserver);
                        break;
                    }
                }

                if (app == null) {
                    FirebaseOptions options = new FirebaseOptions.Builder()
                            .setApplicationId(m.get("app_id")) // Required for Analytics.
                            .setApiKey(m.get("api_key")) // Required for Auth.
                            // .setDatabaseUrl(m.get("database_url")) // Required for RTDB. //TODO
                            .build();
                    app = FirebaseApp.initializeApp(mAppContext, options, mPushAccount.pushserver);
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
                    mConnection.setFCMToken(t.getResult().getToken());
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
            e.printStackTrace();
            Log.e(TAG, "unexpected error: ", e);
            requestStatus = Connection.STATUS_UNEXPECTED_ERROR;
            requestErrorTxt = mAppContext.getString(R.string.errormsg_unexpected_error);
        } finally {
            if (mCancelled.get()) { // override error code which may be caused by cancellation
                requestStatus = Connection.STATUS_CANCELED;
                requestErrorTxt = mAppContext.getString(R.string.errormsg_op_canceled);
            }

            if (mConnection != null) {
                mConnection.disconnect();
            }

            mPushAccount.requestStatus = requestStatus;
            mPushAccount.requestErrorCode = requestErrorCode;
            mPushAccount.requestErrorTxt = requestErrorTxt;
            mPushAccount.status = 0;

            if (mBrokerLiveData != null) {
                mBrokerLiveData.postValue(this);
            }
        }

        return null;
    }

    /** override for additional commands after login and exchanging de.radioshuttle.fcm data  */
    public boolean perform() throws Exception {
        return true;
    }

    public PushAccount getBroker() {
        return mPushAccount;
    }


    protected Connection mConnection;
    protected AtomicBoolean mCancelled;
    protected Context mAppContext;
    protected PushAccount mPushAccount;
    protected MutableLiveData<Request> mBrokerLiveData;

    private final static String TAG = Request.class.getSimpleName();
}
