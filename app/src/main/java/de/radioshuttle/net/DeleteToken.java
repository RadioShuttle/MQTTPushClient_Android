/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.net;

import androidx.lifecycle.MutableLiveData;
import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.utils.Utils;

public class DeleteToken extends Request {

    public DeleteToken(Context context, boolean deleteToken, PushAccount pushAccount, MutableLiveData<Request> accountLiveData) {
        super(context, pushAccount, accountLiveData);
        mDeleteToken = deleteToken;
        deviceRemoved = false;
    }

    @Override
    public boolean perform() throws Exception {
        mConnection.removeDevice();
        if (mConnection.lastReturnCode == Cmd.RC_OK) {
            deviceRemoved = true;
        }
        return true;
    }

    @Override
    protected void onPostExecute(PushAccount pushAccount) {
        // Log.d(TAG, "onPostExecute: deleteToken: " + mDeleteToken + ", deviceRemoved: " + deviceRemoved);

        if (!(mDeleteToken && !deviceRemoved)) {
            /* do not call onPostExecute, if device was not removed and token shall be deleted in next op */
            super.onPostExecute(pushAccount);
        }
        if (mDeleteToken) {
            // TODO: if delete account request failed, deletion of token will also fail: senderID and FirebaseApp data must be stored locally to use in DeleteToken.deleteToken() when push server is no available
            mPushAccount.status = 1;
            Utils.executor.execute(new Runnable() {
                @Override
                public void run() {
                    deleteToken();
                }
            });
        }
    }

    protected void deleteToken() {
        FirebaseApp app = null;
        try {
            if (Utils.isEmpty(mSenderID)) Log.d(TAG, "deleteToken(): no sender id. using mPushAccount.fcm_sender_id.");

            String senderID = Utils.isEmpty(mSenderID) ? mPushAccount.fcm_sender_id : mSenderID;
            if (!Utils.isEmpty(senderID)) {
                for (FirebaseApp a : FirebaseApp.getApps(mAppContext)) {
                    if (a.getName().equals(senderID)) {
                        app = FirebaseApp.getInstance(senderID);
                        break;
                    }
                }
                if (app == null) {
                    Log.d(TAG, "deleteToken(): init firebase app with mPushAccount.fcm_app_id");
                    synchronized (FIREBASE_SYNC) {
                        for (FirebaseApp a : FirebaseApp.getApps(mAppContext)) { // reread
                            if (a.getName().equals(mSenderID)) {
                                app = FirebaseApp.getInstance(mSenderID);
                                break;
                            }
                        }
                        if (app == null) {
                            FirebaseOptions options = new FirebaseOptions.Builder()
                                    .setApplicationId(mPushAccount.fcm_app_id)
                                    .build();
                            app = FirebaseApp.initializeApp(mAppContext, options, mSenderID);
                        }
                    }
                }
                if (app != null) {
                    FirebaseInstanceId id = FirebaseInstanceId.getInstance(app);

                    Task<InstanceIdResult> t = id.getInstanceId();
                    try {
                        Tasks.await(t);
                    } catch (Exception e) {
                        Log.d(TAG, "Deletion of token for push server " + mPushAccount.pushserver + "  failed: " + e.getMessage());
                    }

                    if (t.isSuccessful()) {
                        try {
                            id.deleteInstanceId();
                            deviceRemoved = true;
                            Log.d(TAG, "token deleted");
                        } catch (IOException e) {
                            Log.d(TAG, "deleteInstanceId() failed for " + mPushAccount.pushserver + ": " + t.getResult().getToken());
                            // throw new ClientError(e);
                        }
                        Log.d(TAG, "Token deleted for " + mPushAccount.pushserver + ": " + t.getResult().getToken());
                    } else {
                        Log.d(TAG, "Deletion of token for push server " + mPushAccount.pushserver + "  failed.");
                        // throw new ClientError("Deletion of token failed.");
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Deletion of token failed: " + e.getMessage());
        } finally {
            mPushAccount.status = 0;
            if (mAccountLiveData != null) {
                mAccountLiveData.postValue(this);
            }
        }
    }

    /** set if eiter removeDevice() request was succesfull or deleteInstanceID*/
    public boolean deviceRemoved;
    private boolean mDeleteToken;

    private final static String TAG = DeleteToken.class.getSimpleName();
}
