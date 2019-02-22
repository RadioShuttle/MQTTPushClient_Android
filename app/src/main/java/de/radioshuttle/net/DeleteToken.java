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
    }

    @Override
    public boolean perform() throws Exception {
        mConnection.removeDevice();


        return true;
    }

    @Override
    protected void onPostExecute(PushAccount pushAccount) {
        super.onPostExecute(pushAccount);
        if (mDeleteToken) {
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
        for (FirebaseApp a : FirebaseApp.getApps(mAppContext)) {
            if (a.getName().equals(mSenderID)) {
                app = FirebaseApp.getInstance(mSenderID);
                FirebaseInstanceId id = FirebaseInstanceId.getInstance(app);

                Task<InstanceIdResult> t = id.getInstanceId();
                try {
                    Tasks.await(t);
                } catch (Exception e) {
                    Log.d(TAG, "Deletion of token for push server " +  mPushAccount.pushserver + "  failed: " + e.getMessage());
                }

                if (t.isSuccessful()) {
                    try {
                        id.deleteInstanceId();
                        Log.d(TAG, "token deleted");
                    } catch (IOException e) {
                        Log.d(TAG, "deleteInstanceId() failed for " +  mPushAccount.pushserver + ": " + t.getResult().getToken());
                        // throw new ClientError(e);
                    }
                    Log.d(TAG, "Token deleted for " +  mPushAccount.pushserver + ": " + t.getResult().getToken());
                } else {
                    Log.d(TAG, "Deletion of token for push server " +  mPushAccount.pushserver + "  failed.");
                    // throw new ClientError("Deletion of token failed.");
                }
                break;
            }
        }

    }


    private boolean mDeleteToken;

    private final static String TAG = DeleteToken.class.getSimpleName();
}
