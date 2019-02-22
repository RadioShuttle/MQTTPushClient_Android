/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.iid.InstanceIdResult;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.net.Request;


public final class FirebaseTokens {

    /** set info, that for this account the given token has been send */
    public void setTokenSent(String account, String token) {
        if (!(Utils.isEmpty(account) || Utils.isEmpty(token))) {
            rwLock.readLock().lock();
            try {
                if (tokens.containsKey(account) && tokens.get(account).equals(token)) {
                    return;
                }
            } finally {
                rwLock.readLock().unlock();
            }
            rwLock.writeLock().lock();
            try {
                if (!(tokens.containsKey(account) && tokens.get(account).equals(token))) { // reread
                    tokens.put(account, token);
                }
            } finally {
                rwLock.writeLock().unlock();
            }
        }
    }

    public boolean hasToken(String account) {
        boolean hasToken = false;
        if (!(Utils.isEmpty(account))) {
            rwLock.readLock().lock();
            try {
                hasToken = tokens.get(account) != null;
            } finally {
                rwLock.readLock().unlock();
            }
        }
        return hasToken;
    }

    public void removeAccount(String account) {
        if (!Utils.isEmpty(account)) {
            rwLock.writeLock().lock();
            try {
                tokens.remove(account);
            } finally {
                rwLock.writeLock().unlock();
            }
        }
    }

    public void waitForTokenAndNotifyPushServer(final PushAccount pushAccount, final Task<InstanceIdResult> task) {
        Utils.executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Tasks.await(task);
                    if (task.isSuccessful()) {
                        InstanceIdResult result = task.getResult();
                        /* check, if the token has not been sent in the meanwhile */
                        final String account = pushAccount.getKey();
                        rwLock.readLock().lock();
                        try {
                            if (!tokens.containsKey(account) || !tokens.get(account).equals(result.getToken())) {

                                @SuppressLint("StaticFieldLeak")
                                Request sendTokenRequest = new Request(context, pushAccount, null) {
                                    @Override
                                    protected void onPostExecute(PushAccount pushAccount) {
                                        super.onPostExecute(pushAccount);
                                        if (mPushAccount.requestStatus == Cmd.RC_OK) {
                                            Intent intent = new Intent(TOKEN_UPDATED);
                                            intent.putExtra(TOKEN_UPDATE_ACCOUNT, account);
                                            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                                            Log.d(TAG, "Token for " + account + " sent (delayed) to push server"); //TODO: remove log
                                        }
                                    }
                                };
                                sendTokenRequest.setTokenRequest(result.getToken());
                                sendTokenRequest.executeOnExecutor(Utils.executor);
                            }

                        } finally {
                            rwLock.readLock().unlock();
                        }

                    }

                } catch(Exception e) {
                    Log.d(TAG, "waitForTokenAndNotifyPushServer: " + e.getMessage());
                }
            }
        });
    }


    private FirebaseTokens(Context context) {
        this();
        this.context = context;
    }

    private FirebaseTokens() {
        rwLock = new ReentrantReadWriteLock();
        tokens = new HashMap<>();
    }

    public static FirebaseTokens getInstance(Context context) {
        if (firebaseTokens == null) {
            synchronized (lock) {
                if (firebaseTokens == null) {
                    firebaseTokens = new FirebaseTokens(context);
                }
            }
        }
        return firebaseTokens;
    }

    public final static String TOKEN_UPDATED = "TOKEN_UPDATED";
    public final static String TOKEN_UPDATE_ACCOUNT = "TOKEN_UPDATE_ACCOUNT";

    private ReentrantReadWriteLock rwLock;
    private HashMap<String, String> tokens; // account -> token mapping (if key exists, but value is null means request is running
    private Context context;

    private static Object lock = new Object();
    private static volatile FirebaseTokens firebaseTokens;

    private static String TAG = FirebaseTokens.class.getSimpleName();
}
