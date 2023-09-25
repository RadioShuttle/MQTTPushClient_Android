/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.net;

import androidx.lifecycle.MutableLiveData;
import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.IOException;

import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.utils.Utils;

public class DeleteToken extends Request {

    public DeleteToken(Context context, boolean deleteToken, PushAccount pushAccount, MutableLiveData<Request> accountLiveData) {
        super(context, pushAccount, accountLiveData);
        mDeleteTokenArg = deleteToken;
        deviceRemoved = false;
        deletionAborted = false;
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
        // Log.d(TAG, "onPostExecute: deleteToken: " + mDeleteTokenArg + ", deviceRemoved: " + deviceRemoved);

        if (mPushAccount.certException == null && !mPushAccount.inSecureConnectionAsk) {
            // call
            if (!mDeleteTokenArg || deviceRemoved) {
                super.onPostExecute(pushAccount); // finish update UI
            }

            /* even if device has reomved from server , fcm token must be deleted */
            if (mDeleteTokenArg) {
                mPushAccount.status = 1;
                Utils.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        deleteToken();
                    }
                });
            }
        } else {
            // certificate error
            deletionAborted = true;
            super.onPostExecute(pushAccount);
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
                    FirebaseMessaging messageApp = app.get(FirebaseMessaging.class);
                    Task<String> t = messageApp.getToken();

                    try {
                        Tasks.await(t);
                    } catch (Exception e) {
                        Log.d(TAG, "Deletion of token for push server " + mPushAccount.pushserver + "  failed: " + e.getMessage());
                    }

                    if (t.isSuccessful()) {
                        try {
                            messageApp.deleteToken();
                            deviceRemoved = true;
                            Log.d(TAG, "token deleted");
                        } catch (Exception e) {
                            Log.d(TAG, "deleteInstanceId() failed for " + mPushAccount.pushserver + ": " + t.getResult());
                            // throw new ClientError(e);
                        }
                        Log.d(TAG, "Token deleted for " + mPushAccount.pushserver + ": " + t.getResult());
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

    /** set if eihter removeDevice() request was succesfull or deleteInstanceID*/
    public boolean deviceRemoved;
    /** request parameter: if FCM token has to be deleted (multiple accounts may share the same token) */
    private boolean mDeleteTokenArg;
    /** if deletion process has been aborted (in this case do not remove account locally) */
    public volatile boolean deletionAborted;

    private final static String TAG = DeleteToken.class.getSimpleName();
}
