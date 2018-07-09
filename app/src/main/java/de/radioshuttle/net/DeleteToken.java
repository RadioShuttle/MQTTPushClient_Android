/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.net;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import de.radioshuttle.mqttpushclient.Broker;

public class DeleteToken extends BrokerRequest {

    public DeleteToken(Context context, Broker broker, MutableLiveData<BrokerRequest> brokerLiveData) {
        super(context, broker, brokerLiveData);
    }

    @Override
    public boolean perform() throws Exception {
        FirebaseApp app = null;
        for (FirebaseApp a : FirebaseApp.getApps(mAppContext)) {
            if (a.getName().equals(mBroker.pushserver)) {
                app = FirebaseApp.getInstance(mBroker.pushserver);
                FirebaseInstanceId id = FirebaseInstanceId.getInstance(app);

                Task<InstanceIdResult> t = id.getInstanceId();
                try {
                    Tasks.await(t, 3, TimeUnit.SECONDS);
                } catch (Exception e) {
                    Log.d(TAG, "Deletion of token for push server " +  mBroker.pushserver + "  failed: " + e.getMessage());
                }

                if (t.isSuccessful()) {
                    try {
                        id.deleteInstanceId();
                        Log.d(TAG, "token delteded");
                    } catch (IOException e) {
                        Log.d(TAG, "deleteInstanceId() failed for " +  mBroker.pushserver + ": " + t.getResult().getToken());
                        throw new ClientError(e);
                    }
                    mConnection.removeFCMToken(t.getResult().getToken());
                    Log.d(TAG, "Token deleted for " +  mBroker.pushserver + ": " + t.getResult().getToken());
                } else {
                    Log.d(TAG, "Deletion of token for push server " +  mBroker.pushserver + "  failed.");
                    throw new ClientError("Deletion of token failed.");
                }
                break;
            }
        }

        return true;
    }

    private final static String TAG = DeleteToken.class.getSimpleName();
}
